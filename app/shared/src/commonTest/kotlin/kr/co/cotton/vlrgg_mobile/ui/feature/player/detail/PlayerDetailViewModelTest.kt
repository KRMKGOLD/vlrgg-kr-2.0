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
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerProfile
import kr.co.cotton.vlrgg_mobile.domain.repository.PlayerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailViewModelTest {
    @Test
    fun initialStateIsLoadingAndRequestsPlayerIdentityExactlyOnce() = runViewModelTest {
        val repository = FakePlayerRepository(listOf(AppResult.Success(playerDetail())))
        val viewModel = PlayerDetailViewModel(repository, PLAYER_ID)

        assertEquals(PlayerDetailUiState(), viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(listOf(PLAYER_ID), repository.requestedPlayerIds)
    }

    @Test
    fun successPreservesTheWholePlayerDetailAsContent() = runViewModelTest {
        val player = playerDetail()
        val viewModel = PlayerDetailViewModel(FakePlayerRepository(listOf(AppResult.Success(player))), PLAYER_ID)

        advanceUntilIdle()

        assertEquals(PlayerDetailUiState(PlayerDetailContentState.Content(player)), viewModel.uiState.value)
    }

    @Test
    fun nullableAndEmptySectionsRemainContentInsteadOfOverallError() = runViewModelTest {
        val player = playerDetail(
            currentTeam = null,
            agentStats = emptyList(),
            recentMatches = emptyList(),
        )
        val viewModel = PlayerDetailViewModel(FakePlayerRepository(listOf(AppResult.Success(player))), PLAYER_ID)

        advanceUntilIdle()

        assertEquals(PlayerDetailUiState(PlayerDetailContentState.Content(player)), viewModel.uiState.value)
    }

    @Test
    fun failureBecomesOverallErrorWithoutRawFailureDetails() = runViewModelTest {
        val viewModel = PlayerDetailViewModel(FakePlayerRepository(listOf(AppResult.Failure)), PLAYER_ID)

        advanceUntilIdle()

        assertEquals(PlayerDetailUiState(PlayerDetailContentState.Error), viewModel.uiState.value)
        assertFalse(viewModel.uiState.value.toString().contains("exception", ignoreCase = true))
        assertFalse(viewModel.uiState.value.toString().contains("http", ignoreCase = true))
    }

    @Test
    fun retryUsesSameIdentityAndOnlyStartsFromError() = runViewModelTest {
        val repository = FakePlayerRepository(listOf(AppResult.Failure, AppResult.Success(playerDetail())))
        val viewModel = PlayerDetailViewModel(repository, PLAYER_ID)
        advanceUntilIdle()

        viewModel.retry()
        assertEquals(PlayerDetailUiState(), viewModel.uiState.value)
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(PLAYER_ID, PLAYER_ID), repository.requestedPlayerIds)
    }

    @Test
    fun retryWhileLoadingOrAfterContentDoesNotStartADuplicateRequest() = runViewModelTest {
        val repository = FakePlayerRepository(listOf(AppResult.Success(playerDetail())))
        val viewModel = PlayerDetailViewModel(repository, PLAYER_ID)

        viewModel.retry()
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(PLAYER_ID), repository.requestedPlayerIds)
        assertEquals(PlayerDetailContentState.Content(playerDetail()), viewModel.uiState.value.contentState)
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

    private companion object {
        const val PLAYER_ID = "123"
    }
}
