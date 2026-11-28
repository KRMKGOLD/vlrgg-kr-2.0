package kr.co.cotton.vlrgg_mobile.cache

import com.github.benmanes.caffeine.cache.Ticker
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kr.co.cotton.vlrgg_mobile.common.http.RateLimitedFailure
import kr.co.cotton.vlrgg_mobile.common.http.ServerBusyFailure
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kr.co.cotton.vlrgg_mobile.protection.MonotonicClock
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtection
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig
import kotlin.test.*

class ResponseCacheTest {
    private val scopes = mutableListOf<CoroutineScope>()
    private val ticker = FakeTicker()
    private val observability = PublicApiObservability()

    @AfterTest
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    @Test
    fun `key is the fixed route label and validated identifier and never collides across routes`() {
        assertEquals("match-detail:709685", ResponseCacheKey.of(ResponseCacheRoute.MATCH_DETAIL, listOf("709685")).value)
        assertEquals("matches-upcoming:1", ResponseCacheKey.of(ResponseCacheRoute.MATCHES_UPCOMING, listOf("1")).value)
        assertEquals("matches-results:1", ResponseCacheKey.of(ResponseCacheRoute.MATCHES_RESULTS, listOf("1")).value)
        assertEquals("match-detail:1:a-b", ResponseCacheKey.of(ResponseCacheRoute.MATCH_DETAIL, listOf("1", "a-b")).value)
        val keys = ResponseCacheRoute.entries.map { ResponseCacheKey.of(it, listOf("1")).value }
        assertEquals(keys.size, keys.toSet().size)
        listOf(emptyList(), listOf(""), listOf("a:b"), listOf("a/b"), listOf("x".repeat(129)), listOf("한글")).forEach {
            assertFailsWith<IllegalArgumentException> { ResponseCacheKey.of(ResponseCacheRoute.MATCH_DETAIL, it) }
        }
    }

    @Test
    fun `success is served until its own ttl and never after`() = runBlocking {
        val cache = cache()
        val loads = AtomicInteger()
        val key = key("1")
        val load = suspend { loads.incrementAndGet(); LoadedPublicResponse(body("v${loads.get()}"), ttlSeconds = 30) }

        assertBody("v1", cache.get(key, load))
        ticker.advanceNanos(TimeUnit.SECONDS.toNanos(30) - 1)
        assertBody("v1", cache.get(key, load))
        ticker.advanceNanos(1)
        assertBody("v2", cache.get(key, load))
        assertEquals(2, loads.get())
    }

    @Test
    fun `a hit just before expiry does not extend the entry`() = runBlocking {
        val cache = cache()
        val loads = AtomicInteger()
        val key = key("1")
        val load = suspend { loads.incrementAndGet(); LoadedPublicResponse(body("v${loads.get()}"), ttlSeconds = 30) }

        cache.get(key, load)
        repeat(5) {
            ticker.advanceNanos(TimeUnit.SECONDS.toNanos(5))
            assertBody("v1", cache.get(key, load))
        }
        ticker.advanceNanos(TimeUnit.SECONDS.toNanos(5))
        assertBody("v2", cache.get(key, load))
    }

    @Test
    fun `ttl above the five minute cap or below one second is rejected and nothing is stored`() = runBlocking {
        val cache = cache()
        listOf(0, 301, Int.MAX_VALUE).forEach { ttl ->
            assertFailsWith<IllegalArgumentException> {
                cache.get(key("over-$ttl")) { LoadedPublicResponse(body("x"), ttl) }
            }
        }
        val loads = AtomicInteger()
        val load = suspend { loads.incrementAndGet(); LoadedPublicResponse(body("ok"), 300) }
        cache.get(key("cap"), load)
        ticker.advanceNanos(TimeUnit.SECONDS.toNanos(300) - 1)
        cache.get(key("cap"), load)
        assertEquals(1, loads.get())
        ticker.advanceNanos(1)
        cache.get(key("cap"), load)
        assertEquals(2, loads.get())
        // Only the two successful loads occupy memory; the rejected ttl values left nothing behind.
        assertEquals(("match-detail:cap".length + 2).toLong(), cache.weightedBytes())
    }

    @Test
    fun `upstream network failure is not stored and the next request loads again`() = runBlocking {
        val cache = cache()
        val loads = AtomicInteger()
        assertFailsWith<UpstreamNetworkFailure> {
            cache.get(key("1")) { loads.incrementAndGet(); throw UpstreamNetworkFailure() }
        }
        assertBody("fresh", cache.get(key("1")) { loads.incrementAndGet(); LoadedPublicResponse(body("fresh"), 300) })
        assertEquals(2, loads.get())
        assertEquals(0, counter(ResponseCacheCounter.NEGATIVE_HIT))
    }

    @Test
    fun `source parsing failure is stored for 60 seconds then loads again`() = runBlocking {
        val cache = cache()
        val loads = AtomicInteger()
        assertFailsWith<SourceParsingFailure> {
            cache.get(key("1")) { loads.incrementAndGet(); throw SourceParsingFailure(IllegalStateException("selector")) }
        }
        ticker.advanceNanos(TimeUnit.SECONDS.toNanos(60) - 1)
        assertSame(CachedPublicResponse.ParsingFailure, cache.get(key("1")) { error("must replay the cached failure") })
        ticker.advanceNanos(1)
        assertBody("fresh", cache.get(key("1")) { loads.incrementAndGet(); LoadedPublicResponse(body("fresh"), 300) })
        assertEquals(2, loads.get())
        assertEquals(1, counter(ResponseCacheCounter.NEGATIVE_HIT))
    }

    @Test
    fun `a parsing failure from a cancelled load never replaces a stored success`() = runBlocking {
        val cache = cache()
        val parseStarted = CompletableDeferred<Unit>()
        val finishParse = CompletableDeferred<Unit>()
        val stale = async(Dispatchers.Default) {
            cache.get(key("1")) {
                parseStarted.complete(Unit)
                // A blocking parse ignores cancellation and fails after a newer load succeeded.
                runBlocking { finishParse.await() }
                throw SourceParsingFailure(IllegalStateException("selector"))
            }
        }
        parseStarted.await()
        // The only waiter leaves, which cancels the load and removes it, so the next request starts a new load.
        stale.cancelAndJoin()
        assertBody("fresh", withTimeout(2_000) { cache.get(key("1")) { LoadedPublicResponse(body("fresh"), 300) } })
        finishParse.complete(Unit)
        eventually { observability.snapshot().activeApi == 0 }

        assertBody("fresh", cache.get(key("1")) { error("the success must still be cached") })
    }

    @Test
    fun `a success from a cancelled load never replaces a newer stored success`() = runBlocking {
        val cache = cache()
        val parseStarted = CompletableDeferred<Unit>()
        val finishParse = CompletableDeferred<Unit>()
        val stale = async(Dispatchers.Default) {
            cache.get(key("1")) {
                parseStarted.complete(Unit)
                // A blocking parse ignores cancellation and succeeds after a newer load stored its result.
                runBlocking { finishParse.await() }
                LoadedPublicResponse(body("old"), 300)
            }
        }
        parseStarted.await()
        stale.cancelAndJoin()
        assertBody("fresh", withTimeout(2_000) { cache.get(key("1")) { LoadedPublicResponse(body("fresh"), 300) } })
        finishParse.complete(Unit)
        eventually { observability.snapshot().activeApi == 0 }

        assertBody("fresh", cache.get(key("1")) { error("the newer success must still be cached") })
    }

    @Test
    fun `a load finishing right after the locked lookup missed is joined, not replaced`() = runBlocking {
        val misses = AtomicInteger()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstLoadReturned = CompletableDeferred<Unit>()
        // Only the second request's locked miss reacts: it lets the first load finish and gives it time to publish
        // and complete before this request decides between joining and starting a new load.
        val cache = cache(upstreamWorkBurst = 1) {
            if (misses.incrementAndGet() == 2) {
                releaseFirst.complete(Unit)
                runBlocking { withTimeoutOrNull(2_000) { firstLoadReturned.await() } }
                val until = System.nanoTime() + 200_000_000L
                while (observability.snapshot().activeApi != 0 && System.nanoTime() < until) Thread.sleep(1)
            }
        }
        val loads = AtomicInteger()
        val first = async(Dispatchers.Default) {
            cache.get(key("1")) {
                loads.incrementAndGet()
                releaseFirst.await()
                LoadedPublicResponse(body("only"), 300).also { firstLoadReturned.complete(Unit) }
            }
        }
        eventually { misses.get() == 1 && observability.snapshot().activeApi == 1 }
        val second = withTimeout(5_000) { cache.get(key("1")) { loads.incrementAndGet(); LoadedPublicResponse(body("second"), 300) } }

        assertBody("only", second)
        assertBody("only", first.await())
        assertEquals(1, loads.get())
        // The single upstream work token was spent once; a second admission would have been a 429.
        assertEquals(1, counter(ResponseCacheCounter.MISS))
    }

    @Test
    fun `concurrent writers of different keys leave the stored bytes within the limit without forced cleanup`() =
        runBlocking {
            val cache = cache(maxBytes = 1_000, upstreamWorkBurst = 10_000)
            repeat(50) { batch ->
                val release = CompletableDeferred<Unit>()
                val writers = List(8) { index ->
                    async(Dispatchers.Default) {
                        cache.get(key("b$batch-$index")) {
                            release.await()
                            LoadedPublicResponse(ByteArray(300), 300)
                        }
                    }
                }
                eventually { counter(ResponseCacheCounter.MISS) == (batch + 1) * 8L }
                release.complete(Unit)
                writers.awaitAll()
                val stored = cache.storedBytesWithoutMaintenance()
                assertTrue(stored <= 1_000, "batch $batch left $stored bytes in a 1,000 byte cache")
            }
        }

    @Test
    fun `capacity counts key and value bytes and evicts to stay within the limit`() = runBlocking {
        val cache = cache(maxBytes = 1_000)
        repeat(10) { index ->
            cache.get(key("$index")) { LoadedPublicResponse(ByteArray(300) { 'a'.code.toByte() }, 300) }
            // Read before any forced maintenance: eviction already ran on the writing thread.
            val stored = cache.storedBytesWithoutMaintenance()
            assertTrue(stored <= 1_000, "$stored bytes exceed the limit before any forced maintenance")
            assertTrue(cache.weightedBytes() <= 1_000, "weighted size ${cache.weightedBytes()} exceeded the limit")
        }
        assertTrue(cache.weightedBytes() > 0)

        // An entry larger than the whole cache is still returned to its callers but never retained.
        val oversized = cache.get(key("big")) { LoadedPublicResponse(ByteArray(2_000), 300) }
        assertEquals(2_000, (oversized as CachedPublicResponse.Success).body.size)
        assertTrue(cache.weightedBytes() <= 1_000)
        val loads = AtomicInteger()
        cache.get(key("big")) { loads.incrementAndGet(); LoadedPublicResponse(ByteArray(2_000), 300) }
        assertEquals(1, loads.get())
    }

    @Test
    fun `concurrent misses of one key share one load and receive the same bytes`() = runBlocking {
        val cache = cache()
        val loads = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val waiters = List(8) {
            async(Dispatchers.Default) {
                cache.get(key("1")) {
                    loads.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    LoadedPublicResponse(body("shared"), 300)
                }
            }
        }
        started.await()
        eventually { counter(ResponseCacheCounter.JOINED) == 7L }
        release.complete(Unit)

        val bodies = waiters.awaitAll().map { (it as CachedPublicResponse.Success).body }
        assertEquals(1, loads.get())
        assertTrue(bodies.all { it.contentEquals(body("shared")) })
        assertEquals(1, counter(ResponseCacheCounter.MISS))
        assertEquals(0, observability.snapshot().activeApi)
    }

    @Test
    fun `a failed shared load reaches every waiter and the next request loads again`() = runBlocking {
        val cache = cache()
        val loads = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val waiters = List(4) {
            async(Dispatchers.Default) {
                runCatching {
                    cache.get(key("1")) {
                        loads.incrementAndGet()
                        started.complete(Unit)
                        release.await()
                        throw UpstreamNetworkFailure()
                    }
                }
            }
        }
        started.await()
        eventually { counter(ResponseCacheCounter.JOINED) == 3L }
        release.complete(Unit)

        assertTrue(waiters.awaitAll().all { it.exceptionOrNull() is UpstreamNetworkFailure })
        assertEquals(1, loads.get())
        assertBody("retry", cache.get(key("1")) { loads.incrementAndGet(); LoadedPublicResponse(body("retry"), 300) })
        assertEquals(2, loads.get())
    }

    @Test
    fun `one cancelled waiter leaves the load running and the last waiter cancels it`() = runBlocking {
        val cache = cache()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async(Dispatchers.Default) {
            cache.get(key("shared")) {
                started.complete(Unit)
                release.await()
                LoadedPublicResponse(body("kept"), 300)
            }
        }
        started.await()
        val second = async(Dispatchers.Default) { cache.get(key("shared")) { error("must join the first load") } }
        eventually { counter(ResponseCacheCounter.JOINED) == 1L }
        first.cancelAndJoin()
        release.complete(Unit)
        assertBody("kept", second.await())
        assertBody("kept", cache.get(key("shared")) { error("the surviving load must have been stored") })

        val lastStarted = CompletableDeferred<Unit>()
        val loadCancelled = CompletableDeferred<Unit>()
        val last = async(Dispatchers.Default) {
            cache.get(key("last")) {
                try {
                    lastStarted.complete(Unit)
                    awaitCancellation()
                } finally {
                    loadCancelled.complete(Unit)
                }
            }
        }
        lastStarted.await()
        last.cancelAndJoin()
        withTimeout(1_000) { loadCancelled.await() }
        eventually { observability.snapshot().activeApi == 0 }
        val loads = AtomicInteger()
        cache.get(key("last")) { loads.incrementAndGet(); LoadedPublicResponse(body("new"), 300) }
        assertEquals(1, loads.get())
    }

    @Test
    fun `a waiter deadline applies to that waiter only`() = runBlocking {
        val cache = cache()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val patient = async(Dispatchers.Default) {
            cache.get(key("1")) {
                started.complete(Unit)
                release.await()
                LoadedPublicResponse(body("late"), 300)
            }
        }
        started.await()
        val impatient = async(Dispatchers.Default) { withTimeoutOrNull(500) { cache.get(key("1")) { error("must join") } } }
        // The join must be observed before the deadline; a join that never happened would fail here instead of passing.
        eventually { counter(ResponseCacheCounter.JOINED) == 1L }
        assertNull(impatient.await())
        assertFalse(patient.isCompleted)
        release.complete(Unit)
        assertBody("late", patient.await())
    }

    @Test
    fun `hits and joined waiters need no upstream work slot while a new miss is rejected`() = runBlocking {
        val cache = cache(maxActiveUpstreamWork = 1)
        cache.get(key("primed")) { LoadedPublicResponse(body("primed"), 300) }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async(Dispatchers.Default) {
            cache.get(key("held")) {
                started.complete(Unit)
                release.await()
                LoadedPublicResponse(body("held"), 300)
            }
        }
        started.await()
        assertEquals(1, observability.snapshot().activeApi)

        assertBody("primed", cache.get(key("primed")) { error("a hit must not load") })
        val joiner = async(Dispatchers.Default) { cache.get(key("held")) { error("must join") } }
        eventually { counter(ResponseCacheCounter.JOINED) == 1L }
        assertFailsWith<ServerBusyFailure> { cache.get(key("other")) { error("must not start") } }

        release.complete(Unit)
        assertBody("held", holder.await())
        assertBody("held", joiner.await())
        eventually { observability.snapshot().activeApi == 0 }
    }

    @Test
    fun `an exhausted upstream work token bucket rejects a miss with 429`() = runBlocking {
        val cache = cache(upstreamWorkBurst = 1)
        cache.get(key("1")) { LoadedPublicResponse(body("one"), 300) }
        assertFailsWith<RateLimitedFailure> { cache.get(key("2")) { error("must not start") } }
        assertBody("one", cache.get(key("1")) { error("a hit must not load") })
    }

    @Test
    fun `application shutdown cancels a shared load and releases its slot`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = cache(scope = scope)
        val started = CompletableDeferred<Unit>()
        val waiter = async(Dispatchers.Default) {
            cache.get(key("1")) { started.complete(Unit); awaitCancellation() }
        }
        started.await()
        scope.cancel()
        assertFailsWith<CancellationException> { waiter.await() }
        eventually { observability.snapshot().activeApi == 0 }
    }

    @Test
    fun `jvm errors from a load are not swallowed`() = runBlocking {
        val cache = cache()
        assertFailsWith<StackOverflowError> { cache.get(key("1")) { throw StackOverflowError() } }
        Unit
    }

    @Test
    fun `config defaults on with 32 MiB and refuses malformed values`() {
        assertEquals(ResponseCacheConfig(enabled = true, maxBytes = 32 * 1024 * 1024), ResponseCacheConfig.fromEnvironment(emptyMap()))
        assertFalse(ResponseCacheConfig.fromEnvironment(mapOf("VLRGG_RESPONSE_CACHE_ENABLED" to "false")).enabled)
        assertTrue(ResponseCacheConfig.fromEnvironment(mapOf("VLRGG_RESPONSE_CACHE_ENABLED" to "true")).enabled)
        assertEquals(1_024, ResponseCacheConfig.fromEnvironment(mapOf("VLRGG_RESPONSE_CACHE_MAX_BYTES" to "1024")).maxBytes)
        listOf("TRUE", "False", "1", "yes", "", " true").forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) {
                ResponseCacheConfig.fromEnvironment(mapOf("VLRGG_RESPONSE_CACHE_ENABLED" to value))
            }
        }
        listOf("0", "-1", "32MiB", "", "99999999999").forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) {
                ResponseCacheConfig.fromEnvironment(mapOf("VLRGG_RESPONSE_CACHE_MAX_BYTES" to value))
            }
        }
    }

    private fun cache(
        maxBytes: Int = 1024 * 1024,
        maxActiveUpstreamWork: Int = 8,
        upstreamWorkBurst: Int = 100,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        afterInnerMiss: () -> Unit = {},
    ): ResponseCache {
        scopes += scope
        val protection = PublicApiProtection(
            config = PublicApiProtectionConfig(
                apiRequestsPerSecond = 1,
                apiBurst = upstreamWorkBurst,
                maxActiveApiRequests = maxActiveUpstreamWork,
            ),
            scope = scope,
            clock = MonotonicClock { 0L },
            observability = observability,
        )
        return ResponseCache(maxBytes, protection, observability, ticker, afterInnerMiss)
    }

    private fun key(id: String) = ResponseCacheKey.of(ResponseCacheRoute.MATCH_DETAIL, listOf(id))

    private fun body(text: String) = text.toByteArray(Charsets.UTF_8)

    private fun assertBody(expected: String, response: CachedPublicResponse) {
        assertIs<CachedPublicResponse.Success>(response)
        assertEquals(expected, response.body.toString(Charsets.UTF_8))
    }

    private fun counter(counter: ResponseCacheCounter) = observability.snapshot().responseCache.getValue(counter)

    private suspend fun eventually(assertion: () -> Boolean) = withTimeout(2_000) {
        while (!assertion()) yield()
    }
}

/** Manually advanced Caffeine ticker; tests never sleep to move cache time. */
internal class FakeTicker : Ticker {
    private val nanos = AtomicLong()

    override fun read(): Long = nanos.get()

    fun advanceNanos(delta: Long) {
        nanos.addAndGet(delta)
    }

    fun advanceSeconds(seconds: Long) = advanceNanos(TimeUnit.SECONDS.toNanos(seconds))
}
