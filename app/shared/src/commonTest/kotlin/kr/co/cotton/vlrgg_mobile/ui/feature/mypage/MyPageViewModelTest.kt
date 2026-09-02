package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MyPageViewModelTest {
    @Test
    fun startsBothObservationsAndKeepsEmptyAndPopulatedSectionsIndependent() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)

        assertEquals(MyPageUiState(), viewModel.uiState.value)
        assertEquals(1, repository.teamSubscriptions.size)
        assertEquals(1, repository.playerSubscriptions.size)

        repository.emitTeams(AppResult.Success(listOf(team("2"), team("1"))))
        repository.emitPlayers(AppResult.Success(emptyList()))
        runCurrent()

        assertEquals(
            FavoriteSectionState.Content(listOf(team("2"), team("1"))),
            viewModel.uiState.value.favoriteTeams,
        )
        assertEquals(FavoriteSectionState.Empty, viewModel.uiState.value.favoritePlayers)
        assertFalse(viewModel.uiState.value.isFullError)
    }

    @Test
    fun oneSectionFailureNeverHidesTheOtherSection() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)

        repository.emitTeams(AppResult.Success(listOf(team())))
        repository.emitPlayers(AppResult.Failure)
        runCurrent()

        assertEquals(FavoriteSectionState.Content(listOf(team())), viewModel.uiState.value.favoriteTeams)
        assertEquals(FavoriteSectionState.Error, viewModel.uiState.value.favoritePlayers)
        assertFalse(viewModel.uiState.value.isFullError)

        repository.emitTeams(AppResult.Failure)
        repository.emitPlayers(AppResult.Success(listOf(player())))
        runCurrent()

        assertEquals(FavoriteSectionState.Error, viewModel.uiState.value.favoriteTeams)
        assertEquals(FavoriteSectionState.Content(listOf(player())), viewModel.uiState.value.favoritePlayers)
        assertFalse(viewModel.uiState.value.isFullError)
    }

    @Test
    fun onlyTwoFirstFailuresBecomeFullErrorAndRetryIsGenerationSafe() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)

        repository.emitTeams(AppResult.Failure)
        repository.emitPlayers(AppResult.Failure)
        runCurrent()

        assertTrue(viewModel.uiState.value.isFullError)

        viewModel.retry()

        assertEquals(FavoriteSectionState.Loading, viewModel.uiState.value.favoriteTeams)
        assertEquals(FavoriteSectionState.Loading, viewModel.uiState.value.favoritePlayers)
        assertFalse(viewModel.uiState.value.isFullError)
        assertEquals(2, repository.teamSubscriptions.size)
        assertEquals(2, repository.playerSubscriptions.size)

        viewModel.retry()
        assertEquals(2, repository.teamSubscriptions.size)
        assertEquals(2, repository.playerSubscriptions.size)

        repository.emitTeams(AppResult.Success(listOf(team("stale"))), subscription = 0)
        repository.emitPlayers(AppResult.Success(listOf(player("stale"))), subscription = 0)
        repository.emitTeams(AppResult.Success(listOf(team("fresh"))), subscription = 1)
        repository.emitPlayers(AppResult.Success(listOf(player("fresh"))), subscription = 1)
        runCurrent()

        assertEquals(
            FavoriteSectionState.Content(listOf(team("fresh"))),
            viewModel.uiState.value.favoriteTeams,
        )
        assertEquals(
            FavoriteSectionState.Content(listOf(player("fresh"))),
            viewModel.uiState.value.favoritePlayers,
        )
    }

    @Test
    fun fullRetryInvalidatesBothOldSubscriptionsBeforeStartingEitherReplacement() =
        runImmediateViewModelTest {
            val freshTeams = listOf(team("fresh"))
            val freshPlayers = listOf(player("fresh"))
            val repository = ImmediateRetryFavoriteRepository(
                freshTeams = freshTeams,
                freshPlayers = freshPlayers,
            )
            val viewModel = MyPageViewModel(repository)
            assertTrue(viewModel.uiState.value.isFullError)

            var stateWhenReplacementTeamStarted: MyPageUiState? = null
            repository.onReplacementTeamCollection = {
                repository.emitFromOldPlayer(AppResult.Success(listOf(player("stale"))))
                stateWhenReplacementTeamStarted = viewModel.uiState.value
            }

            viewModel.retry()

            val restartState = checkNotNull(stateWhenReplacementTeamStarted)
            assertEquals(FavoriteSectionState.Loading, restartState.favoriteTeams)
            assertEquals(FavoriteSectionState.Loading, restartState.favoritePlayers)
            assertEquals(FavoriteSectionState.Content(freshTeams), viewModel.uiState.value.favoriteTeams)
            assertEquals(FavoriteSectionState.Content(freshPlayers), viewModel.uiState.value.favoritePlayers)
        }

    @Test
    fun failuresAfterAnySuccessfulSnapshotStaySectionLocal() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)

        repository.emitTeams(AppResult.Success(emptyList()))
        repository.emitPlayers(AppResult.Success(listOf(player())))
        runCurrent()
        repository.emitTeams(AppResult.Failure)
        repository.emitPlayers(AppResult.Failure)
        runCurrent()

        assertEquals(FavoriteSectionState.Error, viewModel.uiState.value.favoriteTeams)
        assertEquals(FavoriteSectionState.Error, viewModel.uiState.value.favoritePlayers)
        assertFalse(viewModel.uiState.value.isFullError)
    }

    @Test
    fun sectionRetryRestartsOnlyTheFailedSectionAndIgnoresDuplicateRetry() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)
        repository.emitTeams(AppResult.Failure)
        repository.emitPlayers(AppResult.Success(listOf(player())))
        runCurrent()

        viewModel.retryFavoriteTeams()
        viewModel.retryFavoriteTeams()

        assertEquals(FavoriteSectionState.Loading, viewModel.uiState.value.favoriteTeams)
        assertEquals(FavoriteSectionState.Content(listOf(player())), viewModel.uiState.value.favoritePlayers)
        assertEquals(2, repository.teamSubscriptions.size)
        assertEquals(1, repository.playerSubscriptions.size)

        repository.emitTeams(AppResult.Success(listOf(team("retry"))))
        runCurrent()

        assertEquals(
            FavoriteSectionState.Content(listOf(team("retry"))),
            viewModel.uiState.value.favoriteTeams,
        )
    }

    @Test
    fun repositoryEmissionsReplaceSnapshotsWithoutReloadingOrSorting() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)

        repository.emitTeams(AppResult.Success(listOf(team("3"), team("1"))))
        repository.emitPlayers(AppResult.Success(listOf(player("9"))))
        runCurrent()
        repository.emitTeams(AppResult.Success(listOf(team("1"), team("4"))))
        repository.emitPlayers(AppResult.Success(emptyList()))
        runCurrent()

        assertEquals(
            FavoriteSectionState.Content(listOf(team("1"), team("4"))),
            viewModel.uiState.value.favoriteTeams,
        )
        assertEquals(FavoriteSectionState.Empty, viewModel.uiState.value.favoritePlayers)
        assertEquals(1, repository.teamSubscriptions.size)
        assertEquals(1, repository.playerSubscriptions.size)
    }

    @Test
    fun teamRemovalFailureRestoresTheItemAndRetryUsesTheSameIdOnce() = runViewModelTest {
        val repository = FakeFavoriteRepository(
            teamRemoveResults = listOf(AppResult.Failure, AppResult.Success(Unit)),
        )
        val viewModel = MyPageViewModel(repository)
        val favorites = listOf(team("first"), team("target"), team("last"))
        repository.emitTeams(AppResult.Success(favorites))
        repository.emitPlayers(AppResult.Success(emptyList()))
        runCurrent()

        viewModel.removeFavoriteTeam("target")
        assertEquals(
            FavoriteSectionState.Content(listOf(team("first"), team("last"))),
            viewModel.uiState.value.favoriteTeams,
        )
        runCurrent()

        assertEquals(FavoriteSectionState.Content(favorites), viewModel.uiState.value.favoriteTeams)
        assertEquals(FavoriteRemovalTarget.Team("target"), viewModel.uiState.value.failedRemoval)
        assertEquals(listOf("target"), repository.removedTeamIds)

        viewModel.retryFavoriteRemoval()
        viewModel.retryFavoriteRemoval()
        runCurrent()

        assertEquals(listOf("target", "target"), repository.removedTeamIds)
        assertNull(viewModel.uiState.value.failedRemoval)
        assertEquals(
            FavoriteSectionState.Content(listOf(team("first"), team("last"))),
            viewModel.uiState.value.favoriteTeams,
        )
    }

    @Test
    fun successfulTeamRemovalStaysHiddenUntilObservationAcknowledgesIt() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)
        val beforeRemoval = listOf(team("target"), team("remaining"))
        val afterRemoval = listOf(team("remaining"))
        repository.emitTeams(AppResult.Success(beforeRemoval))
        repository.emitPlayers(AppResult.Success(emptyList()))
        runCurrent()

        viewModel.removeFavoriteTeam("target")
        runCurrent()
        repository.emitTeams(AppResult.Success(beforeRemoval))
        runCurrent()

        assertEquals(
            FavoriteSectionState.Content(afterRemoval),
            viewModel.uiState.value.favoriteTeams,
        )

        repository.emitTeams(AppResult.Success(afterRemoval))
        runCurrent()
        repository.emitTeams(AppResult.Success(beforeRemoval))
        runCurrent()

        assertEquals(
            FavoriteSectionState.Content(beforeRemoval),
            viewModel.uiState.value.favoriteTeams,
        )
    }

    @Test
    fun successfulPlayerRemovalStaysHiddenUntilObservationAcknowledgesIt() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)
        val beforeRemoval = listOf(player("target"), player("remaining"))
        val afterRemoval = listOf(player("remaining"))
        repository.emitTeams(AppResult.Success(emptyList()))
        repository.emitPlayers(AppResult.Success(beforeRemoval))
        runCurrent()

        viewModel.removeFavoritePlayer("target")
        runCurrent()
        repository.emitPlayers(AppResult.Success(beforeRemoval))
        runCurrent()

        assertEquals(
            FavoriteSectionState.Content(afterRemoval),
            viewModel.uiState.value.favoritePlayers,
        )

        repository.emitPlayers(AppResult.Success(afterRemoval))
        runCurrent()
        repository.emitPlayers(AppResult.Success(beforeRemoval))
        runCurrent()

        assertEquals(
            FavoriteSectionState.Content(beforeRemoval),
            viewModel.uiState.value.favoritePlayers,
        )
    }

    @Test
    fun removalFailureAfterObservationFailureRestoresTheWholeSectionInStoredOrder() = runViewModelTest {
        val removalResult = CompletableDeferred<AppResult<Unit>>()
        val repository = FakeFavoriteRepository(teamRemoveResult = removalResult)
        val viewModel = MyPageViewModel(repository)
        val teams = listOf(team("first"), team("target"), team("last"))
        val players = listOf(player("sibling"))
        repository.emitTeams(AppResult.Success(teams))
        repository.emitPlayers(AppResult.Success(players))
        runCurrent()

        viewModel.removeFavoriteTeam("target")
        runCurrent()
        assertEquals(
            FavoriteSectionState.Content(listOf(team("first"), team("last"))),
            viewModel.uiState.value.favoriteTeams,
        )

        repository.emitTeams(AppResult.Failure)
        runCurrent()
        assertEquals(FavoriteSectionState.Error, viewModel.uiState.value.favoriteTeams)

        removalResult.complete(AppResult.Failure)
        runCurrent()

        assertEquals(FavoriteSectionState.Content(teams), viewModel.uiState.value.favoriteTeams)
        assertEquals(FavoriteSectionState.Content(players), viewModel.uiState.value.favoritePlayers)
        assertEquals(FavoriteRemovalTarget.Team("target"), viewModel.uiState.value.failedRemoval)
        assertFalse(viewModel.uiState.value.isFullError)
    }

    @Test
    fun removalFailureUsesTheLatestRawSnapshotAndItsRepositoryOrder() = runViewModelTest {
        val removalResult = CompletableDeferred<AppResult<Unit>>()
        val repository = FakeFavoriteRepository(teamRemoveResult = removalResult)
        val viewModel = MyPageViewModel(repository)
        repository.emitTeams(AppResult.Success(listOf(team("first"), team("target"), team("last"))))
        repository.emitPlayers(AppResult.Success(emptyList()))
        runCurrent()

        viewModel.removeFavoriteTeam("target")
        runCurrent()
        val latestSnapshot = listOf(team("target"), team("last"))
        repository.emitTeams(AppResult.Success(latestSnapshot))
        repository.emitTeams(AppResult.Failure)
        runCurrent()
        assertEquals(FavoriteSectionState.Error, viewModel.uiState.value.favoriteTeams)

        removalResult.complete(AppResult.Failure)
        runCurrent()

        assertEquals(FavoriteSectionState.Content(latestSnapshot), viewModel.uiState.value.favoriteTeams)
        assertEquals(FavoriteRemovalTarget.Team("target"), viewModel.uiState.value.failedRemoval)
    }

    @Test
    fun removalFailureDoesNotResurrectATargetMissingFromTheLatestSnapshot() = runViewModelTest {
        val removalResult = CompletableDeferred<AppResult<Unit>>()
        val repository = FakeFavoriteRepository(teamRemoveResult = removalResult)
        val viewModel = MyPageViewModel(repository)
        repository.emitTeams(AppResult.Success(listOf(team("target"), team("last"))))
        repository.emitPlayers(AppResult.Success(emptyList()))
        runCurrent()

        viewModel.removeFavoriteTeam("target")
        runCurrent()
        val latestSnapshot = listOf(team("last"))
        repository.emitTeams(AppResult.Success(latestSnapshot))
        repository.emitTeams(AppResult.Failure)
        runCurrent()
        assertEquals(FavoriteSectionState.Error, viewModel.uiState.value.favoriteTeams)

        viewModel.retryFavoriteTeams()
        assertEquals(FavoriteSectionState.Loading, viewModel.uiState.value.favoriteTeams)

        removalResult.complete(AppResult.Failure)
        runCurrent()

        assertEquals(FavoriteSectionState.Content(latestSnapshot), viewModel.uiState.value.favoriteTeams)
        assertNull(viewModel.uiState.value.failedRemoval)
        viewModel.retryFavoriteRemoval()
        runCurrent()
        assertEquals(listOf("target"), repository.removedTeamIds)
    }

    @Test
    fun playerRemovalCallsOnlyThePlayerRepositoryMethodAndCancellationIsNotError() = runViewModelTest {
        val repository = FakeFavoriteRepository()
        val viewModel = MyPageViewModel(repository)
        repository.emitTeams(AppResult.Success(emptyList()))
        repository.emitPlayers(AppResult.Success(listOf(player("target"))))
        runCurrent()

        viewModel.removeFavoritePlayer("target")
        runCurrent()

        assertEquals(listOf("target"), repository.removedPlayerIds)
        assertTrue(repository.removedTeamIds.isEmpty())
        assertEquals(FavoriteSectionState.Empty, viewModel.uiState.value.favoritePlayers)

        repository.cancelTeamObservation(CancellationException("cancelled"))
        runCurrent()

        assertEquals(FavoriteSectionState.Empty, viewModel.uiState.value.favoriteTeams)
        assertFalse(viewModel.uiState.value.isFullError)
    }

    private fun runViewModelTest(testBody: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun runImmediateViewModelTest(testBody: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun team(id: String = "team") = FavoriteTeam(
        id = id,
        name = "Team $id",
        tag = "TAG",
        country = "Korea",
    )

    private fun player(id: String = "player") = FavoritePlayer(
        id = id,
        handle = "Player $id",
        realName = null,
        countryCode = "KR",
        countryName = "Korea",
    )

    private class FakeFavoriteRepository(
        teamRemoveResults: List<AppResult<Unit>> = emptyList(),
        playerRemoveResults: List<AppResult<Unit>> = emptyList(),
        private val teamRemoveResult: CompletableDeferred<AppResult<Unit>>? = null,
    ) : FavoriteRepository {
        val teamSubscriptions = mutableListOf<Channel<AppResult<List<FavoriteTeam>>>>()
        val playerSubscriptions = mutableListOf<Channel<AppResult<List<FavoritePlayer>>>>()
        val removedTeamIds = mutableListOf<String>()
        val removedPlayerIds = mutableListOf<String>()
        private val teamRemoveResults = ArrayDeque(teamRemoveResults)
        private val playerRemoveResults = ArrayDeque(playerRemoveResults)

        override fun observeFavoriteTeams(): Flow<AppResult<List<FavoriteTeam>>> =
            Channel<AppResult<List<FavoriteTeam>>>(Channel.UNLIMITED)
                .also(teamSubscriptions::add)
                .receiveAsFlow()

        override fun observeFavoritePlayers(): Flow<AppResult<List<FavoritePlayer>>> =
            Channel<AppResult<List<FavoritePlayer>>>(Channel.UNLIMITED)
                .also(playerSubscriptions::add)
                .receiveAsFlow()

        override suspend fun getFavoriteTeams() = error("MyPage must observe instead of snapshot-loading teams")

        override suspend fun getFavoritePlayers() = error("MyPage must observe instead of snapshot-loading players")

        override suspend fun addFavoriteTeam(favorite: FavoriteTeam) = error("MyPage never adds a team")

        override suspend fun addFavoritePlayer(favorite: FavoritePlayer) = error("MyPage never adds a player")

        override suspend fun removeFavoriteTeam(teamId: String): AppResult<Unit> {
            removedTeamIds += teamId
            teamRemoveResult?.let { return it.await() }
            return if (teamRemoveResults.isEmpty()) AppResult.Success(Unit) else teamRemoveResults.removeFirst()
        }

        override suspend fun removeFavoritePlayer(playerId: String): AppResult<Unit> {
            removedPlayerIds += playerId
            return if (playerRemoveResults.isEmpty()) AppResult.Success(Unit) else playerRemoveResults.removeFirst()
        }

        suspend fun emitTeams(
            result: AppResult<List<FavoriteTeam>>,
            subscription: Int = teamSubscriptions.lastIndex,
        ) {
            teamSubscriptions[subscription].send(result)
        }

        suspend fun emitPlayers(
            result: AppResult<List<FavoritePlayer>>,
            subscription: Int = playerSubscriptions.lastIndex,
        ) {
            playerSubscriptions[subscription].send(result)
        }

        fun cancelTeamObservation(cancellation: CancellationException) {
            teamSubscriptions.last().close(cancellation)
        }
    }

    private class ImmediateRetryFavoriteRepository(
        private val freshTeams: List<FavoriteTeam>,
        private val freshPlayers: List<FavoritePlayer>,
    ) : FavoriteRepository {
        private val oldPlayerResults = MutableSharedFlow<AppResult<List<FavoritePlayer>>>(
            extraBufferCapacity = 1,
        )
        private var teamObservationCount = 0
        private var playerObservationCount = 0

        var onReplacementTeamCollection: (() -> Unit)? = null

        override fun observeFavoriteTeams(): Flow<AppResult<List<FavoriteTeam>>> {
            val observation = teamObservationCount++
            return flow {
                if (observation == 0) {
                    emit(AppResult.Failure)
                    awaitCancellation()
                }

                onReplacementTeamCollection?.invoke()
                emit(AppResult.Success(freshTeams))
                awaitCancellation()
            }
        }

        override fun observeFavoritePlayers(): Flow<AppResult<List<FavoritePlayer>>> {
            val observation = playerObservationCount++
            return flow {
                if (observation == 0) {
                    emit(AppResult.Failure)
                    oldPlayerResults.collect { emit(it) }
                } else {
                    emit(AppResult.Success(freshPlayers))
                    awaitCancellation()
                }
            }
        }

        override suspend fun getFavoriteTeams() = error("unused")

        override suspend fun getFavoritePlayers() = error("unused")

        override suspend fun addFavoriteTeam(favorite: FavoriteTeam) = error("unused")

        override suspend fun addFavoritePlayer(favorite: FavoritePlayer) = error("unused")

        override suspend fun removeFavoriteTeam(teamId: String) = error("unused")

        override suspend fun removeFavoritePlayer(playerId: String) = error("unused")

        fun emitFromOldPlayer(result: AppResult<List<FavoritePlayer>>) {
            assertTrue(oldPlayerResults.tryEmit(result))
        }
    }
}
