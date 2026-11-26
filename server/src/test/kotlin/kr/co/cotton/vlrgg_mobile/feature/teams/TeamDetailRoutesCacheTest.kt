package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
import kr.co.cotton.vlrgg_mobile.cache.uncachedBody
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
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

    @AfterTest
    fun close() = harness.close()

    private fun teams(block: suspend ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configureTeamDetailRoutes(createTeamDetailService(transport), cache) }, block)

    @Test
    fun `countdown and score status texts keep the five minute ttl`() = teams {
        assertCachedRouteContract(harness, transport, "/api/v1/teams/8185", 300)
    }

    @Test
    fun `a live row in Upcoming matches shortens the ttl to 30 seconds`() = teams {
        overviewHtml = fixture("live-team-overview.html")
        assertCachedRouteContract(harness, transport, "/api/v1/teams/8185", 30)
    }

    @Test
    fun `a live row in Recent results also shortens the ttl to 30 seconds`() = teams {
        val live = "<div class=\"m-item-result mod-live fc-flex \">LIVE <i class=\"fa fa-caret-down\"></i></div>"
        val score = "<div class=\"m-item-result\"><span>0</span><div>:</div><span>2</span></div>"
        assertTrue(fixture("active-team-overview.html").contains(score))
        overviewHtml = fixture("active-team-overview.html").replace(score, live)
        assertCachedRouteContract(harness, transport, "/api/v1/teams/8185", 30)
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
    fun `each team id has its own entry`() = teams {
        val first = client.get("/api/v1/teams/8185").bodyAsText()
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/teams/9999").status)
        client.get("/api/v1/teams/8185")

        assertEquals(4, transport.calls)
        assertEquals(2, harness.counter(ResponseCacheCounter.MISS))
        assertEquals(1, harness.counter(ResponseCacheCounter.HIT))
        assertContains(first, "\"id\":\"8185\"")
    }

    @Test
    fun `concurrent requests share one load that fetches overview and news once`() = teams {
        val release = CompletableDeferred<Unit>()
        transport.gate = release
        val responses = coroutineScope {
            val requests = List(8) { async(Dispatchers.Default) { client.get("/api/v1/teams/8185") } }
            withTimeout(5_000) { while (harness.counter(ResponseCacheCounter.JOINED) < 7L) yield() }
            release.complete(Unit)
            requests.awaitAll()
        }

        assertEquals(2, transport.calls)
        assertEquals(1, harness.counter(ResponseCacheCounter.MISS))
        assertEquals(7, harness.counter(ResponseCacheCounter.JOINED))
        assertTrue(responses.all { it.status == HttpStatusCode.OK })
        val first = responses.first().bodyAsBytes()
        responses.forEach { assertContentEquals(first, it.bodyAsBytes()) }
    }

    @Test
    fun `miss body equals the uncached route body`() {
        val uncached = uncachedBody({ configureTeamDetailRoutes(createTeamDetailService(transport)) }, "/api/v1/teams/8185")
        lateinit var cached: ByteArray
        teams { cached = client.get("/api/v1/teams/8185").bodyAsBytes() }
        assertContentEquals(uncached, cached)
    }

    @Test
    fun `invalid team requests never reach the cache or upstream`() = teams {
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
