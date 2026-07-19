package kr.co.cotton.vlrgg_mobile.feature.search

import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchScraperTest {
    @Test
    fun `scraper encodes the query in one fixed VLR search request and parses that response`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val scraper = VlrSearchScraper(
            upstreamHtmlTransport = object : UpstreamHtmlTransport {
                override suspend fun get(url: Url): String {
                    requestedUrls += url.toString()
                    return fixture("single-type-results.html")
                }
            },
            parser = SearchParser(),
        )

        val source = scraper.scrape("Sentinels KR")

        assertEquals(listOf("https://www.vlr.gg/search/?q=Sentinels+KR"), requestedUrls)
        assertEquals(listOf("9", "10"), source.results.map { it.id })
    }

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("fixtures/search/$name"),
    ).readText()
}
