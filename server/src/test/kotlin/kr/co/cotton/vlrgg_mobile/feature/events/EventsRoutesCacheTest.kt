package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheRoute
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
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

    private fun events(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configureEventsRoutes(createEventsService(transport), cache) }, block)

    private fun key(route: ResponseCacheRoute, vararg identifier: String) = harness.key(route, *identifier)

    @Test
    fun `list detail news and stats use their own key and the five minute ttl`() = events {
        assertCachedRouteContract(harness, transport, "/api/v1/events", key(ResponseCacheRoute.EVENTS_LIST, "all"), 300)
        assertCachedRouteContract(harness, transport, "/api/v1/events/100", key(ResponseCacheRoute.EVENT_DETAIL, "100"), 300)
        assertCachedRouteContract(harness, transport, "/api/v1/events/100/news", key(ResponseCacheRoute.EVENT_NEWS, "100"), 300)
        assertCachedRouteContract(harness, transport, "/api/v1/events/100/stats", key(ResponseCacheRoute.EVENT_STATS, "100"), 300)
    }

    @Test
    fun `matches ttl is 300 seconds without a live match`() = events {
        assertCachedRouteContract(harness, transport, "/api/v1/events/100/matches", key(ResponseCacheRoute.EVENT_MATCHES, "100"), 300)
    }

    @Test
    fun `matches ttl is 30 seconds when any item is live`() = events {
        matchesHtml = matchesHtml.replace("<div class=\"ml-status\">Upcoming</div>", "<div class=\"ml-status\">Live</div>")
        assertCachedRouteContract(harness, transport, "/api/v1/events/100/matches", key(ResponseCacheRoute.EVENT_MATCHES, "100"), 30)
    }

    @Test
    fun `the same event id never collides across the five event routes`() = events {
        listOf("", "/matches", "/news", "/stats").forEach { suffix ->
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/events/100$suffix").status)
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/events").status)

        val keys = harness.store.sets.map { it.key }
        assertEquals(5, keys.size)
        assertEquals(5, keys.toSet().size)
        assertEquals(
            setOf("event-detail:100", "event-matches:100", "event-news:100", "event-stats:100", "events-list:all"),
            keys.map { it.removePrefix("test-sha:") }.toSet(),
        )
    }

    @Test
    fun `a stored event news body is never served for the event detail request`() = events {
        client.get("/api/v1/events/100/news")
        val detailBody = client.get("/api/v1/events/100").bodyAsText()

        assertContains(detailBody, "Masters Seoul")
        assertFalse(detailBody.contains("masters-recap"))
    }

    @Test
    fun `invalid event requests never reach the store or upstream`() = events {
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

    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader.getResource("events/$name")).readText()
}
