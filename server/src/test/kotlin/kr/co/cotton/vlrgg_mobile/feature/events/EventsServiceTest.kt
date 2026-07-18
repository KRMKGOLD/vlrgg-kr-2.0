package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kotlin.test.*

class EventsServiceTest {
    @Test
    fun `service delegates each section to its own fresh upstream resource`() = runBlocking {
        val scraper = RecordingEventsScraper()
        val service = DefaultEventsService(scraper, EventsParser(), EventsMapper())

        val firstList = service.getEventList()
        val secondList = service.getEventList()
        assertEquals(4, firstList.ongoing.size + firstList.upcoming.size + firstList.completedOrPaused.size)
        assertEquals(4, secondList.ongoing.size + secondList.upcoming.size + secondList.completedOrPaused.size)
        assertEquals("100", service.getEventDetail("100").id)
        assertEquals(2, service.getEventMatches("100").items.size)
        assertEquals(1, service.getEventNews("100").items.size)
        assertEquals(EventStatsAvailability.AVAILABLE, service.getEventStats("100").availability)

        assertEquals(2, scraper.calls.count { it == "list" })
        assertEquals(listOf("detail:100", "matches:100", "news:100", "stats:100"), scraper.calls.drop(2))
    }

    @Test
    fun `service never returns a prior success after a later upstream failure`() = runBlocking {
        val firstPage = page("event-list.html", "/events")
        var requestCount = 0
        val scraper = object : RecordingEventsScraper() {
            override suspend fun fetchEventList(): EventHtmlPage {
                requestCount += 1
                if (requestCount == 1) return firstPage
                throw UpstreamNetworkFailure(Url("https://www.vlr.gg/events"))
            }
        }
        val service = DefaultEventsService(scraper, EventsParser(), EventsMapper())

        assertTrue(service.getEventList().ongoing.isNotEmpty())
        assertFailsWith<UpstreamNetworkFailure> { service.getEventList() }
        Unit
    }

    private open inner class RecordingEventsScraper : EventsScraper {
        val calls = mutableListOf<String>()

        override suspend fun fetchEventList(): EventHtmlPage {
            calls += "list"
            return page("event-list.html", "/events")
        }

        override suspend fun fetchEventDetail(eventId: String): EventHtmlPage {
            calls += "detail:$eventId"
            return page("event-detail.html", "/event/$eventId")
        }

        override suspend fun fetchEventMatches(eventId: String): EventHtmlPage {
            calls += "matches:$eventId"
            return page("event-matches.html", "/event/matches/$eventId/?series_id=all")
        }

        override suspend fun fetchEventNews(eventId: String): EventHtmlPage {
            calls += "news:$eventId"
            return page("event-news.html", "/event/news/$eventId")
        }

        override suspend fun fetchEventStats(eventId: String): EventHtmlPage {
            calls += "stats:$eventId"
            return page("event-stats.html", "/event/stats/$eventId")
        }
    }

    private fun page(name: String, path: String) = EventHtmlPage(
        upstreamUrl = Url("https://www.vlr.gg$path"),
        html = fixture(name),
    )

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("events/$name"),
    ).readText()
}
