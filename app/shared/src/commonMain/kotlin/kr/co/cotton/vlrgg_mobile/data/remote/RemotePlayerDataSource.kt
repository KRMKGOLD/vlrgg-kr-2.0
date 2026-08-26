package kr.co.cotton.vlrgg_mobile.data.remote

import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerDetailResponseDto

internal interface RemotePlayerDataSource {

    suspend fun getPlayerDetail(playerId: String): PlayerDetailResponseDto
}
