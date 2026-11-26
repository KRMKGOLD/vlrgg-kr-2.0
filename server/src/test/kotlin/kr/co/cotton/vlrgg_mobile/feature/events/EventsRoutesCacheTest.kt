package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
import kr.co.cotton.vlrgg_mobile.cache.uncachedBody
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchEventResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchSummaryResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchTeamResponse
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kotlin.test.*

class EventsRoutesCacheTest {
    private val harness = RouteCacheHarness()
    private var matchesHtml = fixture("event-matches.html")
    private val transport = CountingTransport { url ->
        when (url.toString()) {
            "https://www.vlr.gg/events" -> fixture("event-list.html")
            "https://www.vlr.gg/event/100" -> fixture("event-detail.html")
            "https://www.vlr.gg/event/matches/100/?series_id=all" -> matchesHtml
            "https://www.vlr.gg/event/news/100" -> fixture("event-news.html")
            "https://www.vlr.gg/event/stats/100" -> fixture("event-stats.html")
            else -> error(url.toString())
        }
    }

    @AfterTest
    fun close() = harness.close()

    private fun events(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configureEventsRoutes(createEventsService(transport), cache) }, block)

    @Test
    fun `list detail news and stats cache for five minutes`() = events {
        listOf("/api/v1/events", "/api/v1/events/100", "/api/v1/events/100/news", "/api/v1/events/100/stats").forEach {
            assertCachedRouteContract(harness, transport, it, 300)
        }
    }

    @Test
    fun `matches ttl is 300 seconds without a live match`() = events {
        assertTrue(matchesHtml.contains("<div class=\"ml-status\">Upcoming</div>"))
        assertCachedRouteContract(harness, transport, "/api/v1/events/100/matches", 300)
    }

    @Test
    fun `matches ttl is 30 seconds when any item is live`() = events {
        matchesHtml = matchesHtml.replace("<div class=\"ml-status\">Upcoming</div>", "<div class=\"ml-status\">Live</div>")
        assertCachedRouteContract(harness, transport, "/api/v1/events/100/matches", 30)
    }

    @Test
    fun `matches ttl follows the status of the response items`() {
        fun response(vararg statuses: MatchStatus) = EventMatchesResponse(items = statuses.mapIndexed { index, status -> item("m$index", status) })

        assertEquals(30, eventMatchesTtlSeconds(response(MatchStatus.COMPLETED, MatchStatus.LIVE)))
        assertEquals(300, eventMatchesTtlSeconds(response(MatchStatus.COMPLETED, MatchStatus.UPCOMING)))
        assertEquals(300, eventMatchesTtlSeconds(response()))
    }

    @Test
    fun `the same event id never collides across the five event routes`() = events {
        val paths = listOf("/api/v1/events/100", "/api/v1/events/100/matches", "/api/v1/events/100/news", "/api/v1/events/100/stats", "/api/v1/events")
        val bodies = paths.map { client.get(it).also { response -> assertEquals(HttpStatusCode.OK, response.status, it) }.bodyAsText() }

        assertEquals(paths.size, transport.calls)
        assertEquals(paths.size.toLong(), harness.counter(ResponseCacheCounter.MISS))
        assertEquals(0, harness.counter(ResponseCacheCounter.HIT))
        assertEquals(paths.size, bodies.toSet().size)
        assertContains(bodies[0], "Masters Seoul")
        assertFalse(bodies[0].contains("masters-recap"))
        // Every route now has its own hit, so none of them was stored under another route's key.
        paths.forEach { client.get(it) }
        assertEquals(paths.size, transport.calls)
        assertEquals(paths.size.toLong(), harness.counter(ResponseCacheCounter.HIT))
    }

    @Test
    fun `miss bodies equal the uncached route bodies`() {
        listOf("/api/v1/events", "/api/v1/events/100", "/api/v1/events/100/matches", "/api/v1/events/100/news", "/api/v1/events/100/stats")
            .forEach { path ->
                val uncached = uncachedBody({ configureEventsRoutes(createEventsService(transport)) }, path)
                lateinit var cached: ByteArray
                events { cached = client.get(path).bodyAsBytes() }
                assertContentEquals(uncached, cached, path)
            }
    }

    @Test
    fun `invalid event requests never reach the cache or upstream`() = events {
        assertInvalidRequestsSkipCacheAndUpstream(
            harness,
            transport,
            listOf(
                "/api/v1/events?filter=all",
                "/api/v1/events/0",
                "/api/v1/events/01",
                "/api/v1/events/abc",
                "/api/v1/events/100?debug=true",
                "/api/v1/events/100/matches?page=1",
                "/api/v1/events/100/news?debug=true",
                "/api/v1/events/100/stats?debug=true",
            ),
        )
        assertEquals(0, transport.calls)
    }

    private fun item(id: String, status: MatchStatus) = MatchSummaryResponse(
        id = id,
        status = status,
        timeLabel = "5:00 PM",
        homeTeam = MatchTeamResponse("Alpha"),
        awayTeam = MatchTeamResponse("Beta"),
        event = MatchEventResponse("Event"),
    )

    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader.getResource("events/$name")).readText()
}
