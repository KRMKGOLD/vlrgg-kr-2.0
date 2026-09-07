package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemotePlayerDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerDetailResponseDto
import kr.co.cotton.vlrgg_mobile.network.getPublicJson

@Inject
internal class RemotePlayerDataSourceImpl(
    private val httpClient: HttpClient,
) : RemotePlayerDataSource {

    override suspend fun getPlayerDetail(playerId: String): PlayerDetailResponseDto =
        httpClient.getPublicJson("$PLAYERS_PATH/$playerId")

    private companion object {
        const val PLAYERS_PATH = "/api/v1/players"
    }
}
