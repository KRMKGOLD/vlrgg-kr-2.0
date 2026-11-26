package kr.co.cotton.vlrgg_mobile.feature.matches

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.cache.FakeTicker
import kr.co.cotton.vlrgg_mobile.cache.ResponseCache
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.PublicResponseTooLargeFailure
import kr.co.cotton.vlrgg_mobile.common.http.ServerBusyFailure
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.plugins.FailureEvent
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kr.co.cotton.vlrgg_mobile.protection.MonotonicClock
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtection
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig
import kotlin.test.*

class MatchesRoutesCacheTest {
    private val service = CountingMatchesService()
    private val failureEvents = CopyOnWriteArrayList<FailureEvent>()
    private val observability = PublicApiObservability()
    private val ticker = FakeTicker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cancelScope() = scope.cancel()

    private fun counter(counter: ResponseCacheCounter) = observability.snapshot().responseCache.getValue(counter)

    private fun cachedRoutes(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val protection = PublicApiProtection(
            config = PublicApiProtectionConfig(apiBurst = 1_000),
            scope = scope,
            clock = MonotonicClock { 0L },
            observability = observability,
        )
        val cache = ResponseCache(maxBytes = 1024 * 1024, protection = protection, observability = observability, ticker = ticker)
        application {
            configureSerialization()
            configureErrorHandling(failureEventSink = { failureEvents += it })
            routing { configureMatchesRoutes(service, cache) }
        }
        block()
    }

    @Test
    fun `hit replays the miss body and content type byte for byte without calling the service`() = cachedRoutes {
        listOf("/api/v1/matches/upcoming?page=2", "/api/v1/matches/results?page=3", "/api/v1/matches/709685").forEach { path ->
            val miss = client.get(path)
            val callsAfterMiss = service.calls.get()
            val hit = client.get(path)

            assertEquals(HttpStatusCode.OK, hit.status)
            assertEquals(miss.headers[HttpHeaders.ContentType], hit.headers[HttpHeaders.ContentType])
            assertEquals("application/json", hit.headers[HttpHeaders.ContentType])
            assertContentEquals(miss.bodyAsBytes(), hit.bodyAsBytes())
            assertEquals(callsAfterMiss, service.calls.get())
        }
        assertEquals(3, service.calls.get())
        assertEquals(3, counter(ResponseCacheCounter.HIT))
        assertEquals(3, counter(ResponseCacheCounter.MISS))
    }

    @Test
    fun `miss body equals the uncached response body`() {
        lateinit var uncached: ByteArray
        testApplication {
            application {
                configureSerialization()
                configureErrorHandling()
                routing { configureMatchesRoutes(CountingMatchesService()) }
            }
            uncached = client.get("/api/v1/matches/709685").bodyAsBytes()
        }
        lateinit var cached: ByteArray
        cachedRoutes { cached = client.get("/api/v1/matches/709685").bodyAsBytes() }
        assertContentEquals(uncached, cached)
    }

    @Test
    fun `omitted page and page 1 share one key and routes never share keys`() = cachedRoutes {
        client.get("/api/v1/matches/upcoming")
        client.get("/api/v1/matches/upcoming?page=1")
        client.get("/api/v1/matches/upcoming?page=2")
        client.get("/api/v1/matches/results")
        client.get("/api/v1/matches/results?page=1")
        client.get("/api/v1/matches/results?page=2")
        client.get("/api/v1/matches/1")

        assertEquals(5, service.calls.get())
        assertEquals(listOf(1, 2, 1, 2), service.listPages)
    }

    @Test
    fun `list ttl is 30 or 120 seconds on page 1 and 300 seconds after`() = cachedRoutes {
        val cases = listOf(
            "/api/v1/matches/upcoming" to 30L,
            "/api/v1/matches/upcoming?page=2" to 300L,
            "/api/v1/matches/results" to 120L,
            "/api/v1/matches/results?page=2" to 300L,
        )
        cases.forEach { (path, ttl) -> assertTtl(path, ttl) }
    }

    @Test
    fun `match detail ttl is 30 seconds when live and 300 seconds otherwise`() = cachedRoutes {
        service.detailStatus = MatchStatus.LIVE
        assertTtl("/api/v1/matches/1", 30)
        service.detailStatus = MatchStatus.UPCOMING
        assertTtl("/api/v1/matches/2", 300)
        service.detailStatus = MatchStatus.COMPLETED
        assertTtl("/api/v1/matches/3", 300)
    }

    @Test
    fun `upstream network and other failures are not stored and the next request calls the service again`() = cachedRoutes {
        service.failure = UpstreamNetworkFailure()
        assertError(client.get("/api/v1/matches/709685"), HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
        assertError(client.get("/api/v1/matches/709685"), HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
        service.failure = ServerBusyFailure()
        assertError(client.get("/api/v1/matches/upcoming"), HttpStatusCode.ServiceUnavailable, ApiErrorCode.SERVER_BUSY)
        service.failure = PublicResponseTooLargeFailure(IllegalStateException())
        assertError(client.get("/api/v1/matches/results"), HttpStatusCode.BadGateway, ApiErrorCode.RESPONSE_TOO_LARGE)
        service.failure = null
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/709685").status)

        assertEquals(5, service.calls.get())
        assertEquals(0, counter(ResponseCacheCounter.NEGATIVE_HIT))
    }

    @Test
    fun `source parsing failure is replayed for 60 seconds without the service or a failure event`() = cachedRoutes {
        service.failure = SourceParsingFailure(IllegalStateException("selector details"))
        val first = client.get("/api/v1/matches/709685")
        assertError(first, HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
        assertEquals(1, failureEvents.size)
        assertContains(failureEvents.single().json, "\"error_code\":\"SOURCE_PARSING_FAILURE\"")

        service.failure = null
        ticker.advanceSeconds(59)
        val replay = client.get("/api/v1/matches/709685")
        assertEquals(first.status, replay.status)
        assertEquals(first.headers[HttpHeaders.ContentType], replay.headers[HttpHeaders.ContentType])
        assertEquals(first.bodyAsText(), replay.bodyAsText())
        assertEquals(
            ApiErrorResponse(ApiErrorCode.SOURCE_PARSING_FAILURE, "Unable to parse data from the upstream source."),
            Json.decodeFromString<ApiErrorResponse>(replay.bodyAsText()),
        )
        assertEquals(1, service.calls.get())
        assertEquals(1, failureEvents.size)
        assertEquals(1, counter(ResponseCacheCounter.NEGATIVE_HIT))

        ticker.advanceSeconds(1)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/709685").status)
        assertEquals(2, service.calls.get())
    }

    @Test
    fun `an expired entry is never served when the refresh fails`() = cachedRoutes {
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/709685").status)
        ticker.advanceSeconds(300)
        service.failure = UpstreamNetworkFailure()

        val response = client.get("/api/v1/matches/709685")
        assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
        assertFalse(response.bodyAsText().contains("709685"))
        assertEquals(2, service.calls.get())
    }

    @Test
    fun `invalid requests never reach the cache`() = cachedRoutes {
        listOf(
            "/api/v1/matches/upcoming?page=0",
            "/api/v1/matches/upcoming?page=01",
            "/api/v1/matches/results?unexpected=true",
            "/api/v1/matches/0001",
            "/api/v1/matches/12abc",
        ).forEach { assertError(client.get(it), HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST) }

        assertEquals(0, service.calls.get())
        ResponseCacheCounter.entries.forEach { assertEquals(0, counter(it), it.name) }
    }

    @Test
    fun `concurrent requests for one key call the service once and all receive the same body`() = cachedRoutes {
        val release = CompletableDeferred<Unit>()
        service.gate = release
        val responses = coroutineScope {
            val requests = List(8) { async(Dispatchers.Default) { client.get("/api/v1/matches/709685") } }
            withTimeout(5_000) { while (counter(ResponseCacheCounter.JOINED) < 7L) yield() }
            release.complete(Unit)
            requests.awaitAll()
        }

        assertEquals(1, service.calls.get())
        assertTrue(responses.all { it.status == HttpStatusCode.OK })
        val first = responses.first().bodyAsBytes()
        responses.forEach { assertContentEquals(first, it.bodyAsBytes()) }
        assertEquals(1, counter(ResponseCacheCounter.MISS))
    }

    /** Hit until one nanosecond before [ttlSeconds], then a fresh service call at the boundary. */
    private suspend fun ApplicationTestBuilder.assertTtl(path: String, ttlSeconds: Long) {
        val before = service.calls.get()
        assertEquals(HttpStatusCode.OK, client.get(path).status)
        ticker.advanceNanos(ttlSeconds * 1_000_000_000L - 1)
        assertEquals(HttpStatusCode.OK, client.get(path).status)
        assertEquals(before + 1, service.calls.get(), "$path must still be cached just before $ttlSeconds s")
        ticker.advanceNanos(1)
        assertEquals(HttpStatusCode.OK, client.get(path).status)
        assertEquals(before + 2, service.calls.get(), "$path must expire at $ttlSeconds s")
    }

    private suspend fun assertError(response: HttpResponse, status: HttpStatusCode, code: ApiErrorCode) {
        val body = response.bodyAsText()
        assertEquals(status, response.status)
        assertEquals(code, Json.decodeFromString<ApiErrorResponse>(body).code)
        assertFalse(body.contains("selector"))
    }

    private class CountingMatchesService : MatchesService {
        val calls = AtomicInteger()
        val listPages = CopyOnWriteArrayList<Int>()
        @Volatile var failure: Exception? = null
        @Volatile var detailStatus: MatchStatus = MatchStatus.UPCOMING
        @Volatile var gate: CompletableDeferred<Unit>? = null

        override suspend fun getMatches(category: MatchListCategory, page: Int): MatchesPageResponse {
            calls.incrementAndGet()
            listPages += page
            gate?.await()
            failure?.let { throw it }
            return MatchesPageResponse(
                category = category,
                page = page,
                groups = listOf(
                    MatchDateGroupResponse(
                        dateLabel = "오늘 · Today",
                        date = "2026-11-24",
                        matches = listOf(
                            MatchSummaryResponse(
                                id = "709685",
                                status = MatchStatus.UPCOMING,
                                timeLabel = "5:00 PM",
                                homeTeam = MatchTeamResponse("팀 \"Alpha\" \\ 🎮"),
                                awayTeam = MatchTeamResponse("Beta"),
                                event = MatchEventResponse("Event"),
                            ),
                        ),
                    ),
                ),
            )
        }

        override suspend fun getMatch(matchId: String): MatchDetailResponse {
            calls.incrementAndGet()
            gate?.await()
            failure?.let { throw it }
            return MatchDetailResponse(
                id = matchId,
                status = detailStatus,
                timeLabel = "LIVE",
                homeTeam = MatchTeamResponse("한글 팀"),
                awayTeam = MatchTeamResponse("Beta"),
                event = MatchEventResponse("Event"),
                maps = emptyList(),
                headToHead = emptyList(),
                pastMatches = emptyList(),
            )
        }
    }
}
