package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kotlin.test.*

class VlrMatchesScraperTest {
    @Test
    fun `builds only canonical VLR paths from validated feature inputs`() = runBlockingTest {
        val transport = CapturingTransport()
        val scraper = VlrMatchesScraper(transport)

        scraper.fetchList(MatchListCategory.UPCOMING, page = 2)
        scraper.fetchList(MatchListCategory.RESULTS, page = 1)
        scraper.fetchDetail("709685")

        assertEquals(
            listOf("/matches", "/matches/results", "/709685"),
            transport.urls.map(Url::encodedPath),
        )
        assertEquals("2", transport.urls.first().parameters["page"])
        assertTrue(transport.urls.all { it.protocol == URLProtocol.HTTPS && it.host == "www.vlr.gg" })
        assertTrue(transport.urls.all { it.user.isNullOrEmpty() && it.password.isNullOrEmpty() })
    }

    private class CapturingTransport : UpstreamHtmlTransport {
        val urls = mutableListOf<Url>()

        override suspend fun get(url: Url): String {
            urls += url
            return "<html></html>"
        }
    }
}

private fun runBlockingTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
