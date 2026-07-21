package kr.co.cotton.vlrgg_mobile.feature.series

/** Coordinates one fresh upstream request, parsing, and mapping without a cache or stale fallback. */
internal class SeriesService(
    private val scraper: SeriesScraper,
    private val parser: SeriesParser,
    private val mapper: SeriesMapper,
) {
    suspend fun get(seriesId: SeriesId): SeriesResponse {
        val page = scraper.fetch(seriesId)
        return mapper.map(parser.parse(page, seriesId))
    }
}
