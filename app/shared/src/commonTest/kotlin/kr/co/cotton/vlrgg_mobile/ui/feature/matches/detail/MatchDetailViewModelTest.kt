package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchMap
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.matches.RelatedMatch
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailViewModelTest {

    @Test
    fun initialLoadingRequestsTheExactMatchIdentityExactlyOnce() = runViewModelTest {
        val repository = FakeMatchRepository(listOf(AppResult.Success(matchDetail())))
        val viewModel = MatchDetailViewModel(repository, MATCH_ID)

        assertEquals(MatchDetailUiState(), viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(listOf(MATCH_ID), repository.requestedMatchIds)
    }

    @Test
    fun successPreservesTheWholeMatchDetailAsContent() = runViewModelTest {
        val match = matchDetail()
        val viewModel = MatchDetailViewModel(
            matchRepository = FakeMatchRepository(listOf(AppResult.Success(match))),
            matchId = MATCH_ID,
        )

        advanceUntilIdle()

        assertEquals(
            MatchDetailUiState(
                contentState = MatchDetailContentState.Content(match),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun optionalFieldsAndEmptySectionsRemainContent() = runViewModelTest {
        val sparseMatch = matchDetail(
            relativeTimeLabel = null,
            scheduledAt = null,
            homeScore = null,
            awayScore = null,
            description = null,
            seriesFormat = null,
            maps = emptyList(),
            headToHead = emptyList(),
            pastMatches = emptyList(),
        )
        val viewModel = MatchDetailViewModel(
            matchRepository = FakeMatchRepository(listOf(AppResult.Success(sparseMatch))),
            matchId = MATCH_ID,
        )

        advanceUntilIdle()

        assertEquals(
            MatchDetailUiState(
                contentState = MatchDetailContentState.Content(sparseMatch),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun failureBecomesWholeScreenErrorWithoutRawFailureDetails() = runViewModelTest {
        val viewModel = MatchDetailViewModel(
            matchRepository = FakeMatchRepository(listOf(AppResult.Failure)),
            matchId = MATCH_ID,
        )

        advanceUntilIdle()

        assertEquals(
            MatchDetailUiState(contentState = MatchDetailContentState.Error),
            viewModel.uiState.value,
        )
        assertFalse(viewModel.uiState.value.toString().contains("exception", ignoreCase = true))
        assertFalse(viewModel.uiState.value.toString().contains("message", ignoreCase = true))
        assertFalse(viewModel.uiState.value.toString().contains("status", ignoreCase = true))
    }

    @Test
    fun retryUsesSameMatchIdentityAndTransitionsThroughLoading() = runViewModelTest {
        val match = matchDetail()
        val repository = FakeMatchRepository(
            listOf(AppResult.Failure, AppResult.Success(match)),
        )
        val viewModel = MatchDetailViewModel(repository, MATCH_ID)
        advanceUntilIdle()

        viewModel.retry()

        assertEquals(MatchDetailUiState(), viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(listOf(MATCH_ID, MATCH_ID), repository.requestedMatchIds)
        assertEquals(
            MatchDetailUiState(
                contentState = MatchDetailContentState.Content(match),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun retryOutsideErrorDoesNotCreateDuplicateRequests() = runViewModelTest {
        val loadingRepository = FakeMatchRepository(listOf(AppResult.Success(matchDetail())))
        val loadingViewModel = MatchDetailViewModel(loadingRepository, MATCH_ID)

        loadingViewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(MATCH_ID), loadingRepository.requestedMatchIds)

        val contentRepository = FakeMatchRepository(listOf(AppResult.Success(matchDetail())))
        val contentViewModel = MatchDetailViewModel(contentRepository, MATCH_ID)
        advanceUntilIdle()

        contentViewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(MATCH_ID), contentRepository.requestedMatchIds)
    }

    @Test
    fun repeatedRetryInTheSameErrorTurnStartsOnlyOneNewRequest() = runViewModelTest {
        val repository = FakeMatchRepository(
            listOf(AppResult.Failure, AppResult.Success(matchDetail())),
        )
        val viewModel = MatchDetailViewModel(repository, MATCH_ID)
        advanceUntilIdle()

        viewModel.retry()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(MATCH_ID, MATCH_ID), repository.requestedMatchIds)
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

    private fun matchDetail(
        relativeTimeLabel: String? = "IN 2 HOURS",
        scheduledAt: String? = "2026-08-30T10:00:00Z",
        homeScore: Int? = 2,
        awayScore: Int? = 1,
        description: String? = "Upper final",
        seriesFormat: String? = "bo3",
        maps: List<MatchMap> = listOf(MatchMap("Lotus", 13, 8)),
        headToHead: List<RelatedMatch> = listOf(
            RelatedMatch("previous-1", "Alpha", "Beta", 2, 0),
        ),
        pastMatches: List<RelatedMatch> = listOf(
            RelatedMatch("previous-2", "Alpha", "Gamma", 1, 2),
        ),
    ) = MatchDetail(
        id = MATCH_ID,
        status = MatchStatus.COMPLETED,
        timeLabel = "final",
        relativeTimeLabel = relativeTimeLabel,
        scheduledAt = scheduledAt,
        homeTeam = MatchTeam("Alpha", id = "team-alpha"),
        awayTeam = MatchTeam("Beta", id = "team-beta"),
        homeScore = homeScore,
        awayScore = awayScore,
        event = MatchEvent("Champions Seoul", series = "Playoffs", id = "event-1"),
        description = description,
        seriesFormat = seriesFormat,
        maps = maps,
        headToHead = headToHead,
        pastMatches = pastMatches,
    )

    private class FakeMatchRepository(
        private val detailResults: List<AppResult<MatchDetail>>,
    ) : MatchRepository {
        val requestedMatchIds = mutableListOf<String>()

        override suspend fun getUpcomingMatches(page: Int) = error("Match list is not used in detail tests")

        override suspend fun getResults(page: Int) = error("Match list is not used in detail tests")

        override suspend fun getMatchDetail(matchId: String): AppResult<MatchDetail> {
            val requestIndex = requestedMatchIds.size
            requestedMatchIds += matchId
            check(requestIndex in detailResults.indices) {
                "No Match Detail result prepared for request $requestIndex"
            }
            return detailResults[requestIndex]
        }
    }

    private companion object {
        const val MATCH_ID = "7000"
    }
}
