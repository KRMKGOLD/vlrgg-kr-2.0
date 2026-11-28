package kr.co.cotton.vlrgg_mobile.feature.player

/** Coordinates one request-time scrape, parse, and response mapping without a cache or stale fallback. */
internal class PlayerDetailService(
    private val scraper: PlayerDetailScraper,
    private val parser: PlayerDetailParser,
    private val mapper: PlayerDetailMapper,
) {
    suspend fun get(playerId: PlayerId): PlayerDetailResponse =
        mapper.map(playerId, parser.parse(scraper.scrape(playerId)))
}
