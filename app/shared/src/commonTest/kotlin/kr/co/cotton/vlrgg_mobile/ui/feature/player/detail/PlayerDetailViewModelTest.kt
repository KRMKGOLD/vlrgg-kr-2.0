package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerProfile
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.PlayerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailViewModelTest {
    @Test
    fun initialStateIsLoadingAndRequestsPlayerIdentityExactlyOnce() = runViewModelTest {
        val repository = FakePlayerRepository(listOf(AppResult.Success(playerDetail())))
        val viewModel = PlayerDetailViewModel(repository, FakeFavoriteRepository(), PLAYER_ID)

        assertEquals(PlayerDetailUiState(), viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(listOf(PLAYER_ID), repository.requestedPlayerIds)
    }

    @Test
    fun successPreservesTheWholePlayerDetailAsContent() = runViewModelTest {
        val player = playerDetail()
        val viewModel = PlayerDetailViewModel(FakePlayerRepository(listOf(AppResult.Success(player))), FakeFavoriteRepository(), PLAYER_ID)

        advanceUntilIdle()

        assertEquals(
            PlayerDetailUiState(
                PlayerDetailContentState.Content(player),
                PlayerFavoriteUiState(isRestored = true),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun nullableAndEmptySectionsRemainContentInsteadOfOverallError() = runViewModelTest {
        val player = playerDetail(
            currentTeam = null,
            agentStats = emptyList(),
            recentMatches = emptyList(),
        )
        val viewModel = PlayerDetailViewModel(FakePlayerRepository(listOf(AppResult.Success(player))), FakeFavoriteRepository(), PLAYER_ID)

        advanceUntilIdle()

        assertEquals(
            PlayerDetailUiState(
                PlayerDetailContentState.Content(player),
                PlayerFavoriteUiState(isRestored = true),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun failureBecomesOverallErrorWithoutRawFailureDetails() = runViewModelTest {
        val viewModel = PlayerDetailViewModel(FakePlayerRepository(listOf(AppResult.Failure)), FakeFavoriteRepository(), PLAYER_ID)

        advanceUntilIdle()

        assertEquals(
            PlayerDetailUiState(
                PlayerDetailContentState.Error,
                PlayerFavoriteUiState(isRestored = true),
            ),
            viewModel.uiState.value,
        )
        assertFalse(viewModel.uiState.value.toString().contains("exception", ignoreCase = true))
        assertFalse(viewModel.uiState.value.toString().contains("http", ignoreCase = true))
    }

    @Test
    fun retryUsesSameIdentityAndOnlyStartsFromError() = runViewModelTest {
        val repository = FakePlayerRepository(listOf(AppResult.Failure, AppResult.Success(playerDetail())))
        val viewModel = PlayerDetailViewModel(repository, FakeFavoriteRepository(), PLAYER_ID)
        advanceUntilIdle()

        viewModel.retry()
        assertEquals(
            PlayerDetailUiState(favorite = PlayerFavoriteUiState(isRestored = true)),
            viewModel.uiState.value,
        )
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(PLAYER_ID, PLAYER_ID), repository.requestedPlayerIds)
    }

    @Test
    fun retryWhileLoadingOrAfterContentDoesNotStartADuplicateRequest() = runViewModelTest {
        val repository = FakePlayerRepository(listOf(AppResult.Success(playerDetail())))
        val viewModel = PlayerDetailViewModel(repository, FakeFavoriteRepository(), PLAYER_ID)

        viewModel.retry()
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(PLAYER_ID), repository.requestedPlayerIds)
        assertEquals(PlayerDetailContentState.Content(playerDetail()), viewModel.uiState.value.contentState)
    }

    @Test
    fun restoresFavoriteFromRepositoryWithoutChangingLoadedContent() = runViewModelTest {
        val player = playerDetail()
        val favoriteRepository = FakeFavoriteRepository(existing = listOf(player.favoriteSnapshot()))
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favoriteRepository,
            PLAYER_ID,
        )

        advanceUntilIdle()

        assertEquals(PlayerDetailContentState.Content(player), viewModel.uiState.value.contentState)
        assertTrue(viewModel.uiState.value.favorite.isFavorite)
        assertTrue(viewModel.uiState.value.favorite.isRestored)
        assertEquals(1, favoriteRepository.restoreCallCount)
    }

    @Test
    fun favoriteRestoreFailureKeepsTheFavoriteUnrestoredAndPreventsMutation() = runViewModelTest {
        val player = playerDetail()
        val favorites = FakeFavoriteRepository(favoritePlayersResult = AppResult.Failure)
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )

        advanceUntilIdle()

        assertEquals(PlayerDetailContentState.Content(player), viewModel.uiState.value.contentState)
        assertEquals(PlayerFavoriteUiState(), viewModel.uiState.value.favorite)

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(favorites.added.isEmpty())
        assertTrue(favorites.removed.isEmpty())
    }

    @Test
    fun addIsOptimisticThenRollsBackWithSafeRetryIntentOnFailure() = runViewModelTest {
        val player = playerDetail()
        val favorites = FakeFavoriteRepository(addResults = listOf(AppResult.Failure))
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )
        advanceUntilIdle()

        viewModel.toggleFavorite()
        assertTrue(viewModel.uiState.value.favorite.isFavorite)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.favorite.isFavorite)
        assertEquals(PlayerFavoriteMutationIntent.Add, viewModel.uiState.value.favorite.failedIntent)
        assertEquals(listOf(player.favoriteSnapshot()), favorites.added)
        assertEquals(PlayerDetailContentState.Content(player), viewModel.uiState.value.contentState)
    }

    @Test
    fun removeIsOptimisticThenRestoresOnFailure() = runViewModelTest {
        val player = playerDetail()
        val favorites = FakeFavoriteRepository(existing = listOf(player.favoriteSnapshot()), removeResults = listOf(AppResult.Failure))
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )
        advanceUntilIdle()

        viewModel.toggleFavorite()
        assertFalse(viewModel.uiState.value.favorite.isFavorite)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.favorite.isFavorite)
        assertEquals(PlayerFavoriteMutationIntent.Remove, viewModel.uiState.value.favorite.failedIntent)
        assertEquals(listOf(PLAYER_ID), favorites.removed)
    }

    @Test
    fun retryReusesSamePlayerSnapshotExactlyOnceAndDismissClearsIntent() = runViewModelTest {
        val player = playerDetail()
        val favorites = FakeFavoriteRepository(addResults = listOf(AppResult.Failure, AppResult.Success(Unit)))
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()
        viewModel.retryFavoriteMutation()
        viewModel.retryFavoriteMutation()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.favorite.isFavorite)
        assertEquals(null, viewModel.uiState.value.favorite.failedIntent)
        assertEquals(listOf(player.favoriteSnapshot(), player.favoriteSnapshot()), favorites.added)

        viewModel.dismissFavoriteError()
        assertEquals(null, viewModel.uiState.value.favorite.failedIntent)
    }

    @Test
    fun noFavoriteMutationRunsWithoutLoadedContentOrWhileAnotherMutationIsRunning() = runViewModelTest {
        val favorites = FakeFavoriteRepository()
        val loadingViewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(playerDetail()))),
            favorites,
            PLAYER_ID,
        )
        loadingViewModel.toggleFavorite()
        advanceUntilIdle()
        assertEquals(emptyList(), favorites.added)

        val player = playerDetail()
        val delayedFavorites = FakeFavoriteRepository(addResults = listOf(AppResult.Success(Unit)))
        val contentViewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            delayedFavorites,
            PLAYER_ID,
        )
        advanceUntilIdle()
        contentViewModel.toggleFavorite()
        contentViewModel.toggleFavorite()
        advanceUntilIdle()

        assertEquals(listOf(player.favoriteSnapshot()), delayedFavorites.added)
    }

    @Test
    fun recreatedViewModelRestoresPersistedFavoriteForSamePlayerId() = runViewModelTest {
        val player = playerDetail()
        val favorites = FakeFavoriteRepository(existing = listOf(player.favoriteSnapshot()))

        val recreatedViewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )
        advanceUntilIdle()

        assertTrue(recreatedViewModel.uiState.value.favorite.isFavorite)
        assertEquals(1, favorites.restoreCallCount)
    }

    private fun runViewModelTest(testBody: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun playerDetail(
        currentTeam: kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerCurrentTeam? = null,
        agentStats: List<kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat> = emptyList(),
        recentMatches: List<kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch> = emptyList(),
    ) = PlayerDetail(
        id = PLAYER_ID,
        profile = PlayerProfile("stax", null, emptyList(), null, null),
        currentTeam = currentTeam,
        agentStats = agentStats,
        recentMatches = recentMatches,
    )

    private class FakePlayerRepository(
        private val results: List<AppResult<PlayerDetail>>,
    ) : PlayerRepository {
        val requestedPlayerIds = mutableListOf<String>()

        override suspend fun getPlayerDetail(playerId: String): AppResult<PlayerDetail> {
            requestedPlayerIds += playerId
            return results[requestedPlayerIds.lastIndex]
        }
    }

    private class FakeFavoriteRepository(
        private val existing: List<FavoritePlayer> = emptyList(),
        private val favoritePlayersResult: AppResult<List<FavoritePlayer>> = AppResult.Success(existing),
        private val addResults: List<AppResult<Unit>> = emptyList(),
        private val removeResults: List<AppResult<Unit>> = emptyList(),
    ) : FavoriteRepository {
        var restoreCallCount = 0
        val added = mutableListOf<FavoritePlayer>()
        val removed = mutableListOf<String>()

        override fun observeFavoriteTeams() = error("unused")

        override fun observeFavoritePlayers() = error("unused")

        override suspend fun getFavoriteTeams() = error("unused")

        override suspend fun getFavoritePlayers(): AppResult<List<FavoritePlayer>> {
            restoreCallCount += 1
            return favoritePlayersResult
        }

        override suspend fun addFavoriteTeam(favorite: kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam) = error("unused")

        override suspend fun addFavoritePlayer(favorite: FavoritePlayer): AppResult<Unit> {
            added += favorite
            return addResults.getOrElse(added.lastIndex) { AppResult.Success(Unit) }
        }

        override suspend fun removeFavoriteTeam(teamId: String) = error("unused")

        override suspend fun removeFavoritePlayer(playerId: String): AppResult<Unit> {
            removed += playerId
            return removeResults.getOrElse(removed.lastIndex) { AppResult.Success(Unit) }
        }
    }

    private fun PlayerDetail.favoriteSnapshot() = FavoritePlayer(
        id = id,
        handle = profile.handle,
        realName = profile.realName,
        countryCode = profile.countryCode,
        countryName = profile.countryName,
    )

    private companion object {
        const val PLAYER_ID = "123"
    }
}
