package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheConfig
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.feature.events.createEventsService
import kr.co.cotton.vlrgg_mobile.feature.matches.createMatchesService
import kr.co.cotton.vlrgg_mobile.feature.news.createDefaultNewsService
import kr.co.cotton.vlrgg_mobile.feature.player.createPlayerDetailService
import kr.co.cotton.vlrgg_mobile.feature.search.createSearchService
import kr.co.cotton.vlrgg_mobile.feature.series.createSeriesService
import kr.co.cotton.vlrgg_mobile.feature.teams.createTeamDetailService
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig
import kotlin.test.*

/** Verifies the cache is wired through `module` and `configureRouting` for every route, and Search stays uncached. */
class ResponseCacheRoutingTest {
    private val cachedPaths = listOf(
        "/api/v1/matches/upcoming",
        "/api/v1/matches/results",
        "/api/v1/matches/709685",
        "/api/v1/events",
        "/api/v1/events/100",
        "/api/v1/events/100/matches",
        "/api/v1/events/100/news",
        "/api/v1/events/100/stats",
        "/api/v1/teams/100",
        "/api/v1/news",
        "/api/v1/news/100/champions-run",
        "/api/v1/players/100",
        "/api/v1/series/100",
    )

    @Test
    fun `every cached route is served through the cache and none shares an entry with another`() {
        val transport = CountingTransport { error("unreachable") }.also {
            it.failure = SourceParsingFailure(IllegalStateException("drift"))
        }
        testApplication {
            application {
                module(
                    upstreamTransport = transport,
                    protectionConfig = PublicApiProtectionConfig(apiBurst = 1_000, upstreamBurst = 1_000, requestBurst = 1_000),
                    responseCacheConfig = ResponseCacheConfig(),
                )
            }

            // A failed load is stored for 60 seconds. If a route did not use the cache, or two routes shared a key,
            // either its first request would not reach upstream or its second request would.
            cachedPaths.forEach { path ->
                val before = transport.calls
                assertEquals(HttpStatusCode.BadGateway, client.get(path).status, path)
                assertTrue(transport.calls > before, "$path: the first request loads from upstream")
                val afterFirst = transport.calls
                assertEquals(HttpStatusCode.BadGateway, client.get(path).status, path)
                assertEquals(afterFirst, transport.calls, "$path: the second request is a cache replay")
            }
        }
    }

    @Test
    fun `search reaches upstream on every request even with the cache enabled`() {
        val transport = CountingTransport { error("unreachable") }.also {
            it.failure = SourceParsingFailure(IllegalStateException("drift"))
        }
        testApplication {
            application {
                module(
                    upstreamTransport = transport,
                    protectionConfig = PublicApiProtectionConfig(apiBurst = 1_000, upstreamBurst = 1_000, requestBurst = 1_000),
                    responseCacheConfig = ResponseCacheConfig(),
                )
            }

            repeat(3) { assertEquals(HttpStatusCode.BadGateway, client.get("/api/v1/search?q=tenz").status) }
            assertEquals(3, transport.calls)
        }
    }

    @Test
    fun `search is served without touching the cache while a cached route counts through it`() {
        val harness = RouteCacheHarness()
        val transport = CountingTransport { url ->
            when {
                url.encodedPath.startsWith("/search") -> fixture("search/mixed-results.html")
                url.encodedPath.startsWith("/news") -> fixture("news-list-page-1.html")
                else -> error(url.encodedPath)
            }
        }
        try {
            testApplication {
                application {
                    configureSerialization()
                    configureErrorHandling()
                    configureRouting(
                        newsService = createDefaultNewsService(transport),
                        eventsService = createEventsService(transport),
                        searchService = createSearchService(transport),
                        seriesService = createSeriesService(transport),
                        teamDetailService = createTeamDetailService(transport),
                        playerDetailService = createPlayerDetailService(transport),
                        matchesService = createMatchesService(transport),
                        enableApiDocumentation = false,
                        responseCache = harness.cache,
                    )
                }

                val first = client.get("/api/v1/search?q=tenz")
                val second = client.get("/api/v1/search?q=tenz")
                assertEquals(HttpStatusCode.OK, first.status)
                assertEquals(HttpStatusCode.OK, second.status)
                assertEquals(2, transport.calls)
                ResponseCacheCounter.entries.forEach { assertEquals(0, harness.counter(it), it.name) }

                // Positive control: the same cache counts a cached route.
                assertEquals(HttpStatusCode.OK, client.get("/api/v1/news").status)
                assertEquals(1, harness.counter(ResponseCacheCounter.MISS))
            }
        } finally {
            harness.close()
        }
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/$name")).readText()
}
