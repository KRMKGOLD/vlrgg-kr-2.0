package kr.co.cotton.vlrgg_mobile.feature.search

import kotlin.test.*
import kotlinx.coroutines.runBlocking

class SearchServiceTest {

    @Test
    fun `service requests the source for every search and does not retain stale results`() = runBlocking {
        val scraper = RecordingSearchScraper()
        val service = SearchService(scraper, SearchMapper())

        val first = service.search("Sentinels")
        val second = service.search("Sentinels")

        assertEquals(2, scraper.requestedQueries.size)
        assertEquals(listOf("Sentinels", "Sentinels"), scraper.requestedQueries)
        assertEquals(first, second)
    }

    private class RecordingSearchScraper : SearchScraper {
        val requestedQueries = mutableListOf<String>()

        override suspend fun scrape(query: String): SearchSourceModel {
            requestedQueries += query
            return SearchSourceModel(
                listOf(SearchSourceResult(SearchSourceResultType.TEAM, "2", "Sentinels", null)),
            )
        }
    }
}
