package kr.co.cotton.vlrgg_mobile.data.remote

import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamDetailResponseDto

internal interface RemoteTeamDataSource {

    suspend fun getTeamDetail(teamId: String): TeamDetailResponseDto
}
