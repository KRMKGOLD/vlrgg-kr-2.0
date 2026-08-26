package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteTeamDataSource
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.repository.TeamRepository

@Inject
internal class TeamRepositoryImpl(
    private val remoteTeamDataSource: RemoteTeamDataSource,
) : TeamRepository {

    override suspend fun getTeamDetail(teamId: String): AppResult<TeamDetail> = wrapAsAppResult {
        remoteTeamDataSource.getTeamDetail(teamId).toDomain()
    }
}
