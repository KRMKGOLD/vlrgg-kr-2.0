package kr.co.cotton.vlrgg_mobile.feature.events

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
        val firstPage = fixture("event-list.html")
        var requestCount = 0
        val scraper = object : RecordingEventsScraper() {
            override suspend fun fetchEventList(): String {
                requestCount += 1
                if (requestCount == 1) return firstPage
                throw UpstreamNetworkFailure()
            }
        }
        val service = DefaultEventsService(scraper, EventsParser(), EventsMapper())

        assertTrue(service.getEventList().ongoing.isNotEmpty())
        val failure = try {
            service.getEventList()
            null
        } catch (failure: UpstreamNetworkFailure) {
            failure
        }
        assertNotNull(failure)
        Unit
    }

    private open inner class RecordingEventsScraper : EventsScraper {
        val calls = mutableListOf<String>()

        override suspend fun fetchEventList(): String {
            calls += "list"
            return fixture("event-list.html")
        }

        override suspend fun fetchEventDetail(eventId: String): String {
            calls += "detail:$eventId"
            return fixture("event-detail.html")
        }

        override suspend fun fetchEventMatches(eventId: String): String {
            calls += "matches:$eventId"
            return fixture("event-matches.html")
        }

        override suspend fun fetchEventNews(eventId: String): String {
            calls += "news:$eventId"
            return fixture("event-news.html")
        }

        override suspend fun fetchEventStats(eventId: String): String {
            calls += "stats:$eventId"
            return fixture("event-stats.html")
        }
    }

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("events/$name"),
    ).readText()
}
