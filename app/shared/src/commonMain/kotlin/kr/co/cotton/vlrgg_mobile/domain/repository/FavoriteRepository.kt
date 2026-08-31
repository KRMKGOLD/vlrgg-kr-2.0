package kr.co.cotton.vlrgg_mobile.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam

interface FavoriteRepository {

    fun observeFavoriteTeams(): Flow<AppResult<List<FavoriteTeam>>>

    fun observeFavoritePlayers(): Flow<AppResult<List<FavoritePlayer>>>

    suspend fun getFavoriteTeams(): AppResult<List<FavoriteTeam>>

    suspend fun getFavoritePlayers(): AppResult<List<FavoritePlayer>>

    suspend fun addFavoriteTeam(favorite: FavoriteTeam): AppResult<Unit>

    suspend fun addFavoritePlayer(favorite: FavoritePlayer): AppResult<Unit>

    suspend fun removeFavoriteTeam(teamId: String): AppResult<Unit>

    suspend fun removeFavoritePlayer(playerId: String): AppResult<Unit>
}
