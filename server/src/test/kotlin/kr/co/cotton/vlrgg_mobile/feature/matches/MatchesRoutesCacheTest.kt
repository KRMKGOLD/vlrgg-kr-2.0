package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.cache.FakeResponseCacheStore
import kr.co.cotton.vlrgg_mobile.cache.ResponseCache
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheFailureKind
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheStoreException
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheWarnings
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
import kotlin.test.*

class MatchesRoutesCacheTest {
    private val store = FakeResponseCacheStore()
    private val service = CountingMatchesService()
    private val failureEvents = CopyOnWriteArrayList<FailureEvent>()
    private val observability = PublicApiObservability()

    private fun counter(counter: ResponseCacheCounter) = observability.snapshot().responseCache.getValue(counter)

    private fun cachedRoutes(timeoutMillis: Long = 200, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val cache = ResponseCache(
            store = store,
            deploySha = SHA,
            observability = observability,
            warnings = ResponseCacheWarnings(environment = emptyMap(), sink = {}),
            operationTimeoutMillis = timeoutMillis,
        )
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
    fun `stored value is the exact success json the client receives`() = cachedRoutes {
        val body = client.get("/api/v1/matches/709685").bodyAsText()
        assertEquals(body, store.values.getValue("$SHA:match-detail:709685"))
    }

    @Test
    fun `omitted page and page 1 share one key and ttl follows the route table`() = cachedRoutes {
        client.get("/api/v1/matches/upcoming")
        client.get("/api/v1/matches/upcoming?page=1")
        client.get("/api/v1/matches/upcoming?page=2")
        client.get("/api/v1/matches/results")
        client.get("/api/v1/matches/results?page=1")
        client.get("/api/v1/matches/results?page=2")

        assertEquals(4, service.calls.get())
        assertEquals(
            listOf(
                "$SHA:matches-upcoming:1" to 30,
                "$SHA:matches-upcoming:2" to 300,
                "$SHA:matches-results:1" to 120,
                "$SHA:matches-results:2" to 300,
            ),
            store.sets.map { it.key to it.ttlSeconds },
        )
        assertTrue(store.sets.none { it.onlyIfAbsent })
    }

    @Test
    fun `match detail ttl is 30 seconds when live and 300 seconds otherwise`() = cachedRoutes {
        service.detailStatus = MatchStatus.LIVE
        client.get("/api/v1/matches/1")
        service.detailStatus = MatchStatus.UPCOMING
        client.get("/api/v1/matches/2")
        service.detailStatus = MatchStatus.COMPLETED
        client.get("/api/v1/matches/3")

        assertEquals(listOf(30, 300, 300), store.sets.map { it.ttlSeconds })
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

        assertEquals(4, service.calls.get())
        assertTrue(store.sets.isEmpty())
    }

    @Test
    fun `source parsing failure is cached for 60 seconds with NX and replayed without the service or a failure event`() = cachedRoutes {
        service.failure = SourceParsingFailure(IllegalStateException("selector details"))
        val first = client.get("/api/v1/matches/709685")
        assertError(first, HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
        assertEquals(
            FakeResponseCacheStore.SetCall("$SHA:match-detail:709685", "[502,\"SOURCE_PARSING_FAILURE\"]", 60, true),
            store.sets.single(),
        )
        assertEquals(1, failureEvents.size)
        assertContains(failureEvents.single().json, "\"error_code\":\"SOURCE_PARSING_FAILURE\"")

        service.failure = null
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
    }

    @Test
    fun `a negative store failure never hides the original parsing failure`() = cachedRoutes {
        store.beforeSet = { throw ResponseCacheStoreException(ResponseCacheFailureKind.HTTP) }
        service.failure = SourceParsingFailure(IllegalStateException("selector details"))
        assertError(client.get("/api/v1/matches/709685"), HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
        assertEquals(1, counter(ResponseCacheCounter.STORE_FAILED))
    }

    @Test
    fun `an expired entry is never served when the refresh fails`() = cachedRoutes {
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/709685").status)
        store.expire("$SHA:match-detail:709685")
        service.failure = UpstreamNetworkFailure()

        val response = client.get("/api/v1/matches/709685")
        assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
        assertFalse(response.bodyAsText().contains("709685"))
        assertEquals(2, service.calls.get())
    }

    @Test
    fun `slow or failing lookups fall back to the service`() = cachedRoutes(timeoutMillis = 50) {
        store.values["$SHA:match-detail:1"] = "{\"stale\":true}"
        store.beforeGet = { delay(5_000) }
        val slow = client.get("/api/v1/matches/1")
        assertEquals(HttpStatusCode.OK, slow.status)
        assertFalse(slow.bodyAsText().contains("stale"))

        store.beforeGet = { throw ResponseCacheStoreException(ResponseCacheFailureKind.IO) }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/2").status)

        assertEquals(2, service.calls.get())
        assertEquals(2, counter(ResponseCacheCounter.LOOKUP_FAILED))
    }

    @Test
    fun `store failures and timeouts still answer 200 and count store_failed`() = cachedRoutes(timeoutMillis = 50) {
        store.beforeSet = { throw ResponseCacheStoreException(ResponseCacheFailureKind.HTTP) }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/1").status)
        store.beforeSet = { delay(5_000) }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/2").status)

        assertEquals(2, counter(ResponseCacheCounter.STORE_FAILED))
    }

    @Test
    fun `invalid requests never reach the store`() = cachedRoutes {
        listOf(
            "/api/v1/matches/upcoming?page=0",
            "/api/v1/matches/upcoming?page=01",
            "/api/v1/matches/results?unexpected=true",
            "/api/v1/matches/0001",
            "/api/v1/matches/12abc",
        ).forEach { assertError(client.get(it), HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST) }

        assertTrue(store.gets.isEmpty())
        assertTrue(store.sets.isEmpty())
        assertEquals(0, service.calls.get())
    }

    private suspend fun assertError(response: HttpResponse, status: HttpStatusCode, code: ApiErrorCode) {
        val body = response.bodyAsText()
        assertEquals(status, response.status)
        assertEquals(code, Json.decodeFromString<ApiErrorResponse>(body).code)
        assertFalse(body.contains("selector"))
    }

    private class CountingMatchesService : MatchesService {
        val calls = AtomicInteger()
        @Volatile var failure: Exception? = null
        @Volatile var detailStatus: MatchStatus = MatchStatus.UPCOMING

        override suspend fun getMatches(category: MatchListCategory, page: Int): MatchesPageResponse {
            calls.incrementAndGet()
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

    private companion object {
        const val SHA = "test-sha"
    }
}
