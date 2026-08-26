package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.remote.RemotePlayerDataSource
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.repository.PlayerRepository

@Inject
internal class PlayerRepositoryImpl(
    private val remotePlayerDataSource: RemotePlayerDataSource,
) : PlayerRepository {

    override suspend fun getPlayerDetail(playerId: String): AppResult<PlayerDetail> = wrapAsAppResult {
        remotePlayerDataSource.getPlayerDetail(playerId).toDomain()
    }
}
