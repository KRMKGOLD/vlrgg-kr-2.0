package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kr.co.cotton.vlrgg_mobile.data.local.FavoriteLocalDataSource
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.mapper.toStorage
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository

@Inject
internal class FavoriteRepositoryImpl(
    private val localDataSource: FavoriteLocalDataSource,
) : FavoriteRepository {

    override fun observeFavoriteTeams(): Flow<AppResult<List<FavoriteTeam>>> =
        localDataSource.observeFavoriteTeams()
            .map { it.map { favorite -> favorite.toDomain() } }
            .asAppResultFlow()

    override fun observeFavoritePlayers(): Flow<AppResult<List<FavoritePlayer>>> =
        localDataSource.observeFavoritePlayers()
            .map { it.map { favorite -> favorite.toDomain() } }
            .asAppResultFlow()

    override suspend fun getFavoriteTeams(): AppResult<List<FavoriteTeam>> = wrapAsAppResult {
        localDataSource.getFavoriteTeams().map { it.toDomain() }
    }

    override suspend fun getFavoritePlayers(): AppResult<List<FavoritePlayer>> = wrapAsAppResult {
        localDataSource.getFavoritePlayers().map { it.toDomain() }
    }

    override suspend fun addFavoriteTeam(favorite: FavoriteTeam): AppResult<Unit> = wrapAsAppResult {
        localDataSource.upsertFavoriteTeam(favorite.toStorage())
    }

    override suspend fun addFavoritePlayer(favorite: FavoritePlayer): AppResult<Unit> = wrapAsAppResult {
        localDataSource.upsertFavoritePlayer(favorite.toStorage())
    }

    override suspend fun removeFavoriteTeam(teamId: String): AppResult<Unit> = wrapAsAppResult {
        localDataSource.removeFavoriteTeam(teamId)
    }

    override suspend fun removeFavoritePlayer(playerId: String): AppResult<Unit> = wrapAsAppResult {
        localDataSource.removeFavoritePlayer(playerId)
    }
}

private fun <T> Flow<T>.asAppResultFlow(): Flow<AppResult<T>> =
    map<T, AppResult<T>> { AppResult.Success(it) }
        .catch { exception ->
            if (exception is CancellationException) throw exception
            emit(AppResult.Failure)
        }
