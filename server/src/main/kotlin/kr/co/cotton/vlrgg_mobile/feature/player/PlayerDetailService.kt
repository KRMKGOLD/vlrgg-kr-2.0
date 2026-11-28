package kr.co.cotton.vlrgg_mobile.feature.player

/** Coordinates one request-time scrape, parse, and response mapping on every call. Response caching is the route layer's job, and there is no stale fallback. */
internal class PlayerDetailService(
    private val scraper: PlayerDetailScraper,
    private val parser: PlayerDetailParser,
    private val mapper: PlayerDetailMapper,
) {
    suspend fun get(playerId: PlayerId): PlayerDetailResponse =
        mapper.map(playerId, parser.parse(scraper.scrape(playerId)))
}
