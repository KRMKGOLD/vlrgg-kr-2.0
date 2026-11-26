package kr.co.cotton.vlrgg_mobile.cache

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kr.co.cotton.vlrgg_mobile.common.http.RequestDeadlineFailure
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kr.co.cotton.vlrgg_mobile.plugins.formatPublicApiSummary
import kr.co.cotton.vlrgg_mobile.protection.MAX_PUBLIC_JSON_BYTES
import kr.co.cotton.vlrgg_mobile.protection.withWholeRequestDeadline
import kotlin.test.*

class ResponseCacheTest {
    private val store = FakeResponseCacheStore()
    private val observability = PublicApiObservability()
    private val warningLines = mutableListOf<String>()
    private val warnings = ResponseCacheWarnings(environment = emptyMap(), sink = { warningLines += it }, nowMillis = { 0L })

    private fun cache(sha: String = TEST_DEPLOY_SHA, timeoutMillis: Long = 100) =
        ResponseCache(store, sha, observability, warnings, timeoutMillis)

    private fun counter(counter: ResponseCacheCounter) = observability.snapshot().responseCache.getValue(counter)

    @Test
    fun `key is deploy sha then fixed route label then validated identifier`() {
        assertEquals("$TEST_DEPLOY_SHA:matches-upcoming:1", cache().key(ResponseCacheRoute.MATCHES_UPCOMING, "1").value)
        assertEquals("$TEST_DEPLOY_SHA:matches-results:2", cache().key(ResponseCacheRoute.MATCHES_RESULTS, "2").value)
        assertEquals("$TEST_DEPLOY_SHA:match-detail:709685", cache().key(ResponseCacheRoute.MATCH_DETAIL, "709685").value)
        assertFailsWith<IllegalArgumentException> { cache().key(ResponseCacheRoute.MATCH_DETAIL, "a:b") }
        assertFailsWith<IllegalArgumentException> { cache().key(ResponseCacheRoute.MATCH_DETAIL, "/api/v1/matches?x=1") }
        assertFailsWith<IllegalArgumentException> { cache().key(ResponseCacheRoute.MATCH_DETAIL) }
    }

    @Test
    fun `caches with different deploy shas never share an entry`() = runBlocking {
        val first = cache(sha = "sha-one")
        val second = cache(sha = "sha-two")
        first.store(first.key(ResponseCacheRoute.MATCH_DETAIL, "1"), "{\"v\":1}", 30)

        assertNull(second.lookup(second.key(ResponseCacheRoute.MATCH_DETAIL, "1")))
        val hit = assertIs<CachedPublicResponse.Success>(first.lookup(first.key(ResponseCacheRoute.MATCH_DETAIL, "1")))
        assertEquals("{\"v\":1}", hit.body.decodeToString())
    }

    @Test
    fun `ttl above five minutes or below one second is rejected before the store is called`() = runBlocking {
        val cache = cache()
        val key = cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")
        assertFailsWith<IllegalArgumentException> { cache.store(key, "{}", ResponseCacheTtl.MAX_SECONDS + 1) }
        assertFailsWith<IllegalArgumentException> { cache.store(key, "{}", 0) }
        assertTrue(store.sets.isEmpty())

        cache.store(key, "{}", ResponseCacheTtl.MAX_SECONDS)
        assertEquals(300, store.sets.single().ttlSeconds)
    }

    @Test
    fun `negative entry uses the fixed value sixty seconds and NX and never overwrites a success`() = runBlocking {
        val cache = cache()
        val key = cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")
        cache.storeSourceParsingFailure(key)
        assertEquals(FakeResponseCacheStore.SetCall(key.value, "[502,\"SOURCE_PARSING_FAILURE\"]", 60, true), store.sets.single())
        assertSame(CachedPublicResponse.SourceParsingFailure, cache.lookup(key))
        assertEquals(1, counter(ResponseCacheCounter.NEGATIVE_HIT))

        val successKey = cache.key(ResponseCacheRoute.MATCH_DETAIL, "2")
        cache.store(successKey, "{\"ok\":true}", 300)
        cache.storeSourceParsingFailure(successKey)
        assertEquals("{\"ok\":true}", store.values[successKey.value])
        assertEquals(0, counter(ResponseCacheCounter.STORE_FAILED))
    }

    @Test
    fun `absent and damaged values are misses and damaged ones are never served`() = runBlocking {
        val cache = cache()
        val key = cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")
        assertNull(cache.lookup(key))
        assertEquals(1, counter(ResponseCacheCounter.MISS))

        listOf("not json", "[502,\"NOT_FOUND\"]", "[404,\"SOURCE_PARSING_FAILURE\"]", "").forEach { damaged ->
            store.values[key.value] = damaged
            assertNull(cache.lookup(key))
        }
        store.values[key.value] = "{" + "a".repeat(MAX_PUBLIC_JSON_BYTES)
        assertNull(cache.lookup(key))
        assertEquals(5, counter(ResponseCacheCounter.LOOKUP_FAILED))
        assertEquals(1, counter(ResponseCacheCounter.MISS))
        assertEquals(0, counter(ResponseCacheCounter.HIT))
    }

    @Test
    fun `lookup timeout and store exceptions become misses`() = runBlocking {
        val cache = cache(timeoutMillis = 50)
        val key = cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")
        store.values[key.value] = "{}"

        store.beforeGet = { delay(5_000) }
        val startedAt = System.nanoTime()
        assertNull(cache.lookup(key))
        assertTrue((System.nanoTime() - startedAt) / 1_000_000 < 2_000, "lookup must stop at its own timeout")

        store.beforeGet = { throw ResponseCacheStoreException(ResponseCacheFailureKind.HTTP) }
        assertNull(cache.lookup(key))
        store.beforeGet = { throw IllegalStateException("store detail $TEST_UPSTASH_TOKEN") }
        assertNull(cache.lookup(key))

        assertEquals(3, counter(ResponseCacheCounter.LOOKUP_FAILED))
        assertEquals(listOf("timeout", "http", "io"), warningLines.map { Json.parseToJsonElement(it).jsonObject["kind"].toString().trim('"') })
    }

    @Test
    fun `a cancellation raised by the store propagates unchanged from get and set`() = runBlocking {
        val cache = cache()
        val key = cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")
        val raised = CancellationException("not this wrapper's timeout")
        store.beforeGet = { throw raised }
        store.beforeSet = { throw raised }

        // Coroutine stack-trace recovery may copy the exception, so compare its message rather than identity.
        assertEquals(raised.message, assertFailsWith<CancellationException> { cache.lookup(key) }.message)
        assertEquals(raised.message, assertFailsWith<CancellationException> { cache.store(key, "{}", 30) }.message)
        assertEquals(raised.message, assertFailsWith<CancellationException> { cache.storeSourceParsingFailure(key) }.message)
        assertTrue(ResponseCacheCounter.entries.all { counter(it) == 0L }, "a cancellation is neither a miss nor a store failure")
        assertTrue(warningLines.isEmpty())
    }

    @Test
    fun `store failures and timeouts are counted and never thrown`() = runBlocking {
        val cache = cache(timeoutMillis = 50)
        val key = cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")
        store.beforeSet = { throw ResponseCacheStoreException(ResponseCacheFailureKind.IO) }
        cache.store(key, "{}", 30)
        cache.storeSourceParsingFailure(key)
        store.beforeSet = { delay(5_000) }
        cache.store(key, "{}", 30)

        assertEquals(3, counter(ResponseCacheCounter.STORE_FAILED))
        assertTrue(warningLines.all { "\"operation\":\"set\"" in it })
    }

    @Test
    fun `cancelling the caller during a lookup propagates and is not turned into a miss`() = runBlocking {
        val cache = cache(timeoutMillis = 10_000)
        val entered = CompletableDeferred<Unit>()
        store.beforeGet = { entered.complete(Unit); awaitCancellation() }
        val continuedAsMiss = AtomicBoolean(false)
        val job = launch {
            cache.lookup(cache.key(ResponseCacheRoute.MATCH_DETAIL, "1"))
            continuedAsMiss.set(true)
        }
        entered.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse(continuedAsMiss.get())
        assertEquals(0, counter(ResponseCacheCounter.LOOKUP_FAILED))
        assertEquals(0, counter(ResponseCacheCounter.MISS))
    }

    @Test
    fun `whole request deadline during a lookup stays the request deadline`() = runBlocking {
        val cache = cache(timeoutMillis = 10_000)
        store.beforeGet = { awaitCancellation() }
        var loaded = false

        assertFailsWith<RequestDeadlineFailure> {
            withWholeRequestDeadline(50) {
                cache.lookup(cache.key(ResponseCacheRoute.MATCH_DETAIL, "1"))
                loaded = true
            }
        }
        assertFalse(loaded)
        assertEquals(0, counter(ResponseCacheCounter.LOOKUP_FAILED))
    }

    @Test
    fun `disabled cache never touches a store or counters`() = runBlocking {
        val disabled = ResponseCache.DISABLED
        assertFalse(disabled.isEnabled)
        assertNull(disabled.lookup(disabled.key(ResponseCacheRoute.MATCH_DETAIL, "1")))
        assertTrue(ResponseCacheCounter.entries.all { counter(it) == 0L })
    }

    @Test
    fun `warning lines are fixed field json without key value url token or exception text`() = runBlocking {
        val cache = cache()
        store.beforeGet = { throw IllegalStateException("$TEST_UPSTASH_URL $TEST_UPSTASH_TOKEN") }
        cache.lookup(cache.key(ResponseCacheRoute.MATCH_DETAIL, "709685"))

        val line = warningLines.single()
        val json = Json.parseToJsonElement(line).jsonObject
        assertEquals(setOf("severity", "message", "operation", "kind", "route", "serviceContext"), json.keys)
        assertEquals(
            "{\"severity\":\"WARN\",\"message\":\"response cache operation failed\",\"operation\":\"get\",\"kind\":\"io\"," +
                "\"route\":\"match-detail\",\"serviceContext\":{\"service\":\"vlrgg-server-local\",\"version\":\"local\"}}",
            line,
        )
        listOf("709685", TEST_DEPLOY_SHA, "upstash", TEST_UPSTASH_TOKEN, "IllegalStateException", "@type", "/api/").forEach {
            assertFalse(line.contains(it), "warning must not contain $it")
        }
        assertFalse(line.contains('\n'))
    }

    @Test
    fun `warnings are limited to four per operation per minute`() = runBlocking {
        var now = 0L
        val lines = mutableListOf<String>()
        val limited = ResponseCacheWarnings(environment = emptyMap(), sink = { lines += it }, nowMillis = { now })
        val cache = ResponseCache(store, TEST_DEPLOY_SHA, observability, limited, 100)
        val key = cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")
        store.beforeGet = { throw ResponseCacheStoreException(ResponseCacheFailureKind.HTTP) }
        store.beforeSet = { throw ResponseCacheStoreException(ResponseCacheFailureKind.HTTP) }

        repeat(6) { cache.lookup(key); cache.store(key, "{}", 30) }
        assertEquals(4, lines.count { "\"operation\":\"get\"" in it })
        assertEquals(4, lines.count { "\"operation\":\"set\"" in it })
        assertEquals(6, counter(ResponseCacheCounter.LOOKUP_FAILED))
        assertEquals(6, counter(ResponseCacheCounter.STORE_FAILED))

        now = 60_000
        cache.lookup(key)
        assertEquals(5, lines.count { "\"operation\":\"get\"" in it })
    }

    @Test
    fun `summary appends cache counters after the unchanged existing fields`() = runBlocking {
        val cache = cache()
        val key = cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")
        cache.lookup(key)
        cache.store(key, "{}", 30)
        cache.lookup(key)
        store.beforeGet = { throw ResponseCacheStoreException(ResponseCacheFailureKind.HTTP) }
        cache.lookup(key)

        val summary = formatPublicApiSummary(observability.snapshot())
        assertTrue(summary.endsWith("telemetry_failures=0 cache={hit=1,negative_hit=0,miss=1,lookup_failed=1,store_failed=0}"), summary)
        assertTrue(summary.startsWith("public_api_summary requests=0 routes={api=0,other=0} "))
        assertContains(summary, " diagnostics_emitted={EXPECTED=0,UPSTREAM_NETWORK=0,INTERNAL=0,SOURCE_PARSING=0} ")
    }
}
