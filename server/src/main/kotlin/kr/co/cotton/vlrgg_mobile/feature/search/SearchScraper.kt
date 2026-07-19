package kr.co.cotton.vlrgg_mobile.feature.search

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

internal fun interface SearchScraper {
    suspend fun scrape(query: String): SearchSourceModel
}

/** Fetches one canonical upstream search page and immediately hands its HTML to the parser. */
internal class VlrSearchScraper(
    private val upstreamHtmlTransport: UpstreamHtmlTransport,
    private val parser: SearchParser,
) : SearchScraper {
    override suspend fun scrape(query: String): SearchSourceModel {
        val upstreamUrl = URLBuilder().apply {
            protocol = URLProtocol.HTTPS
            host = "www.vlr.gg"
            encodedPath = "/search/"
            parameters.append("q", query)
        }.build()

        return parser.parse(upstreamHtmlTransport.get(upstreamUrl), upstreamUrl)
    }
}
