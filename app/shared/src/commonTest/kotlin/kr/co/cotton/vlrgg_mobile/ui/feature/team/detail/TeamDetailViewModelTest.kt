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
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kr.co.cotton.vlrgg_mobile.domain.repository.TeamRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TeamDetailViewModelTest {

    @Test
    fun initialStateIsLoadingAndRequestsTeamIdentityExactlyOnce() = runViewModelTest {
        val repository = FakeTeamRepository(listOf(AppResult.Success(teamDetail())))
        val viewModel = TeamDetailViewModel(repository, TEAM_ID)

        assertEquals(TeamDetailUiState(), viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(listOf(TEAM_ID), repository.requestedTeamIds)
    }

    @Test
    fun successPreservesTheWholeTeamDetailAsContent() = runViewModelTest {
        val team = teamDetail()
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Success(team))),
            teamId = TEAM_ID,
        )

        advanceUntilIdle()

        assertEquals(
            TeamDetailUiState(TeamDetailContentState.Content(team)),
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
            teamId = TEAM_ID,
        )

        advanceUntilIdle()

        assertEquals(
            TeamDetailUiState(TeamDetailContentState.Content(sparseTeam)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun failureBecomesOverallErrorWithoutExposingRawFailureDetails() = runViewModelTest {
        val viewModel = TeamDetailViewModel(
            teamRepository = FakeTeamRepository(listOf(AppResult.Failure)),
            teamId = TEAM_ID,
        )

        advanceUntilIdle()

        assertEquals(
            TeamDetailUiState(TeamDetailContentState.Error),
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
        val viewModel = TeamDetailViewModel(repository, TEAM_ID)
        advanceUntilIdle()

        viewModel.retry()

        assertEquals(TeamDetailUiState(), viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(listOf(TEAM_ID, TEAM_ID), repository.requestedTeamIds)
        assertEquals(
            TeamDetailUiState(TeamDetailContentState.Content(team)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun retryOutsideErrorDoesNotCreateDuplicateRequests() = runViewModelTest {
        val loadingRepository = FakeTeamRepository(listOf(AppResult.Success(teamDetail())))
        val loadingViewModel = TeamDetailViewModel(loadingRepository, TEAM_ID)

        loadingViewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(TEAM_ID), loadingRepository.requestedTeamIds)

        val contentRepository = FakeTeamRepository(listOf(AppResult.Success(teamDetail())))
        val contentViewModel = TeamDetailViewModel(contentRepository, TEAM_ID)
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
        val viewModel = TeamDetailViewModel(repository, TEAM_ID)
        advanceUntilIdle()

        viewModel.retry()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(TEAM_ID, TEAM_ID), repository.requestedTeamIds)
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

    private companion object {
        const val TEAM_ID = "8185"
    }
}
