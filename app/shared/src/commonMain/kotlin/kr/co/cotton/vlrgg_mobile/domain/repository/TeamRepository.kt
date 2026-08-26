package kr.co.cotton.vlrgg_mobile.domain.repository

import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail

interface TeamRepository {

    suspend fun getTeamDetail(teamId: String): AppResult<TeamDetail>
}
