package kr.co.cotton.vlrgg_mobile.cache

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.plugins.FailureEvent
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kotlin.test.*

/** In-memory store with call records and injectable delays or failures; honors `NX` like Redis. */
internal class FakeResponseCacheStore : ResponseCacheStore {
    data class SetCall(val key: String, val value: String, val ttlSeconds: Int, val onlyIfAbsent: Boolean)

    val values = ConcurrentHashMap<String, String>()
    val gets = CopyOnWriteArrayList<String>()
    val sets = CopyOnWriteArrayList<SetCall>()

    @Volatile var beforeGet: suspend (String) -> Unit = {}
    @Volatile var beforeSet: suspend (SetCall) -> Unit = {}

    override suspend fun get(key: String): String? {
        gets += key
        beforeGet(key)
        return values[key]
    }

    override suspend fun set(key: String, value: String, ttlSeconds: Int, onlyIfAbsent: Boolean): Boolean {
        val call = SetCall(key, value, ttlSeconds, onlyIfAbsent)
        sets += call
        beforeSet(call)
        if (onlyIfAbsent && values.containsKey(key)) return false
        values[key] = value
        return true
    }

    /** Simulates TTL expiry of one entry. */
    fun expire(key: String) {
        values.remove(key)
    }
}

/**
 * Loopback HTTP server that behaves like the Upstash REST root endpoint for GET and SET [EX] [NX].
 * [reply] can override the answer per request. It never leaves the local machine.
 */
internal class LoopbackUpstash(private val token: String = TEST_UPSTASH_TOKEN) : AutoCloseable {
    data class Received(
        val method: String,
        val path: String,
        val authorization: String?,
        val contentType: String?,
        val command: List<String>,
        val remotePort: Int,
    )

    class Reply(val status: Int, val body: String, val delayMillis: Long = 0, val chunked: Boolean = false)

    /**
     * Sends 200 headers and a partial JSON body at once, signals [headersSent], then holds the rest of the body
     * until [release] opens. Models a store that stalls mid-response.
     */
    class StalledBody(val headersSent: CountDownLatch = CountDownLatch(1), val release: CountDownLatch = CountDownLatch(1))

    @Volatile var stalled: StalledBody? = null

    val values = ConcurrentHashMap<String, String>()
    val received = CopyOnWriteArrayList<Received>()
    @Volatile var reply: ((Received) -> Reply?)? = null

    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also { server ->
        server.executor = executor
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    val uri: URI get() = URI("http://${server.address.address.hostAddress}:${server.address.port}/")

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val command = runCatching { Json.parseToJsonElement(body).jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
            val received = Received(
                method = exchange.requestMethod,
                path = exchange.requestURI.toString(),
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                contentType = exchange.requestHeaders.getFirst("Content-Type"),
                command = command,
                remotePort = exchange.remoteAddress.port,
            )
            this.received += received
            stalled?.let { stall ->
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("{\"result\":\"".toByteArray(Charsets.UTF_8))
                exchange.responseBody.flush()
                stall.headersSent.countDown()
                stall.release.await()
                runCatching { exchange.responseBody.write("x\"}".toByteArray(Charsets.UTF_8)) }
                return
            }
            val answer = reply?.invoke(received) ?: defaultReply(received)
            if (answer.delayMillis > 0) Thread.sleep(answer.delayMillis)
            val bytes = answer.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(answer.status, if (answer.chunked) 0 else if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
        }
    }

    private fun defaultReply(received: Received): Reply {
        if (received.authorization != "Bearer $token") return Reply(401, "{\"error\":\"Unauthorized\"}")
        val command = received.command
        val result = when (command.firstOrNull()) {
            "GET" -> values[command[1]]?.let(::JsonPrimitive) ?: JsonNull
            "SET" -> if ("NX" in command.drop(3) && values.containsKey(command[1])) {
                JsonNull
            } else {
                values[command[1]] = command[2]
                JsonPrimitive("OK")
            }
            else -> return Reply(400, "{\"error\":\"ERR unknown command\"}")
        }
        return Reply(200, buildJsonObject { put("result", result) }.toString())
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}

internal const val TEST_UPSTASH_URL = "https://test-database.upstash.io"
internal const val TEST_UPSTASH_TOKEN = "AXtestTOKENsentinel0123456789="
internal const val TEST_DEPLOY_SHA = "0123456789abcdef0123456789abcdef01234567"

internal fun enabledTestCacheConfig(): ResponseCacheConfig.Enabled = ResponseCacheConfig.Enabled(
    restUrl = Url(TEST_UPSTASH_URL),
    token = TEST_UPSTASH_TOKEN,
    deploySha = TEST_DEPLOY_SHA,
)

internal const val TEST_ROUTE_CACHE_SHA = "test-sha"

/** Counts upstream fetches, so "the service was not called" means the count did not move. */
internal class CountingTransport(private val respond: (Url) -> String) : UpstreamHtmlTransport {
    private val count = AtomicInteger()
    val calls: Int get() = count.get()
    val requestedPaths = CopyOnWriteArrayList<String>()
    @Volatile var failure: Exception? = null

    override suspend fun get(url: Url): String {
        count.incrementAndGet()
        requestedPaths += url.encodedPath
        failure?.let { throw it }
        return respond(url)
    }
}

/** One enabled [ResponseCache] over a [FakeResponseCacheStore] with the counters and failure events a route test inspects. */
internal class RouteCacheHarness {
    val store = FakeResponseCacheStore()
    val observability = PublicApiObservability()
    val failureEvents = CopyOnWriteArrayList<FailureEvent>()
    val cache = ResponseCache(
        store = store,
        deploySha = TEST_ROUTE_CACHE_SHA,
        observability = observability,
        warnings = ResponseCacheWarnings(environment = emptyMap(), sink = {}),
        operationTimeoutMillis = 200,
    )

    fun counter(counter: ResponseCacheCounter) = observability.snapshot().responseCache.getValue(counter)

    fun key(route: ResponseCacheRoute, vararg identifier: String): String = cache.key(route, *identifier).value

    fun run(install: Route.(ResponseCache) -> Unit, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureErrorHandling(failureEventSink = { failureEvents += it })
            routing { install(cache) }
        }
        block()
    }
}

/**
 * The cache contract every cached public GET shares, driven through the real route and service:
 * miss stores the exact body under [expectedKey] with [expectedTtlSeconds]; a hit replays the same bytes without
 * another upstream fetch; an upstream network failure is not stored; a parsing failure is stored for 60 seconds
 * with `NX` and replayed without the upstream or a second failure event.
 */
internal suspend fun ApplicationTestBuilder.assertCachedRouteContract(
    harness: RouteCacheHarness,
    transport: CountingTransport,
    path: String,
    expectedKey: String,
    expectedTtlSeconds: Int,
) {
    val store = harness.store
    store.values.clear()
    store.sets.clear()

    val miss = client.get(path)
    assertEquals(HttpStatusCode.OK, miss.status, path)
    val missBytes = miss.bodyAsBytes()
    val callsAfterMiss = transport.calls
    assertTrue(callsAfterMiss > 0, path)
    assertEquals(
        listOf(FakeResponseCacheStore.SetCall(expectedKey, missBytes.decodeToString(), expectedTtlSeconds, false)),
        store.sets.toList(),
        path,
    )

    val hit = client.get(path)
    assertEquals(HttpStatusCode.OK, hit.status, path)
    assertEquals("application/json", hit.headers[HttpHeaders.ContentType], path)
    assertEquals(miss.headers[HttpHeaders.ContentType], hit.headers[HttpHeaders.ContentType], path)
    assertContentEquals(missBytes, hit.bodyAsBytes(), path)
    assertEquals(callsAfterMiss, transport.calls, "$path: a hit must not fetch upstream")
    assertEquals(1, store.sets.size, "$path: a hit must not store again")

    store.expire(expectedKey)
    store.sets.clear()
    transport.failure = UpstreamNetworkFailure(IllegalStateException("private upstream detail"))
    val network = client.get(path)
    assertEquals(HttpStatusCode.BadGateway, network.status, path)
    assertEquals(ApiErrorCode.UPSTREAM_NETWORK_FAILURE, Json.decodeFromString<ApiErrorResponse>(network.bodyAsText()).code, path)
    assertTrue(store.sets.isEmpty(), "$path: a network failure must not be stored")

    transport.failure = SourceParsingFailure(IllegalStateException("selector details"))
    val failureEventsBefore = harness.failureEvents.size
    val parsing = client.get(path)
    val parsingBody = parsing.bodyAsText()
    assertEquals(HttpStatusCode.BadGateway, parsing.status, path)
    assertEquals(ApiErrorCode.SOURCE_PARSING_FAILURE, Json.decodeFromString<ApiErrorResponse>(parsingBody).code, path)
    assertFalse(parsingBody.contains("selector"), path)
    assertEquals(
        listOf(FakeResponseCacheStore.SetCall(expectedKey, SOURCE_PARSING_FAILURE_CACHE_VALUE, 60, true)),
        store.sets.toList(),
        path,
    )
    assertEquals(failureEventsBefore + 1, harness.failureEvents.size, path)

    transport.failure = null
    val callsBeforeReplay = transport.calls
    val replay = client.get(path)
    assertEquals(parsing.status, replay.status, path)
    assertEquals(parsingBody, replay.bodyAsText(), path)
    assertEquals(callsBeforeReplay, transport.calls, "$path: a cached parsing failure must not fetch upstream")
    assertEquals(failureEventsBefore + 1, harness.failureEvents.size, "$path: a replay is not a new failure event")
}

/** Each invalid path answers `INVALID_REQUEST` with no store call and no upstream fetch. */
internal suspend fun ApplicationTestBuilder.assertInvalidRequestsSkipCacheAndUpstream(
    harness: RouteCacheHarness,
    transport: CountingTransport,
    paths: List<String>,
) {
    val callsBefore = transport.calls
    paths.forEach { path ->
        val response = client.get(path)
        assertEquals(HttpStatusCode.BadRequest, response.status, path)
        assertEquals(ApiErrorCode.INVALID_REQUEST, Json.decodeFromString<ApiErrorResponse>(response.bodyAsText()).code, path)
    }
    assertTrue(harness.store.gets.isEmpty(), "invalid requests must not read the store")
    assertTrue(harness.store.sets.isEmpty(), "invalid requests must not write the store")
    assertEquals(callsBefore, transport.calls)
}
