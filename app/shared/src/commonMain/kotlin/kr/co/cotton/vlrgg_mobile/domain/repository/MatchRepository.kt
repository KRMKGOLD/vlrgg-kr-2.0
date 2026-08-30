package kr.co.cotton.vlrgg_mobile.domain.repository

import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchPage
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail

interface MatchRepository {

    suspend fun getUpcomingMatches(page: Int): AppResult<MatchPage>

    suspend fun getResults(page: Int): AppResult<MatchPage>

    suspend fun getMatchDetail(matchId: String): AppResult<MatchDetail>
}
