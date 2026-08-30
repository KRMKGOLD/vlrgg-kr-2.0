package kr.co.cotton.vlrgg_mobile.data.local

import kotlinx.coroutines.flow.Flow
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoritePlayerStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoriteTeamStorage

internal interface FavoriteLocalDataSource {

    fun observeFavoriteTeams(): Flow<List<FavoriteTeamStorage>>

    fun observeFavoritePlayers(): Flow<List<FavoritePlayerStorage>>

    suspend fun getFavoriteTeams(): List<FavoriteTeamStorage>

    suspend fun getFavoritePlayers(): List<FavoritePlayerStorage>

    suspend fun upsertFavoriteTeam(favorite: FavoriteTeamStorage)

    suspend fun upsertFavoritePlayer(favorite: FavoritePlayerStorage)

    suspend fun removeFavoriteTeam(teamId: String)

    suspend fun removeFavoritePlayer(playerId: String)
}
