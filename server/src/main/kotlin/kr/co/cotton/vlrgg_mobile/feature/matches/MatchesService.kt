package kr.co.cotton.vlrgg_mobile.feature.matches

internal interface MatchesService {
    suspend fun getMatches(category: MatchListCategory, page: Int): MatchesPageResponse

    suspend fun getMatch(matchId: String): MatchDetailResponse
}

internal class DefaultMatchesService(
    private val scraper: MatchesScraper,
    private val parser: VlrMatchesParser,
    private val mapper: MatchesMapper,
) : MatchesService {
    override suspend fun getMatches(category: MatchListCategory, page: Int): MatchesPageResponse {
        val scraped = scraper.fetchList(category, page)
        return mapper.toPageResponse(category, page, parser.parseList(scraped.html, scraped.upstreamUrl))
    }

    override suspend fun getMatch(matchId: String): MatchDetailResponse {
        val scraped = scraper.fetchDetail(matchId)
        return mapper.toDetailResponse(parser.parseDetail(scraped.html, scraped.upstreamUrl, matchId))
    }
}
