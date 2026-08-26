package kr.co.cotton.vlrgg_mobile.domain.repository

import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail

interface PlayerRepository {

    suspend fun getPlayerDetail(playerId: String): AppResult<PlayerDetail>
}
