package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kotlin.test.*

class EventsScraperTest {
    @Test
    fun `scraper builds only fixed event upstream resources from validated identifiers`() = runBlocking {
        val requested = mutableListOf<String>()
        val scraper = VlrEventsScraper(
            object : UpstreamHtmlTransport {
                override suspend fun get(url: Url): String {
                    requested += url.toString()
                    return "html"
                }
            },
        )

        scraper.fetchEventList()
        scraper.fetchEventDetail("100")
        scraper.fetchEventMatches("100")
        scraper.fetchEventNews("100")
        scraper.fetchEventStats("100")

        assertEquals(
            listOf(
                "https://www.vlr.gg/events",
                "https://www.vlr.gg/event/100",
                "https://www.vlr.gg/event/matches/100/?series_id=all",
                "https://www.vlr.gg/event/news/100",
                "https://www.vlr.gg/event/stats/100",
            ),
            requested,
        )
    }
}
