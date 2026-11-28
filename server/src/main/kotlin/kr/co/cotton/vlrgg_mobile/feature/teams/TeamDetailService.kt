package kr.co.cotton.vlrgg_mobile.feature.teams

/** Coordinates one request-time scrape, parse, and app-contract mapping on every call. Response caching is the route layer's job, and there is no stale fallback. */
internal class TeamDetailService(
    private val scraper: TeamDetailScraper,
    private val parser: TeamDetailParser,
    private val mapper: TeamDetailMapper,
) {
    suspend fun get(teamId: TeamId): TeamDetailResponse = mapper.map(teamId, parser.parse(scraper.scrape(teamId)))
}
