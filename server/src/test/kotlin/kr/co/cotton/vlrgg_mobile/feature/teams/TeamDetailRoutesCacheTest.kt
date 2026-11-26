package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheRoute
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
import kotlin.test.*

class TeamDetailRoutesCacheTest {
    private val harness = RouteCacheHarness()
    private var overviewHtml = fixture("active-team-overview.html")
    private val transport = CountingTransport { url ->
        when {
            url.encodedPath.startsWith("/team/news/") -> fixture("active-team-news.html")
            url.encodedPath.startsWith("/team/") -> overviewHtml
            else -> error(url.encodedPath)
        }
    }

    private fun teams(block: suspend ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configureTeamDetailRoutes(createTeamDetailService(transport), cache) }, block)

    private fun teamKey() = harness.key(ResponseCacheRoute.TEAM_DETAIL, "8185")

    @Test
    fun `countdown and score status texts keep the five minute ttl`() = teams {
        assertCachedRouteContract(harness, transport, "/api/v1/teams/8185", teamKey(), 300)
    }

    @Test
    fun `a live row in Upcoming matches shortens the ttl to 30 seconds`() = teams {
        overviewHtml = fixture("live-team-overview.html")
        assertCachedRouteContract(harness, transport, "/api/v1/teams/8185", teamKey(), 30)
    }

    @Test
    fun `a live row in Recent results also shortens the ttl to 30 seconds`() = teams {
        overviewHtml = fixture("active-team-overview.html").replace(
            "<div class=\"m-item-result\"><span>0</span><div>:</div><span>2</span></div>",
            "<div class=\"m-item-result mod-live fc-flex \">LIVE <i class=\"fa fa-caret-down\"></i></div>",
        )
        assertCachedRouteContract(harness, transport, "/api/v1/teams/8185", teamKey(), 30)
    }

    @Test
    fun `team ttl follows only the literal LIVE status text`() {
        fun response(upcoming: List<String?>, recent: List<String?> = emptyList()) = TeamDetailResponse(
            id = "1",
            name = "Team",
            tag = null,
            country = null,
            upcomingMatches = upcoming.mapIndexed { index, text -> match("u$index", text) },
            recentMatches = recent.mapIndexed { index, text -> match("r$index", text) },
            players = emptyList(),
            staff = emptyList(),
            news = emptyList(),
        )

        assertEquals(30, teamDetailTtlSeconds(response(listOf("LIVE"))))
        assertEquals(30, teamDetailTtlSeconds(response(listOf("4d 2h"), recent = listOf("0 : 2", " live "))))
        assertEquals(30, teamDetailTtlSeconds(response(listOf(null, "Live"))))
        assertEquals(300, teamDetailTtlSeconds(response(listOf("4d 2h", "1d 6h"), recent = listOf("0 : 2"))))
        assertEquals(300, teamDetailTtlSeconds(response(listOf(null, ""), recent = listOf("LIVE NOW", "TBD", "Delayed"))))
        assertEquals(300, teamDetailTtlSeconds(response(emptyList())))
    }

    @Test
    fun `cache is per team id and a cached Team is not served for another id`() = teams {
        val first = client.get("/api/v1/teams/8185").bodyAsText()
        val second = client.get("/api/v1/teams/9999")
        assertEquals(HttpStatusCode.OK, second.status)

        assertEquals(
            setOf(harness.key(ResponseCacheRoute.TEAM_DETAIL, "8185"), harness.key(ResponseCacheRoute.TEAM_DETAIL, "9999")),
            harness.store.sets.map { it.key }.toSet(),
        )
        assertContains(first, "\"id\":\"8185\"")
    }

    @Test
    fun `invalid team requests never reach the store or upstream`() = teams {
        assertInvalidRequestsSkipCacheAndUpstream(
            harness,
            transport,
            listOf(
                "/api/v1/teams",
                "/api/v1/teams/",
                "/api/v1/teams/0",
                "/api/v1/teams/001",
                "/api/v1/teams/10000000000",
                "/api/v1/teams/not-a-number",
                "/api/v1/teams/8185?view=all",
                "/api/v1/teams/8185?unknown=1",
            ),
        )
        assertEquals(0, transport.calls)
    }

    private fun match(id: String, statusText: String?) = TeamMatchResponse(
        id = id,
        eventName = null,
        eventStage = null,
        teamName = "Team",
        opponentName = "Opponent",
        statusText = statusText,
        scheduledAtText = null,
    )

    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader.getResource("fixtures/teams/$name")).readText()
}
