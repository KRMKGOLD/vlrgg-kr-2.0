package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

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
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.TeamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TeamDetailViewModelTest {

    @Test
    fun initialStateIsLoadingAndRequestsTeamIdentityExactlyOnce() = runViewModelTest {
        val repository = FakeTeamRepository(listOf(AppResult.Success(teamDetail())))
        val viewModel = newViewModel(repository)

        assertEquals(TeamDetailUiState(), viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(listOf(TEAM_ID), repository.requestedTeamIds)
    }

    @Test
    fun successPreservesTheWholeTeamDetailAsContent() = runViewModelTest {
        val team = teamDetail()
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(team))),
            favoriteRepository = FakeFavoriteRepository(),
            teamId = TEAM_ID,
        )

        advanceUntilIdle()

        assertEquals(
            TeamDetailUiState(
                TeamDetailContentState.Content(team),
                TeamFavoriteUiState(isRestored = true),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun successfulEmptySectionsRemainContentInsteadOfBecomingOverallError() = runViewModelTest {
        val sparseTeam = teamDetail(
            upcomingMatches = emptyList(),
            recentMatches = emptyList(),
            players = emptyList(),
            staff = emptyList(),
            news = emptyList(),
        )
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(sparseTeam))),
            favoriteRepository = FakeFavoriteRepository(),
            teamId = TEAM_ID,
        )

        advanceUntilIdle()

        assertEquals(
            TeamDetailUiState(
                TeamDetailContentState.Content(sparseTeam),
                TeamFavoriteUiState(isRestored = true),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun failureBecomesOverallErrorWithoutExposingRawFailureDetails() = runViewModelTest {
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Failure)),
            favoriteRepository = FakeFavoriteRepository(),
            teamId = TEAM_ID,
        )

        advanceUntilIdle()

        assertEquals(
            TeamDetailUiState(
                TeamDetailContentState.Error,
                TeamFavoriteUiState(isRestored = true),
            ),
            viewModel.uiState.value,
        )
        assertFalse(viewModel.uiState.value.toString().contains("exception", ignoreCase = true))
        assertFalse(viewModel.uiState.value.toString().contains("http", ignoreCase = true))
    }

    @Test
    fun retryUsesTheSameTeamIdentityAndTransitionsThroughLoading() = runViewModelTest {
        val team = teamDetail()
        val repository = FakeTeamRepository(
            listOf(AppResult.Failure, AppResult.Success(team)),
        )
        val viewModel = newViewModel(repository)
        advanceUntilIdle()

        viewModel.retry()

        assertEquals(
            TeamDetailUiState(favorite = TeamFavoriteUiState(isRestored = true)),
            viewModel.uiState.value,
        )
        advanceUntilIdle()
        assertEquals(listOf(TEAM_ID, TEAM_ID), repository.requestedTeamIds)
        assertEquals(
            TeamDetailUiState(
                TeamDetailContentState.Content(team),
                TeamFavoriteUiState(isRestored = true),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun retryOutsideErrorDoesNotCreateDuplicateRequests() = runViewModelTest {
        val loadingRepository = FakeTeamRepository(listOf(AppResult.Success(teamDetail())))
        val loadingViewModel = newViewModel(loadingRepository)

        loadingViewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(TEAM_ID), loadingRepository.requestedTeamIds)

        val contentRepository = FakeTeamRepository(listOf(AppResult.Success(teamDetail())))
        val contentViewModel = newViewModel(contentRepository)
        advanceUntilIdle()

        contentViewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(TEAM_ID), contentRepository.requestedTeamIds)
    }

    @Test
    fun repeatedRetryInTheSameErrorTurnStartsOnlyOneNewRequest() = runViewModelTest {
        val repository = FakeTeamRepository(
            listOf(AppResult.Failure, AppResult.Success(teamDetail())),
        )
        val viewModel = newViewModel(repository)
        advanceUntilIdle()

        viewModel.retry()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(TEAM_ID, TEAM_ID), repository.requestedTeamIds)
    }

    @Test
    fun restoresStoredFavoriteAndLeavesUnregisteredTeamOff() = runViewModelTest {
        val storedViewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(teamDetail()))),
            favoriteRepository = FakeFavoriteRepository(
                favoriteTeamsResults = listOf(AppResult.Success(listOf(teamDetail().toFavoriteTeam()))),
            ),
            teamId = TEAM_ID,
        )
        advanceUntilIdle()
        assertTrue(storedViewModel.uiState.value.favorite.isFavorite)
        assertTrue(storedViewModel.uiState.value.favorite.isRestored)

        val unregisteredViewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(teamDetail()))),
            favoriteRepository = FakeFavoriteRepository(),
            teamId = TEAM_ID,
        )
        advanceUntilIdle()
        assertFalse(unregisteredViewModel.uiState.value.favorite.isFavorite)
        assertTrue(unregisteredViewModel.uiState.value.favorite.isRestored)
    }

    @Test
    fun favoriteRestoreFailureKeepsTheFavoriteUnrestoredAndPreventsMutation() = runViewModelTest {
        val team = teamDetail()
        val favorites = FakeFavoriteRepository(
            favoriteTeamsResults = listOf(AppResult.Failure),
            addResults = listOf(AppResult.Success(Unit)),
        )
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(team))),
            favoriteRepository = favorites,
            teamId = TEAM_ID,
        )

        advanceUntilIdle()

        assertEquals(TeamDetailContentState.Content(team), viewModel.uiState.value.contentState)
        assertEquals(TeamFavoriteUiState(), viewModel.uiState.value.favorite)

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(favorites.addedTeams.isEmpty())
        assertTrue(favorites.removedTeamIds.isEmpty())
    }

    @Test
    fun favoriteRetryRestoresFavoriteAfterInitialRestoreFailure() = runViewModelTest {
        val team = teamDetail()
        val teams = FakeTeamRepository(
            listOf(
                AppResult.Failure,
                AppResult.Success(team),
            ),
        )
        val favorites = FakeFavoriteRepository(
            favoriteTeamsResults = listOf(
                AppResult.Failure,
                AppResult.Success(emptyList()),
            ),
        )
        val viewModel = TeamDetailViewModel(
            teamRepository = teams,
            favoriteRepository = favorites,
            teamId = TEAM_ID,
        )

        advanceUntilIdle()
        assertEquals(TeamDetailContentState.Error, viewModel.uiState.value.contentState)
        assertFalse(viewModel.uiState.value.favorite.isRestored)
        assertEquals(listOf(TEAM_ID), teams.requestedTeamIds)
        assertEquals(1, favorites.restoreCallCount)

        viewModel.retry()
        assertEquals(TeamDetailContentState.Loading, viewModel.uiState.value.contentState)
        advanceUntilIdle()

        assertEquals(TeamDetailContentState.Content(team), viewModel.uiState.value.contentState)
        assertTrue(viewModel.uiState.value.favorite.isRestored)
        assertEquals(listOf(TEAM_ID, TEAM_ID), teams.requestedTeamIds)
        assertEquals(2, favorites.restoreCallCount)
    }

    @Test
    fun optimisticAddAndRemovePreserveContentAndCallTheExactFavoriteRepositoryMethod() = runViewModelTest {
        val team = teamDetail()
        val favorites = FakeFavoriteRepository(addResults = listOf(AppResult.Success(Unit)))
        val addViewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(team))),
            favoriteRepository = favorites,
            teamId = TEAM_ID,
        )
        advanceUntilIdle()

        addViewModel.toggleFavorite()
        assertTrue(addViewModel.uiState.value.favorite.isFavorite)
        assertTrue(addViewModel.uiState.value.favorite.isMutationInProgress)
        assertEquals(TeamDetailContentState.Content(team), addViewModel.uiState.value.contentState)
        advanceUntilIdle()
        assertEquals(listOf(team.toFavoriteTeam()), favorites.addedTeams)

        val removeFavorites = FakeFavoriteRepository(
            favoriteTeamsResults = listOf(AppResult.Success(listOf(team.toFavoriteTeam()))),
            removeResults = listOf(AppResult.Success(Unit)),
        )
        val removeViewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(team))),
            favoriteRepository = removeFavorites,
            teamId = TEAM_ID,
        )
        advanceUntilIdle()
        removeViewModel.toggleFavorite()
        assertFalse(removeViewModel.uiState.value.favorite.isFavorite)
        assertEquals(TeamDetailContentState.Content(team), removeViewModel.uiState.value.contentState)
        advanceUntilIdle()
        assertEquals(listOf(TEAM_ID), removeFavorites.removedTeamIds)
    }

    @Test
    fun failedMutationsRollbackExposeOnlySafeIntentAndRetryExactSnapshotOnce() = runViewModelTest {
        val team = teamDetail()
        val favorites = FakeFavoriteRepository(
            addResults = listOf(AppResult.Failure, AppResult.Success(Unit)),
        )
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(team))),
            favoriteRepository = favorites,
            teamId = TEAM_ID,
        )
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.favorite.isFavorite)
        assertEquals(TeamFavoriteMutationIntent.Add, viewModel.uiState.value.favorite.failedIntent)
        assertFalse(viewModel.uiState.value.toString().contains("exception", ignoreCase = true))
        assertFalse(viewModel.uiState.value.toString().contains("http", ignoreCase = true))

        viewModel.retryFavoriteMutation()
        viewModel.retryFavoriteMutation()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.favorite.isFavorite)
        assertEquals(listOf(team.toFavoriteTeam(), team.toFavoriteTeam()), favorites.addedTeams)
        assertEquals(null, viewModel.uiState.value.favorite.failedIntent)
    }

    @Test
    fun failedRemoveRollsBackToOnAndDismissPreventsStaleSnackbar() = runViewModelTest {
        val team = teamDetail()
        val favorites = FakeFavoriteRepository(
            favoriteTeamsResults = listOf(AppResult.Success(listOf(team.toFavoriteTeam()))),
            removeResults = listOf(AppResult.Failure),
        )
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(team))),
            favoriteRepository = favorites,
            teamId = TEAM_ID,
        )
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.favorite.isFavorite)
        assertEquals(TeamFavoriteMutationIntent.Remove, viewModel.uiState.value.favorite.failedIntent)
        viewModel.dismissFavoriteError()
        assertEquals(null, viewModel.uiState.value.favorite.failedIntent)
    }

    @Test
    fun duplicateClickDuringInFlightMutationDoesNotAddDuplicateRepositoryRequest() = runViewModelTest {
        val favorites = FakeFavoriteRepository(addResults = listOf(AppResult.Success(Unit)))
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(teamDetail()))),
            favoriteRepository = favorites,
            teamId = TEAM_ID,
        )
        advanceUntilIdle()

        viewModel.toggleFavorite()
        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertEquals(1, favorites.addedTeams.size)
    }

    @Test
    fun favoriteToggleDoesNotCreateASyntheticSnapshotBeforeContentExists() = runViewModelTest {
        val favorites = FakeFavoriteRepository()
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Failure)),
            favoriteRepository = favorites,
            teamId = TEAM_ID,
        )
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(favorites.addedTeams.isEmpty())
        assertTrue(favorites.removedTeamIds.isEmpty())
    }

    private fun runViewModelTest(
        testBody: suspend TestScope.() -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun teamDetail(
        upcomingMatches: List<TeamMatch> = listOf(
            TeamMatch(
                id = "match-upcoming",
                eventName = "VCT Pacific",
                eventStage = "Stage 2",
                teamName = "KIWOOM DRX",
                opponentName = "Sentinels",
                statusText = "in 2d",
                scheduledAtText = "2026-08-28 17:00",
            ),
        ),
        recentMatches: List<TeamMatch> = listOf(
            TeamMatch(
                id = "match-recent",
                eventName = "VCT Pacific",
                eventStage = null,
                teamName = "KIWOOM DRX",
                opponentName = "Gen.G",
                statusText = "final",
                scheduledAtText = "2026-08-25",
            ),
        ),
        players: List<TeamRosterMember> = listOf(
            TeamRosterMember("player-1", "MaKo", "Kim Myeong-kwan", listOf("player")),
        ),
        staff: List<TeamRosterMember> = listOf(
            TeamRosterMember("staff-1", "termi", null, listOf("head coach")),
        ),
        news: List<TeamNews> = listOf(
            TeamNews("article-1", "drx-news", "DRX news", "2026-08-25"),
        ),
    ) = TeamDetail(
        id = TEAM_ID,
        name = "KIWOOM DRX",
        tag = "KRX",
        country = "South Korea",
        upcomingMatches = upcomingMatches,
        recentMatches = recentMatches,
        players = players,
        staff = staff,
        news = news,
    )

    private fun newViewModel(teamRepository: TeamRepository) = TeamDetailViewModel(
        teamRepository = teamRepository,
        favoriteRepository = FakeFavoriteRepository(),
        teamId = TEAM_ID,
    )

    private class FakeTeamRepository(
        private val results: List<AppResult<TeamDetail>>,
    ) : TeamRepository {
        val requestedTeamIds = mutableListOf<String>()

        override suspend fun getTeamDetail(teamId: String): AppResult<TeamDetail> {
            val requestIndex = requestedTeamIds.size
            requestedTeamIds += teamId
            check(requestIndex in results.indices) {
                "No Team result prepared for request $requestIndex"
            }
            return results[requestIndex]
        }
    }

    private class FakeFavoriteRepository(
        private val favoriteTeamsResults: List<AppResult<List<FavoriteTeam>>> = listOf(AppResult.Success(emptyList())),
        private val addResults: List<AppResult<Unit>> = emptyList(),
        private val removeResults: List<AppResult<Unit>> = emptyList(),
    ) : FavoriteRepository {
        val addedTeams = mutableListOf<FavoriteTeam>()
        val removedTeamIds = mutableListOf<String>()
        var restoreCallCount = 0

        override fun observeFavoriteTeams(): Flow<AppResult<List<FavoriteTeam>>> = emptyFlow()

        override fun observeFavoritePlayers(): Flow<AppResult<List<FavoritePlayer>>> = emptyFlow()

        override suspend fun getFavoriteTeams(): AppResult<List<FavoriteTeam>> {
            restoreCallCount += 1
            return favoriteTeamsResults.getOrElse(restoreCallCount - 1) { AppResult.Success(emptyList()) }
        }

        override suspend fun getFavoritePlayers(): AppResult<List<FavoritePlayer>> = AppResult.Success(emptyList())

        override suspend fun addFavoriteTeam(favorite: FavoriteTeam): AppResult<Unit> {
            addedTeams += favorite
            return addResults.getOrElse(addedTeams.lastIndex) { AppResult.Success(Unit) }
        }

        override suspend fun addFavoritePlayer(favorite: FavoritePlayer): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun removeFavoriteTeam(teamId: String): AppResult<Unit> {
            removedTeamIds += teamId
            return removeResults.getOrElse(removedTeamIds.lastIndex) { AppResult.Success(Unit) }
        }

        override suspend fun removeFavoritePlayer(playerId: String): AppResult<Unit> = AppResult.Success(Unit)
    }

    private fun TeamDetail.toFavoriteTeam() = FavoriteTeam(
        id = id,
        name = name,
        tag = tag,
        country = country,
    )

    private companion object {
        const val TEAM_ID = "8185"
    }
}
