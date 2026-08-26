package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kr.co.cotton.vlrgg_mobile.data.remote.RemotePlayerDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerDetailResponseDto

@Inject
internal class RemotePlayerDataSourceImpl(
    private val httpClient: HttpClient,
) : RemotePlayerDataSource {

    override suspend fun getPlayerDetail(playerId: String): PlayerDetailResponseDto =
        httpClient.get("$PLAYERS_PATH/$playerId").body()

    private companion object {
        const val PLAYERS_PATH = "/api/v1/players"
    }
}
