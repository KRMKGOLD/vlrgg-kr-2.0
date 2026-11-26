package kr.co.cotton.vlrgg_mobile.cache

import io.ktor.server.testing.*
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kotlin.test.*

class UpstashRestResponseCacheStoreTest {
    private val server = LoopbackUpstash()
    private val clients = CopyOnWriteArrayList<java.net.http.HttpClient>()
    private val stalls = CopyOnWriteArrayList<LoopbackUpstash.StalledBody>()

    @AfterTest
    fun closeResources() {
        stalls.forEach { it.release.countDown() }
        clients.forEach { it.shutdownNow() }
        server.close()
    }

    private fun store(requestTimeout: Duration = UPSTASH_REQUEST_TIMEOUT, token: String = TEST_UPSTASH_TOKEN): UpstashRestResponseCacheStore {
        val client = createUpstashHttpClient().also(clients::add)
        return UpstashRestResponseCacheStore(client, server.uri, token, requestTimeout)
    }

    @Test
    fun `commands are posted to the database root with a bearer token and a json command array`() = runBlocking {
        val store = store()
        assertTrue(store.set("sha:match-detail:1", "{\"id\":\"1\"}", 30, onlyIfAbsent = false))
        assertTrue(store.set("sha:match-detail:2", "[502,\"SOURCE_PARSING_FAILURE\"]", 60, onlyIfAbsent = true))
        assertEquals("{\"id\":\"1\"}", store.get("sha:match-detail:1"))

        assertEquals(3, server.received.size)
        server.received.forEach {
            assertEquals("POST", it.method)
            assertEquals("/", it.path)
            assertEquals("Bearer $TEST_UPSTASH_TOKEN", it.authorization)
            assertEquals("application/json", it.contentType)
        }
        assertEquals(listOf("SET", "sha:match-detail:1", "{\"id\":\"1\"}", "EX", "30"), server.received[0].command)
        assertEquals(listOf("SET", "sha:match-detail:2", "[502,\"SOURCE_PARSING_FAILURE\"]", "EX", "60", "NX"), server.received[1].command)
        assertEquals(listOf("GET", "sha:match-detail:1"), server.received[2].command)
    }

    @Test
    fun `consecutive commands reuse one pooled connection`() = runBlocking {
        val store = store()
        assertNull(store.get("a"))
        assertNull(store.get("b"))
        assertTrue(store.set("c", "{}", 30, onlyIfAbsent = false))

        assertEquals(3, server.received.size)
        assertEquals(1, server.received.map { it.remotePort }.toSet().size, "remote ports: ${server.received.map { it.remotePort }}")
    }

    @Test
    fun `get returns the string result and null for an absent key`() = runBlocking {
        val store = store()
        assertNull(store.get("k"))
        server.values["k"] = "{}"
        assertEquals("{}", store.get("k"))
    }

    @Test
    fun `nx refusal is not stored and not a failure`() = runBlocking {
        val store = store()
        assertTrue(store.set("k", "{\"first\":1}", 30, onlyIfAbsent = false))
        assertFalse(store.set("k", "[502,\"SOURCE_PARSING_FAILURE\"]", 60, onlyIfAbsent = true))
        assertEquals("{\"first\":1}", store.get("k"))
    }

    @Test
    fun `unicode quotes and backslashes survive the rest round trip byte for byte`() = runBlocking {
        val store = store()
        val value = "{\"name\":\"한글 팀 🎮\",\"quote\":\"\\\"q\\\"\",\"path\":\"a\\\\b\",\"line\":\"x\\ny\",\"nul\":\"\\u0000\"}"
        store.set("k", value, 30, onlyIfAbsent = false)
        assertEquals(value, server.values["k"])
        val stored = store.get("k")
        assertContentEquals(value.toByteArray(Charsets.UTF_8), stored!!.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `http errors error envelopes and malformed responses are store failures without their text`() = runBlocking {
        val cases = listOf(
            Triple(401, "{\"error\":\"WRONGPASS $TEST_UPSTASH_TOKEN\"}", ResponseCacheFailureKind.HTTP),
            Triple(400, "{\"error\":\"ERR syntax error\"}", ResponseCacheFailureKind.HTTP),
            Triple(500, "oops", ResponseCacheFailureKind.HTTP),
            Triple(302, "", ResponseCacheFailureKind.HTTP),
            Triple(200, "{\"error\":\"ERR max requests limit exceeded\"}", ResponseCacheFailureKind.HTTP),
            Triple(200, "not json", ResponseCacheFailureKind.DECODE),
            Triple(200, "[1,2]", ResponseCacheFailureKind.DECODE),
            Triple(200, "{}", ResponseCacheFailureKind.DECODE),
            Triple(200, "{\"result\":42}", ResponseCacheFailureKind.DECODE),
            Triple(200, "{\"result\":{\"nested\":true}}", ResponseCacheFailureKind.DECODE),
        )
        val store = store()
        cases.forEach { (status, body, kind) ->
            server.reply = { LoopbackUpstash.Reply(status, body) }
            val failure = assertFailsWith<ResponseCacheStoreException>("$status $body") { store.get("k") }
            assertEquals(kind, failure.kind, "$status $body")
            assertNull(failure.message)
            assertNull(failure.cause)
        }
        assertEquals(cases.size, server.received.size, "redirects are never followed")
    }

    @Test
    fun `a wrong token is an http failure`() = runBlocking {
        val failure = assertFailsWith<ResponseCacheStoreException> { store(token = "wrong-token").get("k") }
        assertEquals(ResponseCacheFailureKind.HTTP, failure.kind)
    }

    @Test
    fun `set treats a nil result without NX and any other result as a decode failure`() = runBlocking {
        val store = store()
        listOf("{\"result\":null}", "{\"result\":\"QUEUED\"}").forEach { body ->
            server.reply = { LoopbackUpstash.Reply(200, body) }
            val failure = assertFailsWith<ResponseCacheStoreException> { store.set("k", "{}", 30, onlyIfAbsent = false) }
            assertEquals(ResponseCacheFailureKind.DECODE, failure.kind)
        }
    }

    @Test
    fun `streamed response bodies over the cap are rejected`() = runBlocking {
        server.reply = { LoopbackUpstash.Reply(200, "{\"result\":\"" + "x".repeat(9 * 1024 * 1024) + "\"}", chunked = true) }
        assertEquals(ResponseCacheFailureKind.DECODE, assertFailsWith<ResponseCacheStoreException> { store().get("k") }.kind)
    }

    @Test
    fun `a delayed response hits the client backstop timeout`() = runBlocking {
        server.reply = { LoopbackUpstash.Reply(200, "{\"result\":null}", delayMillis = 2_000) }
        val failure = assertFailsWith<ResponseCacheStoreException> { store(requestTimeout = Duration.ofMillis(100)).get("k") }
        assertEquals(ResponseCacheFailureKind.TIMEOUT, failure.kind)
    }

    @Test
    fun `caller cancellation aborts the stalled exchange before the server finishes the body`() = runBlocking {
        val client = createUpstashHttpClient().also(clients::add)
        val store = UpstashRestResponseCacheStore(client, server.uri, TEST_UPSTASH_TOKEN)
        val stall = stallServer()
        val lookup = async(Dispatchers.IO) { store.get("k") }
        assertTrue(stall.headersSent.await(10, TimeUnit.SECONDS), "headers and a partial body must have been sent")

        lookup.cancelAndJoin()
        assertTrue(lookup.isCancelled)
        assertExchangeAbortedBeforeRelease(client, stall)
    }

    @Test
    fun `the 200 ms wrapper timeout on a real store is a miss and aborts the stalled exchange`() = runBlocking {
        val client = createUpstashHttpClient().also(clients::add)
        val store = UpstashRestResponseCacheStore(client, server.uri, TEST_UPSTASH_TOKEN)
        // Warm the pooled connection so the stalled request reaches the server well inside 200 ms.
        assertNull(store.get("warm-up"))
        val observability = PublicApiObservability()
        val cache = ResponseCache(store, TEST_DEPLOY_SHA, observability, ResponseCacheWarnings(environment = emptyMap(), sink = {}))
        val stall = stallServer()

        // The body cannot complete before release, so a null result can only come from the wrapper's timeout.
        assertNull(cache.lookup(cache.key(ResponseCacheRoute.MATCH_DETAIL, "1")))
        assertTrue(stall.headersSent.await(10, TimeUnit.SECONDS), "headers and a partial body must have been sent")
        assertEquals(1, observability.snapshot().responseCache.getValue(ResponseCacheCounter.LOOKUP_FAILED))
        assertExchangeAbortedBeforeRelease(client, stall)
    }

    @Test
    fun `application stop aborts an in flight exchange instead of waiting for it`() {
        val client = createUpstashHttpClient().also(clients::add)
        val stall = stallServer()
        val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        lateinit var lookup: Deferred<String?>
        // Only a safety net so a regression fails instead of hanging the suite.
        val watchdog = releaseAfter(stall, seconds = 30)
        try {
            testApplication {
                lateinit var store: UpstashRestResponseCacheStore
                application { store = closeOnStop(UpstashRestResponseCacheStore(client, server.uri, TEST_UPSTASH_TOKEN)) }
                startApplication()
                lookup = requestScope.async { store.get("k") }
                assertTrue(stall.headersSent.await(10, TimeUnit.SECONDS), "headers and a partial body must have been sent")
            }
            assertEquals(1, stall.release.count, "application stop must not wait for the stalled body")
            assertTrue(client.awaitTermination(Duration.ofSeconds(10)), "client must terminate while the body is still stalled")
            assertEquals(1, stall.release.count, "the body must still be stalled")
            val failure = runBlocking { assertFailsWith<ResponseCacheStoreException> { lookup.await() } }
            assertEquals(ResponseCacheFailureKind.IO, failure.kind)
        } finally {
            stall.release.countDown()
            watchdog.interrupt()
            requestScope.cancel()
        }
    }

    @Test
    fun `a closed client fails without a request`() = runBlocking {
        val store = store()
        store.close()
        assertEquals(ResponseCacheFailureKind.IO, assertFailsWith<ResponseCacheStoreException> { store.get("k") }.kind)
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun `production client prefers http2 never follows redirects and has a connect backstop`() {
        val client = createUpstashHttpClient().also(clients::add)
        assertEquals(java.net.http.HttpClient.Version.HTTP_2, client.version())
        assertEquals(java.net.http.HttpClient.Redirect.NEVER, client.followRedirects())
        assertEquals(UPSTASH_CONNECT_TIMEOUT, client.connectTimeout().orElseThrow())
        assertTrue(UPSTASH_CONNECT_TIMEOUT.toMillis() > DEFAULT_RESPONSE_CACHE_OPERATION_TIMEOUT_MILLIS)
        assertTrue(UPSTASH_REQUEST_TIMEOUT.toMillis() > DEFAULT_RESPONSE_CACHE_OPERATION_TIMEOUT_MILLIS)
    }

    private fun stallServer(): LoopbackUpstash.StalledBody =
        LoopbackUpstash.StalledBody().also { server.stalled = it; stalls += it }

    /** A client terminates only when no exchange is left, so this proves the stalled exchange was aborted. */
    private fun assertExchangeAbortedBeforeRelease(client: java.net.http.HttpClient, stall: LoopbackUpstash.StalledBody) {
        client.shutdown()
        assertTrue(client.awaitTermination(Duration.ofSeconds(10)), "client must terminate while the body is still stalled")
        assertEquals(1, stall.release.count, "the body must still be stalled")
    }

    private fun releaseAfter(stall: LoopbackUpstash.StalledBody, seconds: Long): Thread =
        Thread {
            try {
                Thread.sleep(seconds * 1_000)
                stall.release.countDown()
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }
}
