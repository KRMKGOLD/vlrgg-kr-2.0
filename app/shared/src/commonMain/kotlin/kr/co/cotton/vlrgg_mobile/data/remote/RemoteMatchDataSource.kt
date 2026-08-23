package kr.co.cotton.vlrgg_mobile.data.remote

import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchesPageResponseDto

internal interface RemoteMatchDataSource {

    suspend fun getUpcomingMatches(page: Int): MatchesPageResponseDto

    suspend fun getResults(page: Int): MatchesPageResponseDto
}
