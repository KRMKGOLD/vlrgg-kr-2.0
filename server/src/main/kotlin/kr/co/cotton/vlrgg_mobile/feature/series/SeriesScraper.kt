package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

internal fun interface SeriesScraper {
    suspend fun fetch(seriesId: SeriesId): SeriesHtmlPage
}

/** Fetches exactly one current canonical Series page and retains no previous response. */
internal class VlrSeriesScraper(
    private val transport: UpstreamHtmlTransport,
) : SeriesScraper {
    override suspend fun fetch(seriesId: SeriesId): SeriesHtmlPage {
        val upstreamUrl = Url("https://www.vlr.gg/series/${seriesId.value}")
        return SeriesHtmlPage(upstreamUrl = upstreamUrl, html = transport.get(upstreamUrl))
    }
}
