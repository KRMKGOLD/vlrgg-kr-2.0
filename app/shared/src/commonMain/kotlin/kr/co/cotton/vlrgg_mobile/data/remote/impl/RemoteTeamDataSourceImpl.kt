package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteTeamDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamDetailResponseDto

@Inject
internal class RemoteTeamDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteTeamDataSource {

    override suspend fun getTeamDetail(teamId: String): TeamDetailResponseDto =
        httpClient.get("$TEAMS_PATH/$teamId").body()

    private companion object {
        const val TEAMS_PATH = "/api/v1/teams"
    }
}
