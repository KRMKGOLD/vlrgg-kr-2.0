package kr.co.cotton.vlrgg_mobile.feature.series

/** Coordinates one fresh upstream request, parsing, and mapping on every call. Response caching is the route layer's job, and there is no stale fallback. */
internal class SeriesService(
    private val scraper: SeriesScraper,
    private val parser: SeriesParser,
    private val mapper: SeriesMapper,
) {
    suspend fun get(seriesId: SeriesId): SeriesResponse {
        val html = scraper.fetch(seriesId)
        return mapper.map(parser.parse(html, seriesId))
    }
}
