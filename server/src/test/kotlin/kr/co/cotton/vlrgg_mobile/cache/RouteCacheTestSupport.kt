package kr.co.cotton.vlrgg_mobile.cache

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
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
import kr.co.cotton.vlrgg_mobile.protection.MonotonicClock
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtection
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig
import kotlin.test.*

/** Counts upstream fetches, so "the service was not called" means the count did not move. */
internal class CountingTransport(private val respond: (Url) -> String) : UpstreamHtmlTransport {
    private val count = AtomicInteger()
    val calls: Int get() = count.get()
    @Volatile var failure: Exception? = null

    /** While set, every fetch waits here after it is counted; the test opens it to let loads finish. */
    @Volatile var gate: CompletableDeferred<Unit>? = null

    override suspend fun get(url: Url): String {
        count.incrementAndGet()
        gate?.await()
        failure?.let { throw it }
        return respond(url)
    }
}

/**
 * One enabled [ResponseCache] on a manually advanced ticker, with the observability counters and failure events a
 * route test inspects. Routes are installed the way `module` does, with the real serialization and error handling.
 */
internal class RouteCacheHarness : AutoCloseable {
    val ticker = FakeTicker()
    val observability = PublicApiObservability()
    val failureEvents = CopyOnWriteArrayList<FailureEvent>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val protection = PublicApiProtection(
        config = PublicApiProtectionConfig(apiBurst = 1_000),
        scope = scope,
        clock = MonotonicClock { 0L },
        observability = observability,
    )
    val cache = ResponseCache(maxBytes = 4 * 1024 * 1024, protection = protection, observability = observability, ticker = ticker)

    fun counter(counter: ResponseCacheCounter): Long = observability.snapshot().responseCache.getValue(counter)

    fun run(install: Route.(ResponseCache) -> Unit, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureErrorHandling(failureEventSink = { failureEvents += it })
            routing { install(cache) }
        }
        block()
    }

    override fun close() = scope.cancel()
}

/**
 * The cache contract every cached public GET shares, driven through the real route and service. [ttlSeconds] is the
 * expected TTL of the success response for [path].
 *
 * A miss loads once and a hit replays the same bytes without another upstream fetch. The entry is served until one
 * nanosecond before its TTL and loaded again at the TTL. An expired entry is not served when the refresh fails.
 * An upstream network failure is not stored. A parsing failure is stored and replayed for exactly 60 seconds
 * without the upstream or a second failure event.
 */
internal suspend fun ApplicationTestBuilder.assertCachedRouteContract(
    harness: RouteCacheHarness,
    transport: CountingTransport,
    path: String,
    ttlSeconds: Long,
) {
    val ticker = harness.ticker
    val ttlNanos = ttlSeconds * 1_000_000_000L
    val misses = harness.counter(ResponseCacheCounter.MISS)
    val hits = harness.counter(ResponseCacheCounter.HIT)
    val negativeHits = harness.counter(ResponseCacheCounter.NEGATIVE_HIT)

    val callsBeforeMiss = transport.calls
    val miss = client.get(path)
    assertEquals(HttpStatusCode.OK, miss.status, path)
    val missBytes = miss.bodyAsBytes()
    val callsPerLoad = transport.calls - callsBeforeMiss
    assertTrue(callsPerLoad > 0, path)
    assertEquals(misses + 1, harness.counter(ResponseCacheCounter.MISS), path)

    val hit = client.get(path)
    assertEquals(HttpStatusCode.OK, hit.status, path)
    assertEquals("application/json", hit.headers[HttpHeaders.ContentType], path)
    assertEquals(miss.headers[HttpHeaders.ContentType], hit.headers[HttpHeaders.ContentType], path)
    assertContentEquals(missBytes, hit.bodyAsBytes(), path)
    assertEquals(callsBeforeMiss + callsPerLoad, transport.calls, "$path: a hit must not fetch upstream")
    assertEquals(hits + 1, harness.counter(ResponseCacheCounter.HIT), path)

    ticker.advanceNanos(ttlNanos - 1)
    assertContentEquals(missBytes, client.get(path).bodyAsBytes(), path)
    assertEquals(callsBeforeMiss + callsPerLoad, transport.calls, "$path must still be cached just before $ttlSeconds s")
    ticker.advanceNanos(1)
    assertContentEquals(missBytes, client.get(path).bodyAsBytes(), path)
    assertEquals(callsBeforeMiss + 2 * callsPerLoad, transport.calls, "$path must expire at $ttlSeconds s")

    // The refresh at the TTL stored a new entry. Expire it, and a failing refresh must not return the old value.
    ticker.advanceNanos(ttlNanos)
    transport.failure = UpstreamNetworkFailure(IllegalStateException("private upstream detail"))
    val network = client.get(path)
    val networkBody = network.bodyAsText()
    assertEquals(HttpStatusCode.BadGateway, network.status, path)
    assertEquals(ApiErrorCode.UPSTREAM_NETWORK_FAILURE, Json.decodeFromString<ApiErrorResponse>(networkBody).code, path)
    assertFalse(networkBody.contains("private upstream detail"), path)

    transport.failure = null
    val callsBeforeRecovery = transport.calls
    assertEquals(HttpStatusCode.OK, client.get(path).status, path)
    assertEquals(callsBeforeRecovery + callsPerLoad, transport.calls, "$path: a network failure must not be stored")
    assertEquals(negativeHits, harness.counter(ResponseCacheCounter.NEGATIVE_HIT), path)

    ticker.advanceNanos(ttlNanos)
    transport.failure = SourceParsingFailure(IllegalStateException("selector details"))
    val failureEventsBefore = harness.failureEvents.size
    val parsing = client.get(path)
    val parsingBody = parsing.bodyAsText()
    assertEquals(HttpStatusCode.BadGateway, parsing.status, path)
    assertEquals(ApiErrorCode.SOURCE_PARSING_FAILURE, Json.decodeFromString<ApiErrorResponse>(parsingBody).code, path)
    assertFalse(parsingBody.contains("selector"), path)
    assertEquals(failureEventsBefore + 1, harness.failureEvents.size, path)

    transport.failure = null
    val callsBeforeReplay = transport.calls
    ticker.advanceSeconds(59)
    val replay = client.get(path)
    assertEquals(parsing.status, replay.status, path)
    assertEquals(parsing.headers[HttpHeaders.ContentType], replay.headers[HttpHeaders.ContentType], path)
    assertEquals(parsingBody, replay.bodyAsText(), path)
    assertEquals(callsBeforeReplay, transport.calls, "$path: a cached parsing failure must not fetch upstream")
    assertEquals(failureEventsBefore + 1, harness.failureEvents.size, "$path: a replay is not a new failure event")
    assertEquals(negativeHits + 1, harness.counter(ResponseCacheCounter.NEGATIVE_HIT), path)

    ticker.advanceSeconds(1)
    assertEquals(HttpStatusCode.OK, client.get(path).status, path)
    assertEquals(callsBeforeReplay + callsPerLoad, transport.calls, "$path: a parsing failure lasts 60 seconds")
}

/** Each invalid path answers `INVALID_REQUEST` and touches neither the cache nor the upstream. */
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
    ResponseCacheCounter.entries.forEach { assertEquals(0, harness.counter(it), it.name) }
    assertEquals(callsBefore, transport.calls)
}

/**
 * The same cached bytes the route served, from a route without a cache. Used to prove a miss body equals what the
 * uncached route returns.
 */
internal fun uncachedBody(install: Route.() -> Unit, path: String): ByteArray {
    lateinit var body: ByteArray
    testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing { install() }
        }
        body = client.get(path).bodyAsBytes()
    }
    return body
}
