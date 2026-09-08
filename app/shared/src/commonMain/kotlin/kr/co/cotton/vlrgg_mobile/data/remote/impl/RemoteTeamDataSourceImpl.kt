package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteTeamDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamDetailResponseDto
import kr.co.cotton.vlrgg_mobile.network.getPublicJson

@Inject
internal class RemoteTeamDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteTeamDataSource {

    override suspend fun getTeamDetail(teamId: String): TeamDetailResponseDto =
        httpClient.getPublicJson("$TEAMS_PATH/$teamId")

    private companion object {
        const val TEAMS_PATH = "/api/v1/teams"
    }
}
