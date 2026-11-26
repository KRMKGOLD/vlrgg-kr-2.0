package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.FakeResponseCacheStore
import kr.co.cotton.vlrgg_mobile.cache.SOURCE_PARSING_FAILURE_CACHE_VALUE
import kr.co.cotton.vlrgg_mobile.cache.TEST_DEPLOY_SHA
import kr.co.cotton.vlrgg_mobile.cache.enabledTestCacheConfig
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.feature.events.createEventsService
import kr.co.cotton.vlrgg_mobile.feature.matches.createMatchesService
import kr.co.cotton.vlrgg_mobile.feature.news.createDefaultNewsService
import kr.co.cotton.vlrgg_mobile.feature.player.createPlayerDetailService
import kr.co.cotton.vlrgg_mobile.feature.search.createSearchService
import kr.co.cotton.vlrgg_mobile.feature.series.createSeriesService
import kr.co.cotton.vlrgg_mobile.feature.teams.createTeamDetailService
import kr.co.cotton.vlrgg_mobile.module
import kotlin.test.*

/** Verifies the cache is wired through `module` and `configureRouting` for every route, and Search stays uncached. */
class ResponseCacheRoutingTest {
    private val store = FakeResponseCacheStore()
    private val transport = CountingTransport { error("unreachable") }.also {
        it.failure = SourceParsingFailure(IllegalStateException("drift"))
    }

    private fun wired(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            module(
                newsService = createDefaultNewsService(transport),
                eventsService = createEventsService(transport),
                searchService = createSearchService(transport),
                seriesService = createSeriesService(transport),
                teamDetailService = createTeamDetailService(transport),
                playerDetailService = createPlayerDetailService(transport),
                matchesService = createMatchesService(transport),
                responseCacheConfig = enabledTestCacheConfig(),
                responseCacheStore = store,
            )
        }
        block()
    }

    @Test
    fun `every cached route reaches the store under its own key`() = wired {
        val expected = mapOf(
            "/api/v1/matches/upcoming" to "matches-upcoming:1",
            "/api/v1/matches/results" to "matches-results:1",
            "/api/v1/matches/709685" to "match-detail:709685",
            "/api/v1/events" to "events-list:all",
            "/api/v1/events/100" to "event-detail:100",
            "/api/v1/events/100/matches" to "event-matches:100",
            "/api/v1/events/100/news" to "event-news:100",
            "/api/v1/events/100/stats" to "event-stats:100",
            "/api/v1/teams/100" to "team-detail:100",
            "/api/v1/news" to "news-list:1",
            "/api/v1/news/101/champions-run" to "news-article:101:champions-run",
            "/api/v1/players/100" to "player-detail:100",
            "/api/v1/series/100" to "series-detail:100",
        )
        expected.keys.forEach { path -> assertEquals(HttpStatusCode.BadGateway, client.get(path).status, path) }

        assertEquals(expected.values.map { "$TEST_DEPLOY_SHA:$it" }, store.sets.map { it.key })
        assertTrue(store.sets.all { it.value == SOURCE_PARSING_FAILURE_CACHE_VALUE && it.onlyIfAbsent && it.ttlSeconds == 60 })
    }

    @Test
    fun `search is never looked up or stored`() = wired {
        listOf("/api/v1/search?q=tenz", "/api/v1/search?q=tenz").forEach { path ->
            assertEquals(HttpStatusCode.BadGateway, client.get(path).status)
        }

        assertEquals(2, transport.calls)
        assertTrue(store.gets.isEmpty())
        assertTrue(store.sets.isEmpty())
        assertTrue(store.values.isEmpty())
    }
}
