package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.cache.FakeTicker
import kr.co.cotton.vlrgg_mobile.cache.ResponseCache
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheConfig
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchDetailResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchEventResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchTeamResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesPageResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesService
import kr.co.cotton.vlrgg_mobile.feature.matches.configureMatchesRoutes
import kr.co.cotton.vlrgg_mobile.feature.teams.configureTeamDetailRoutes
import kr.co.cotton.vlrgg_mobile.feature.teams.createTeamDetailService
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kotlin.test.*

/**
 * Whole-request limit versus upstream work limit, through the production composition (`module`) wherever possible.
 * Upstream work is exhausted by holding its only permit, so results never depend on token refill timing.
 */
class UpstreamWorkLimitTest {
    @Test
    fun `every public data route is rejected when the cache is empty and upstream work is exhausted`() =
        assertEveryDataRouteTakesUpstreamWork(ResponseCacheConfig())

    @Test
    fun `every public data route is rejected when the cache is disabled and upstream work is exhausted`() =
        assertEveryDataRouteTakesUpstreamWork(ResponseCacheConfig.DISABLED)

    private fun assertEveryDataRouteTakesUpstreamWork(cacheConfig: ResponseCacheConfig) = testApplication {
        val transport = HeldTransport()
        application {
            module(
                protectionConfig = singleUpstreamWorkConfig(),
                responseCacheConfig = cacheConfig,
                upstreamTransport = transport,
            )
        }

        coroutineScope {
            val holder = async(Dispatchers.Default) { client.get("/api/v1/matches/999") }
            transport.started.await()
            DATA_ROUTES.forEach { path ->
                val response = client.get(path)
                val body = response.bodyAsText()
                assertTrue(response.status.value in setOf(429, 503), "$path answered ${response.status}: $body")
                val code = Json.decodeFromString<ApiErrorResponse>(body).code
                assertTrue(code == ApiErrorCode.RATE_LIMITED || code == ApiErrorCode.SERVER_BUSY, "$path answered $code")
                assertNotNull(response.headers[HttpHeaders.RetryAfter], path)
            }
            // The rejections came from the upstream work limit: no route reached the fetch admission or the transport.
            assertEquals(1, transport.fetches.get())
            transport.release.complete(Unit)
            assertEquals(HttpStatusCode.BadGateway, holder.await().status)
        }
    }

    @Test
    fun `a cache hit is served while upstream work is exhausted but a miss is rejected`() = testApplication {
        val transport = HeldTransport()
        val matches = CountingMatchesService()
        application {
            module(
                matchesService = matches,
                protectionConfig = singleUpstreamWorkConfig(),
                responseCacheConfig = ResponseCacheConfig(),
                upstreamTransport = transport,
            )
        }

        val primed = client.get("/api/v1/matches/1")
        assertEquals(HttpStatusCode.OK, primed.status)
        coroutineScope {
            val holder = async(Dispatchers.Default) { client.get("/api/v1/events") }
            transport.started.await()

            val hit = client.get("/api/v1/matches/1")
            assertEquals(HttpStatusCode.OK, hit.status)
            assertContentEquals(primed.bodyAsBytes(), hit.bodyAsBytes())
            assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/matches/2").status)
            assertEquals(1, matches.calls.get())

            transport.release.complete(Unit)
            assertEquals(HttpStatusCode.BadGateway, holder.await().status)
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/2").status)
    }

    @Test
    fun `the whole-request limit also bounds cache hits and leaves health exempt`() = testApplication {
        val config = PublicApiProtectionConfig(requestsPerSecond = 1, requestBurst = 2)
        val observability = PublicApiObservability()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val protection = PublicApiProtection(config, scope, MonotonicClock { 0L }, observability)
        val matches = CountingMatchesService()
        application {
            configureSerialization()
            configureErrorHandling(observability)
            configurePublicRequestProtection(protection, config)
            routing {
                get("/health") { call.respondText("ok") }
                configureMatchesRoutes(matches, ResponseCache(1024 * 1024, protection, observability, FakeTicker()))
            }
        }

        try {
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/1").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/1").status)
            val limited = client.get("/api/v1/matches/1")
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("1", limited.headers[HttpHeaders.RetryAfter])
            assertEquals(ApiErrorCode.RATE_LIMITED, Json.decodeFromString<ApiErrorResponse>(limited.bodyAsText()).code)
            assertEquals(HttpStatusCode.OK, client.get("/health").status)
            assertEquals(1, matches.calls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `unknown routes and rejected input take no upstream work token`() = testApplication {
        val config = PublicApiProtectionConfig(apiRequestsPerSecond = 1, apiBurst = 1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val protection = PublicApiProtection(config, scope, MonotonicClock { 0L })
        val matches = CountingMatchesService()
        application {
            configureSerialization()
            configureErrorHandling()
            configurePublicRequestProtection(protection, config)
            routing { configureMatchesRoutes(matches, ResponseCache(1024 * 1024, protection, ticker = FakeTicker())) }
        }

        try {
            assertEquals(HttpStatusCode.NotFound, client.get("/unknown").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/matches/0001").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/matches/upcoming?page=0").status)
            // The single upstream work token is still available for the first real miss, then exhausted.
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/1").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/1").status)
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/matches/2").status)
            assertEquals(1, matches.calls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a request carries one upstream work slot through the pipeline into child coroutines`() = testApplication {
        application {
            module(upstreamTransport = HeldTransport())
            routing {
                get("/test/upstream-work") {
                    val outer = currentCoroutineContext()[UpstreamWork]
                    val inner = coroutineScope { async(Dispatchers.Default) { currentCoroutineContext()[UpstreamWork] }.await() }
                    call.respondText("${outer != null && outer === inner}")
                }
            }
        }

        assertEquals("true", client.get("/test/upstream-work").bodyAsText())
    }

    @Test
    fun `team detail parallel fetches share the request's single upstream work slot`() = testApplication {
        val started = AtomicInteger()
        val bothStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = object : UpstreamHtmlTransport {
            override suspend fun get(url: Url): String {
                if (started.incrementAndGet() == 2) bothStarted.complete(Unit)
                release.await()
                throw UpstreamNetworkFailure()
            }
        }
        application { module(protectionConfig = singleUpstreamWorkConfig(), upstreamTransport = transport) }

        coroutineScope {
            val response = async(Dispatchers.Default) { client.get("/api/v1/teams/1") }
            withTimeout(5_000) { bothStarted.await() }
            release.complete(Unit)
            assertEquals(HttpStatusCode.BadGateway, response.await().status)
        }
        assertEquals(2, started.get())
    }

    @Test
    fun `team detail parallel fetches consume exactly one upstream work token`() = testApplication {
        val config = PublicApiProtectionConfig(apiRequestsPerSecond = 1, apiBurst = 1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val protection = PublicApiProtection(config, scope, MonotonicClock { 0L })
        val fetches = AtomicInteger()
        val bothStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = object : UpstreamHtmlTransport {
            override suspend fun get(url: Url): String {
                // An immediate failure could cancel the sibling fetch before it starts; hold both until both began.
                if (fetches.incrementAndGet() == 2) bothStarted.complete(Unit)
                release.await()
                throw UpstreamNetworkFailure()
            }
        }.withPublicProtection(protection)
        application {
            configureSerialization()
            configureErrorHandling()
            configurePublicRequestProtection(protection, config)
            routing { configureTeamDetailRoutes(createTeamDetailService(transport)) }
        }

        try {
            // Both fetches of one request run on the single token; a second token would have been a 429.
            coroutineScope {
                val response = async(Dispatchers.Default) { client.get("/api/v1/teams/1") }
                withTimeout(5_000) { bothStarted.await() }
                release.complete(Unit)
                assertEquals(HttpStatusCode.BadGateway, response.await().status)
            }
            assertEquals(2, fetches.get())
            // The token is spent: the next request is rejected before it fetches anything.
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/teams/2").status)
            assertEquals(2, fetches.get())
        } finally {
            scope.cancel()
        }
    }

    private fun singleUpstreamWorkConfig() = PublicApiProtectionConfig(
        apiRequestsPerSecond = 100,
        apiBurst = 100,
        maxActiveApiRequests = 1,
        upstreamRequestsPerSecond = 100,
        upstreamBurst = 100,
        maxActiveUpstreamRequests = 8,
        maxInFlightCanonicalUrls = 8,
    )

    /** Holds the first fetch until [release]; any later fetch would be counted and fail the route assertions. */
    private class HeldTransport : UpstreamHtmlTransport {
        val fetches = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun get(url: Url): String {
            fetches.incrementAndGet()
            started.complete(Unit)
            release.await()
            throw UpstreamNetworkFailure()
        }
    }

    private class CountingMatchesService : MatchesService {
        val calls = AtomicInteger()

        override suspend fun getMatches(category: MatchListCategory, page: Int): MatchesPageResponse {
            calls.incrementAndGet()
            return MatchesPageResponse(category, page, emptyList())
        }

        override suspend fun getMatch(matchId: String): MatchDetailResponse {
            calls.incrementAndGet()
            return MatchDetailResponse(
                id = matchId,
                status = MatchStatus.UPCOMING,
                timeLabel = "5:00 PM",
                homeTeam = MatchTeamResponse("Alpha"),
                awayTeam = MatchTeamResponse("Beta"),
                event = MatchEventResponse("Event"),
                maps = emptyList(),
                headToHead = emptyList(),
                pastMatches = emptyList(),
            )
        }
    }

    private companion object {
        /** One valid request per public data operation, so each passes validation and needs upstream work. */
        val DATA_ROUTES = listOf(
            "/api/v1/matches/upcoming",
            "/api/v1/matches/upcoming?page=2",
            "/api/v1/matches/results",
            "/api/v1/matches/results?page=2",
            "/api/v1/matches/1",
            "/api/v1/events",
            "/api/v1/events/1",
            "/api/v1/events/1/matches",
            "/api/v1/events/1/news",
            "/api/v1/events/1/stats",
            "/api/v1/teams/1",
            "/api/v1/news",
            "/api/v1/news?page=2",
            "/api/v1/news/1/sample-article",
            "/api/v1/players/1",
            "/api/v1/series/1",
            "/api/v1/search?q=sentinels",
        )
    }
}
