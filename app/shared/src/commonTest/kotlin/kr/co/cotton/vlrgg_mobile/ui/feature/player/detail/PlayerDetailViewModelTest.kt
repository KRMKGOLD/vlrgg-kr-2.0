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
    fun favoriteRestoreFailureKeepsContentAndExposesOnlySafeRetryableState() = runViewModelTest {
        val player = playerDetail()
        val favorites = FakeFavoriteRepository(favoritePlayersResults = listOf(AppResult.Failure))
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )

        advanceUntilIdle()

        assertEquals(PlayerDetailContentState.Content(player), viewModel.uiState.value.contentState)
        assertEquals(
            PlayerFavoriteUiState(hasRestoreFailure = true),
            viewModel.uiState.value.favorite,
        )
        assertFalse(viewModel.uiState.value.toString().contains("exception", ignoreCase = true))
        assertFalse(viewModel.uiState.value.toString().contains("http", ignoreCase = true))

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(favorites.added.isEmpty())
        assertTrue(favorites.removed.isEmpty())
    }

    @Test
    fun retryFavoriteRestoreRetriesOneFailedRestoreOnceAndConvergesToStoredState() = runViewModelTest {
        val player = playerDetail()
        val favorites = FakeFavoriteRepository(
            favoritePlayersResults = listOf(
                AppResult.Failure,
                AppResult.Success(listOf(player.favoriteSnapshot())),
            ),
        )
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )

        advanceUntilIdle()
        viewModel.retryFavoriteRestore()
        viewModel.retryFavoriteRestore()
        advanceUntilIdle()

        assertEquals(2, favorites.restoreCallCount)
        assertTrue(viewModel.uiState.value.favorite.isRestored)
        assertTrue(viewModel.uiState.value.favorite.isFavorite)
        assertFalse(viewModel.uiState.value.favorite.hasRestoreFailure)

        viewModel.retryFavoriteRestore()
        advanceUntilIdle()
        assertEquals(2, favorites.restoreCallCount)
    }

    @Test
    fun favoriteRetryRestoresFavoriteAfterInitialRestoreFailure() = runViewModelTest {
        val player = playerDetail()
        val players = FakePlayerRepository(
            listOf(
                AppResult.Failure,
                AppResult.Success(player),
            ),
        )
        val favorites = FakeFavoriteRepository(
            favoritePlayersResults = listOf(
                AppResult.Failure,
                AppResult.Success(emptyList()),
            ),
        )
        val viewModel = PlayerDetailViewModel(
            players,
            favorites,
            PLAYER_ID,
        )

        advanceUntilIdle()
        assertEquals(PlayerDetailContentState.Error, viewModel.uiState.value.contentState)
        assertFalse(viewModel.uiState.value.favorite.isRestored)
        assertEquals(listOf(PLAYER_ID), players.requestedPlayerIds)
        assertEquals(1, favorites.restoreCallCount)

        viewModel.retry()
        assertEquals(PlayerDetailContentState.Loading, viewModel.uiState.value.contentState)
        advanceUntilIdle()

        assertEquals(PlayerDetailContentState.Content(player), viewModel.uiState.value.contentState)
        assertTrue(viewModel.uiState.value.favorite.isRestored)
        assertEquals(listOf(PLAYER_ID, PLAYER_ID), players.requestedPlayerIds)
        assertEquals(2, favorites.restoreCallCount)
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
    fun addFavoritePersistsTheLatestProfileImageUrlIncludingNull() = runViewModelTest {
        val imageUrl = "https://cdn.example.com/stax.png"
        val withImage = playerDetail().copy(profile = playerDetail().profile.copy(imageUrl = imageUrl))
        val favorites = FakeFavoriteRepository()
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(withImage))),
            favorites,
            PLAYER_ID,
        )

        advanceUntilIdle()
        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertEquals(imageUrl, favorites.added.single().imageUrl)

        val withoutImageFavorites = FakeFavoriteRepository()
        val withoutImageViewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(playerDetail().copy(profile = playerDetail().profile.copy(imageUrl = null))))),
            withoutImageFavorites,
            PLAYER_ID,
        )
        advanceUntilIdle()
        withoutImageViewModel.toggleFavorite()
        advanceUntilIdle()
        assertEquals(null, withoutImageFavorites.added.single().imageUrl)
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
    fun retryReusesSamePlayerSnapshotExactlyOnce() = runViewModelTest {
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
    }

    @Test
    fun dismissFavoriteErrorClearsAnActualMutationFailureState() = runViewModelTest {
        val player = playerDetail()
        val favorites = FakeFavoriteRepository(addResults = listOf(AppResult.Failure))
        val viewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertEquals(PlayerFavoriteMutationIntent.Add, viewModel.uiState.value.favorite.failedIntent)

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
        val favorites = FakeFavoriteRepository()

        val firstViewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )
        advanceUntilIdle()
        firstViewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(firstViewModel.uiState.value.favorite.isFavorite)

        val recreatedViewModel = PlayerDetailViewModel(
            FakePlayerRepository(listOf(AppResult.Success(player))),
            favorites,
            PLAYER_ID,
        )
        advanceUntilIdle()

        assertTrue(recreatedViewModel.uiState.value.favorite.isFavorite)
        assertEquals(2, favorites.restoreCallCount)
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
        existing: List<FavoritePlayer> = emptyList(),
        favoritePlayersResults: List<AppResult<List<FavoritePlayer>>> = emptyList(),
        addResults: List<AppResult<Unit>> = emptyList(),
        removeResults: List<AppResult<Unit>> = emptyList(),
    ) : FavoriteRepository {
        private val storedFavoritePlayers = existing.toMutableList()
        private val favoritePlayersResultQueue = ArrayDeque(favoritePlayersResults)
        private val addResultQueue = ArrayDeque(addResults)
        private val removeResultQueue = ArrayDeque(removeResults)

        var restoreCallCount = 0
        val added = mutableListOf<FavoritePlayer>()
        val removed = mutableListOf<String>()

        override fun observeFavoriteTeams() = error("unused")

        override fun observeFavoritePlayers() = error("unused")

        override suspend fun getFavoriteTeams() = error("unused")

        override suspend fun getFavoritePlayers(): AppResult<List<FavoritePlayer>> {
            restoreCallCount += 1
            return if (favoritePlayersResultQueue.isEmpty()) {
                AppResult.Success(storedFavoritePlayers.toList())
            } else {
                favoritePlayersResultQueue.removeFirst()
            }
        }

        override suspend fun addFavoriteTeam(favorite: kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam) = error("unused")

        override suspend fun addFavoritePlayer(favorite: FavoritePlayer): AppResult<Unit> {
            added += favorite
            val result = if (addResultQueue.isEmpty()) {
                AppResult.Success(Unit)
            } else {
                addResultQueue.removeFirst()
            }
            if (result is AppResult.Success) {
                val existingIndex = storedFavoritePlayers.indexOfFirst { it.id == favorite.id }
                if (existingIndex >= 0) {
                    storedFavoritePlayers[existingIndex] = favorite
                } else {
                    storedFavoritePlayers += favorite
                }
            }
            return result
        }

        override suspend fun removeFavoriteTeam(teamId: String) = error("unused")

        override suspend fun removeFavoritePlayer(playerId: String): AppResult<Unit> {
            removed += playerId
            val result = if (removeResultQueue.isEmpty()) {
                AppResult.Success(Unit)
            } else {
                removeResultQueue.removeFirst()
            }
            if (result is AppResult.Success) {
                storedFavoritePlayers.removeAll { it.id == playerId }
            }
            return result
        }
    }

    private fun PlayerDetail.favoriteSnapshot() = FavoritePlayer(
        id = id,
        handle = profile.handle,
        realName = profile.realName,
        countryCode = profile.countryCode,
        countryName = profile.countryName,
        imageUrl = profile.imageUrl,
    )

    private companion object {
        const val PLAYER_ID = "123"
    }
}
