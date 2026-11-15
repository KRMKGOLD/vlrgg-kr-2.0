package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Fetches exactly one current canonical Series page and retains no previous response. */
internal class SeriesScraper(
    private val transport: UpstreamHtmlTransport,
) {
    suspend fun fetch(seriesId: SeriesId): String =
        transport.get(Url("https://www.vlr.gg/series/${seriesId.value}"))
}
