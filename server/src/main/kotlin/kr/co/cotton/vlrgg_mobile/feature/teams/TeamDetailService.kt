package kr.co.cotton.vlrgg_mobile.feature.teams

/** Coordinates one request-time scrape, parse, and app-contract mapping with no stale fallback. */
internal class TeamDetailService(
    private val scraper: TeamDetailScraper,
    private val parser: TeamDetailParser,
    private val mapper: TeamDetailMapper,
) {
    suspend fun get(teamId: TeamId): TeamDetailResponse = mapper.map(teamId, parser.parse(scraper.scrape(teamId)))
}
