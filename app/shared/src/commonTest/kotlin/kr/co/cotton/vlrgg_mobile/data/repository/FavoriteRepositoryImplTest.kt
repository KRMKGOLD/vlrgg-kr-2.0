package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.local.FavoriteLocalDataSource
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoritePlayerStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoriteTeamStorage
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class FavoriteRepositoryImplTest {

    @Test
    fun queriesAndMutationsExposeOnlyDomainAppResults() = runTest {
        val source = FakeFavoriteLocalDataSource()
        val repository = FavoriteRepositoryImpl(source)
        val team = FavoriteTeam("2", "DRX", "DRX", "Korea", "https://cdn.example.com/drx.png")
        val player = FavoritePlayer("100", "stax", null, "KR", "Korea", "https://cdn.example.com/stax.png")

        assertEquals(AppResult.Success(Unit), repository.addFavoriteTeam(team))
        assertEquals(AppResult.Success(Unit), repository.addFavoritePlayer(player))
        assertEquals(AppResult.Success(listOf(team)), repository.getFavoriteTeams())
        assertEquals(AppResult.Success(listOf(player)), repository.getFavoritePlayers())
        assertEquals(AppResult.Success(listOf(team)), repository.observeFavoriteTeams().first())
        assertEquals(AppResult.Success(listOf(player)), repository.observeFavoritePlayers().first())
    }

    @Test
    fun nonCancellationReadAndWriteFailureBecomeAppFailure() = runTest {
        val source = FakeFavoriteLocalDataSource(readFailure = IllegalStateException("broken"))
        val repository = FavoriteRepositoryImpl(source)

        assertSame(AppResult.Failure, repository.getFavoriteTeams())
        assertSame(AppResult.Failure, repository.observeFavoriteTeams().first())

        source.readFailure = null
        source.writeFailure = IllegalStateException("broken")
        assertSame(
            AppResult.Failure,
            repository.addFavoriteTeam(FavoriteTeam("2", "DRX", null, null)),
        )
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val source = FakeFavoriteLocalDataSource(readFailure = cancellation)
        val repository = FavoriteRepositoryImpl(source)

        assertSame(cancellation, assertFailsWith { repository.getFavoriteTeams() })
        assertSame(cancellation, assertFailsWith { repository.observeFavoriteTeams().first() })
    }
}

private class FakeFavoriteLocalDataSource(
    var readFailure: Throwable? = null,
    var writeFailure: Throwable? = null,
) : FavoriteLocalDataSource {
    private val teams = MutableStateFlow<List<FavoriteTeamStorage>>(emptyList())
    private val players = MutableStateFlow<List<FavoritePlayerStorage>>(emptyList())

    override fun observeFavoriteTeams(): Flow<List<FavoriteTeamStorage>> = failingFlow(teams)

    override fun observeFavoritePlayers(): Flow<List<FavoritePlayerStorage>> = failingFlow(players)

    override suspend fun getFavoriteTeams(): List<FavoriteTeamStorage> {
        throwIfReadFails()
        return teams.value
    }

    override suspend fun getFavoritePlayers(): List<FavoritePlayerStorage> {
        throwIfReadFails()
        return players.value
    }

    override suspend fun upsertFavoriteTeam(favorite: FavoriteTeamStorage) {
        throwIfWriteFails()
        teams.value = teams.value.replaceAtExistingIdOrAppend(favorite) { it.id }
    }

    override suspend fun upsertFavoritePlayer(favorite: FavoritePlayerStorage) {
        throwIfWriteFails()
        players.value = players.value.replaceAtExistingIdOrAppend(favorite) { it.id }
    }

    override suspend fun removeFavoriteTeam(teamId: String) {
        throwIfWriteFails()
        teams.value = teams.value.filterNot { it.id == teamId }
    }

    override suspend fun removeFavoritePlayer(playerId: String) {
        throwIfWriteFails()
        players.value = players.value.filterNot { it.id == playerId }
    }

    private fun <T> failingFlow(flow: Flow<T>): Flow<T> = kotlinx.coroutines.flow.flow {
        throwIfReadFails()
        emit(flow.first())
    }

    private fun throwIfReadFails() {
        readFailure?.let { throw it }
    }

    private fun throwIfWriteFails() {
        writeFailure?.let { throw it }
    }
}

private fun <T> List<T>.replaceAtExistingIdOrAppend(
    value: T,
    id: (T) -> String,
): List<T> {
    val index = indexOfFirst { id(it) == id(value) }
    return if (index < 0) this + value else toMutableList().also { it[index] = value }
}
