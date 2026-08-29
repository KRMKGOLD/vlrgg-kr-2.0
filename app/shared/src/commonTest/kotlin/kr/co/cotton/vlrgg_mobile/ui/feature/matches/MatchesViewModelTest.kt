package kr.co.cotton.vlrgg_mobile.ui.feature.matches

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchPage
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MatchesViewModelTest {

    @Test
    fun newViewModelInitiallyExposesUpcomingLoadingBeforeRequestRuns() = runViewModelTest {
        val repository = FakeMatchRepository(
            upcomingResults = listOf(successPage(matches = listOf(matchSummary()))),
        )

        val viewModel = MatchesViewModel(repository)

        assertEquals(MatchesTab.UPCOMING_LIVE, viewModel.uiState.value.selectedTab)
        assertEquals(MatchesFeedUiState(), viewModel.uiState.value.upcomingLive)
        assertTrue(repository.requestedUpcomingPages.isEmpty())
    }

    @Test
    fun upcomingInitialSuccessExposesDateGroupedContent() = runViewModelTest {
        val match = matchSummary()
        val groups = listOf(matchGroup("TODAY", match))
        val repository = FakeMatchRepository(
            upcomingResults = listOf(successPage(groups = groups)),
        )
        val viewModel = MatchesViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf(1), repository.requestedUpcomingPages)
        assertEquals(
            MatchesFeedUiState(
                contentState = MatchesFeedContentState.Content(groups),
            ),
            viewModel.uiState.value.upcomingLive,
        )
    }

    @Test
    fun upcomingInitialEmptyExposesEmpty() = runViewModelTest {
        val repository = FakeMatchRepository(
            upcomingResults = listOf(successPage(groups = emptyList())),
        )
        val viewModel = MatchesViewModel(repository)

        advanceUntilIdle()

        assertEquals(
            MatchesFeedUiState(contentState = MatchesFeedContentState.Empty),
            viewModel.uiState.value.upcomingLive,
        )
    }

    @Test
    fun upcomingInitialFailureExposesErrorAndRetryRequestsFirstPage() = runViewModelTest {
        val recovered = matchSummary(id = "recovered")
        val repository = FakeMatchRepository(
            upcomingResults = listOf(
                AppResult.Failure,
                successPage(matches = listOf(recovered)),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            MatchesFeedUiState(contentState = MatchesFeedContentState.Error),
            viewModel.uiState.value.upcomingLive,
        )

        viewModel.retryInitial()

        assertEquals(MatchesFeedUiState(), viewModel.uiState.value.upcomingLive)
        advanceUntilIdle()
        assertEquals(listOf(1, 1), repository.requestedUpcomingPages)
        assertEquals(listOf("recovered"), viewModel.uiState.value.upcomingLive.matchIds())
    }

    @Test
    fun refreshSuccessReplacesExistingGroupsWithFirstPage() = runViewModelTest {
        val repository = FakeMatchRepository(
            upcomingResults = listOf(
                successPage(matches = listOf(matchSummary(id = "existing"))),
                successPage(matches = listOf(matchSummary(id = "refreshed"))),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.upcomingLive.isRefreshing)
        assertEquals(MatchesFeedContentState.Loading, viewModel.uiState.value.upcomingLive.contentState)
        advanceUntilIdle()
        assertEquals(listOf(1, 1), repository.requestedUpcomingPages)
        assertEquals(listOf("refreshed"), viewModel.uiState.value.upcomingLive.matchIds())
        assertFalse(viewModel.uiState.value.upcomingLive.isRefreshing)
    }

    @Test
    fun refreshFailureClearsExistingGroupsAndExposesInitialError() = runViewModelTest {
        val repository = FakeMatchRepository(
            upcomingResults = listOf(
                successPage(matches = listOf(matchSummary(id = "existing"))),
                AppResult.Failure,
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            MatchesFeedUiState(contentState = MatchesFeedContentState.Error),
            viewModel.uiState.value.upcomingLive,
        )
    }

    @Test
    fun loadMoreAfterRefreshFailureMakesNoRequestAndPreservesInitialError() = runViewModelTest {
        val repository = FakeMatchRepository(
            upcomingResults = listOf(
                successPage(matches = listOf(matchSummary(id = "existing"))),
                AppResult.Failure,
                successPage(matches = listOf(matchSummary(id = "unexpected"))),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        val refreshFailureState = viewModel.uiState.value.upcomingLive

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 1), repository.requestedUpcomingPages)
        assertEquals(refreshFailureState, viewModel.uiState.value.upcomingLive)
        assertEquals(MatchesFeedContentState.Error, viewModel.uiState.value.upcomingLive.contentState)
    }

    @Test
    fun loadMoreSuccessMergesDateGroupsAndDeduplicatesStableMatchIds() = runViewModelTest {
        val first = matchSummary(id = "first")
        val duplicate = first.copy(timeLabel = "duplicate must be discarded")
        val second = matchSummary(id = "second")
        val third = matchSummary(id = "third")
        val repository = FakeMatchRepository(
            upcomingResults = listOf(
                successPage(groups = listOf(matchGroup("TODAY", first))),
                successPage(
                    page = 2,
                    groups = listOf(
                        MatchDateGroup("TODAY", listOf(duplicate, second)),
                        MatchDateGroup("TOMORROW", listOf(first, third)),
                    ),
                ),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()

        assertTrue(viewModel.uiState.value.upcomingLive.isLoadingMore)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), repository.requestedUpcomingPages)
        assertEquals(
            listOf(
                MatchDateGroup("TODAY", listOf(first, second)),
                MatchDateGroup("TOMORROW", listOf(third)),
            ),
            viewModel.uiState.value.upcomingLive.contentGroups(),
        )
    }

    @Test
    fun loadMoreFailureKeepsGroupsAndFooterRetryRequestsSamePage() = runViewModelTest {
        val existing = matchSummary(id = "existing")
        val recovered = matchSummary(id = "recovered")
        val repository = FakeMatchRepository(
            upcomingResults = listOf(
                successPage(matches = listOf(existing)),
                AppResult.Failure,
                successPage(page = 2, matches = listOf(recovered)),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("existing"), viewModel.uiState.value.upcomingLive.matchIds())
        assertTrue(viewModel.uiState.value.upcomingLive.hasPaginationError)

        viewModel.retryLoadMore()

        assertTrue(viewModel.uiState.value.upcomingLive.isLoadingMore)
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), repository.requestedUpcomingPages)
        assertEquals(listOf("existing", "recovered"), viewModel.uiState.value.upcomingLive.matchIds())
        assertFalse(viewModel.uiState.value.upcomingLive.hasPaginationError)
    }

    @Test
    fun successfulEmptyPaginationPageStopsFurtherRequests() = runViewModelTest {
        val repository = FakeMatchRepository(
            upcomingResults = listOf(
                successPage(matches = listOf(matchSummary())),
                successPage(page = 2, groups = emptyList()),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.requestedUpcomingPages)
    }

    @Test
    fun duplicateOnlyPaginationPageStopsFurtherRequestsAndKeepsVisibleIdsDeduplicated() = runViewModelTest {
        val first = matchSummary(id = "first")
        val repository = FakeMatchRepository(
            upcomingResults = listOf(
                successPage(matches = listOf(first)),
                successPage(page = 2, matches = listOf(first.copy(timeLabel = "duplicate"))),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.requestedUpcomingPages)
        assertEquals(listOf("first"), viewModel.uiState.value.upcomingLive.matchIds())
    }

    @Test
    fun concurrentLoadMoreRequestsRepositoryOnce() = runViewModelTest {
        val pendingLoadMore = CompletableDeferred<AppResult<MatchPage>>()
        val repository = FakeMatchRepository { feed, page, callIndex ->
            check(feed == Feed.UPCOMING)
            when (callIndex) {
                0 -> successPage(matches = listOf(matchSummary(id = "first")))
                1 -> pendingLoadMore.await()
                else -> error("Unexpected request for page $page")
            }
        }
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        assertEquals(listOf(1, 2), repository.requestedUpcomingPages)
        pendingLoadMore.complete(successPage(page = 2, matches = listOf(matchSummary(id = "second"))))
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), viewModel.uiState.value.upcomingLive.matchIds())
    }

    @Test
    fun refreshCancelsStaleInitialRequestBeforeApplyingFreshResult() = runViewModelTest {
        val pendingInitial = CompletableDeferred<AppResult<MatchPage>>()
        var initialWasCancelled = false
        val repository = FakeMatchRepository { feed, page, callIndex ->
            check(feed == Feed.UPCOMING)
            when (callIndex) {
                0 -> try {
                    pendingInitial.await()
                } catch (error: CancellationException) {
                    initialWasCancelled = true
                    throw error
                }
                1 -> successPage(matches = listOf(matchSummary(id = "fresh")))
                else -> error("Unexpected request for page $page")
            }
        }
        val viewModel = MatchesViewModel(repository)
        runCurrent()

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(initialWasCancelled)
        assertEquals(listOf(1, 1), repository.requestedUpcomingPages)
        assertEquals(listOf("fresh"), viewModel.uiState.value.upcomingLive.matchIds())
    }

    @Test
    fun refreshCancelsStaleLoadMoreRequestBeforeApplyingFreshResult() = runViewModelTest {
        val pendingLoadMore = CompletableDeferred<AppResult<MatchPage>>()
        var loadMoreWasCancelled = false
        val repository = FakeMatchRepository { feed, page, callIndex ->
            check(feed == Feed.UPCOMING)
            when (callIndex) {
                0 -> successPage(matches = listOf(matchSummary(id = "existing")))
                1 -> try {
                    pendingLoadMore.await()
                } catch (error: CancellationException) {
                    loadMoreWasCancelled = true
                    throw error
                }
                2 -> successPage(matches = listOf(matchSummary(id = "fresh")))
                else -> error("Unexpected request for page $page")
            }
        }
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        runCurrent()

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(loadMoreWasCancelled)
        assertEquals(listOf(1, 2, 1), repository.requestedUpcomingPages)
        assertEquals(listOf("fresh"), viewModel.uiState.value.upcomingLive.matchIds())
    }

    @Test
    fun repositoryCancellationIsNotConvertedToAnErrorState() = runViewModelTest {
        val repository = FakeMatchRepository { feed, _, _ ->
            check(feed == Feed.UPCOMING)
            throw CancellationException("cancelled upstream")
        }
        val viewModel = MatchesViewModel(repository)

        advanceUntilIdle()

        assertEquals(MatchesFeedUiState(), viewModel.uiState.value.upcomingLive)
    }

    @Test
    fun restoredResultsTabLoadsOnlyResultsFirstPage() = runViewModelTest {
        val resultMatch = matchSummary(id = "result").copy(status = MatchStatus.COMPLETED)
        val repository = FakeMatchRepository(
            resultsResults = listOf(
                successPage(
                    matches = listOf(resultMatch),
                    category = MatchListCategory.RESULTS,
                ),
            ),
        )

        val viewModel = MatchesViewModel(
            repository,
            SavedStateHandle(
                mapOf(MATCHES_SELECTED_TAB_KEY to MatchesTab.RESULTS.savedStateId),
            ),
        )

        assertEquals(MatchesTab.RESULTS, viewModel.uiState.value.selectedTab)
        assertTrue(repository.requestedUpcomingPages.isEmpty())
        advanceUntilIdle()
        assertTrue(repository.requestedUpcomingPages.isEmpty())
        assertEquals(listOf(1), repository.requestedResultsPages)
        assertEquals(listOf("result"), viewModel.uiState.value.results.matchIds())
    }

    @Test
    fun selectingTabWritesStableIdToSavedState() = runViewModelTest {
        val savedStateHandle = SavedStateHandle()
        val repository = FakeMatchRepository(
            upcomingResults = listOf(successPage(matches = listOf(matchSummary()))),
            resultsResults = listOf(
                successPage(
                    matches = listOf(matchSummary(id = "result")),
                    category = MatchListCategory.RESULTS,
                ),
            ),
        )
        val viewModel = MatchesViewModel(repository, savedStateHandle)
        advanceUntilIdle()

        viewModel.selectTab(MatchesTab.RESULTS)

        assertEquals(
            MatchesTab.RESULTS.savedStateId,
            savedStateHandle.get<String>(MATCHES_SELECTED_TAB_KEY),
        )
        advanceUntilIdle()
        viewModel.selectTab(MatchesTab.UPCOMING_LIVE)
        assertEquals(
            MatchesTab.UPCOMING_LIVE.savedStateId,
            savedStateHandle.get<String>(MATCHES_SELECTED_TAB_KEY),
        )
    }

    @Test
    fun unknownRestoredTabFallsBackToUpcomingAndRepairsSavedState() = runViewModelTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(MATCHES_SELECTED_TAB_KEY to "unknown-tab"),
        )
        val repository = FakeMatchRepository(
            upcomingResults = listOf(successPage(matches = listOf(matchSummary()))),
        )

        val viewModel = MatchesViewModel(repository, savedStateHandle)

        assertEquals(MatchesTab.UPCOMING_LIVE, viewModel.uiState.value.selectedTab)
        assertEquals(
            MatchesTab.UPCOMING_LIVE.savedStateId,
            savedStateHandle.get<String>(MATCHES_SELECTED_TAB_KEY),
        )
        advanceUntilIdle()
        assertEquals(listOf(1), repository.requestedUpcomingPages)
        assertTrue(repository.requestedResultsPages.isEmpty())
    }

    @Test
    fun tabSwitchLoadsResultsOnceAndKeepsBothFeedsData() = runViewModelTest {
        val upcoming = matchSummary(id = "upcoming")
        val result = matchSummary(id = "result").copy(status = MatchStatus.COMPLETED)
        val repository = FakeMatchRepository(
            upcomingResults = listOf(successPage(matches = listOf(upcoming))),
            resultsResults = listOf(
                successPage(
                    matches = listOf(result),
                    category = MatchListCategory.RESULTS,
                ),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.selectTab(MatchesTab.RESULTS)
        advanceUntilIdle()
        viewModel.selectTab(MatchesTab.UPCOMING_LIVE)
        viewModel.selectTab(MatchesTab.RESULTS)
        advanceUntilIdle()

        assertEquals(MatchesTab.RESULTS, viewModel.uiState.value.selectedTab)
        assertEquals(listOf("upcoming"), viewModel.uiState.value.upcomingLive.matchIds())
        assertEquals(listOf("result"), viewModel.uiState.value.results.matchIds())
        assertEquals(listOf(1), repository.requestedUpcomingPages)
        assertEquals(listOf(1), repository.requestedResultsPages)
    }

    @Test
    fun resultsLoadingAndFailureDoNotChangeLoadedUpcomingFeed() = runViewModelTest {
        val pendingResults = CompletableDeferred<AppResult<MatchPage>>()
        val repository = FakeMatchRepository { feed, _, _ ->
            when (feed) {
                Feed.UPCOMING -> successPage(matches = listOf(matchSummary(id = "upcoming")))
                Feed.RESULTS -> pendingResults.await()
            }
        }
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()

        viewModel.selectTab(MatchesTab.RESULTS)
        runCurrent()

        assertEquals(listOf("upcoming"), viewModel.uiState.value.upcomingLive.matchIds())
        assertEquals(MatchesFeedUiState(), viewModel.uiState.value.results)
        pendingResults.complete(AppResult.Failure)
        advanceUntilIdle()
        assertEquals(listOf("upcoming"), viewModel.uiState.value.upcomingLive.matchIds())
        assertEquals(
            MatchesFeedUiState(contentState = MatchesFeedContentState.Error),
            viewModel.uiState.value.results,
        )
    }

    @Test
    fun resultsPaginationFailureAndRetryRemainIndependentFromUpcoming() = runViewModelTest {
        val upcoming = matchSummary(id = "upcoming")
        val firstResult = matchSummary(id = "result-1").copy(status = MatchStatus.COMPLETED)
        val secondResult = matchSummary(id = "result-2").copy(status = MatchStatus.COMPLETED)
        val repository = FakeMatchRepository(
            upcomingResults = listOf(successPage(matches = listOf(upcoming))),
            resultsResults = listOf(
                successPage(
                    matches = listOf(firstResult),
                    category = MatchListCategory.RESULTS,
                ),
                AppResult.Failure,
                successPage(
                    page = 2,
                    matches = listOf(secondResult),
                    category = MatchListCategory.RESULTS,
                ),
            ),
        )
        val viewModel = MatchesViewModel(repository)
        advanceUntilIdle()
        viewModel.selectTab(MatchesTab.RESULTS)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("upcoming"), viewModel.uiState.value.upcomingLive.matchIds())
        assertFalse(viewModel.uiState.value.upcomingLive.hasPaginationError)
        assertEquals(listOf("result-1"), viewModel.uiState.value.results.matchIds())
        assertTrue(viewModel.uiState.value.results.hasPaginationError)

        viewModel.retryLoadMore()
        advanceUntilIdle()

        assertEquals(listOf("upcoming"), viewModel.uiState.value.upcomingLive.matchIds())
        assertEquals(listOf("result-1", "result-2"), viewModel.uiState.value.results.matchIds())
        assertEquals(listOf(1), repository.requestedUpcomingPages)
        assertEquals(listOf(1, 2, 2), repository.requestedResultsPages)
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

    private fun successPage(
        page: Int = 1,
        groups: List<MatchDateGroup>? = null,
        matches: List<MatchSummary> = emptyList(),
        category: MatchListCategory = MatchListCategory.UPCOMING,
    ) = AppResult.Success(
        MatchPage(
            category = category,
            page = page,
            groups = groups ?: listOf(matchGroup(DEFAULT_DATE_LABEL, *matches.toTypedArray())),
        ),
    )

    private fun matchGroup(
        dateLabel: String,
        vararg matches: MatchSummary,
    ) = MatchDateGroup(
        dateLabel = dateLabel,
        matches = matches.toList(),
    )

    private fun matchSummary(
        id: String = "match-id",
    ) = MatchSummary(
        id = id,
        status = MatchStatus.UPCOMING,
        timeLabel = "10:00 AM",
        relativeTimeLabel = "in 1h",
        homeTeam = MatchTeam(name = "Home", id = null),
        awayTeam = MatchTeam(name = "Away", id = null),
        homeScore = null,
        awayScore = null,
        event = MatchEvent(name = "VCT", series = "Regular Season", id = null),
    )

    private fun MatchesFeedUiState.contentGroups(): List<MatchDateGroup> =
        (contentState as MatchesFeedContentState.Content).groups

    private fun MatchesFeedUiState.matchIds(): List<String> =
        contentGroups().flatMap { group -> group.matches }.map { match -> match.id }

    private enum class Feed {
        UPCOMING,
        RESULTS,
    }

    private class FakeMatchRepository(
        private val resultProvider: suspend (feed: Feed, page: Int, callIndex: Int) -> AppResult<MatchPage>,
    ) : MatchRepository {

        constructor(
            upcomingResults: List<AppResult<MatchPage>> = emptyList(),
            resultsResults: List<AppResult<MatchPage>> = emptyList(),
        ) : this(
            resultProvider = { feed, page, callIndex ->
                val results = when (feed) {
                    Feed.UPCOMING -> upcomingResults
                    Feed.RESULTS -> resultsResults
                }
                check(callIndex in results.indices) {
                    "No ${feed.name.lowercase()} result prepared for page $page"
                }
                results[callIndex]
            },
        )

        val requestedUpcomingPages = mutableListOf<Int>()
        val requestedResultsPages = mutableListOf<Int>()

        override suspend fun getUpcomingMatches(page: Int): AppResult<MatchPage> {
            val callIndex = requestedUpcomingPages.size
            requestedUpcomingPages += page
            return resultProvider(Feed.UPCOMING, page, callIndex)
        }

        override suspend fun getResults(page: Int): AppResult<MatchPage> {
            val callIndex = requestedResultsPages.size
            requestedResultsPages += page
            return resultProvider(Feed.RESULTS, page, callIndex)
        }

        override suspend fun getMatchDetail(matchId: String): AppResult<MatchDetail> =
            error("Match detail is not used")
    }

    private companion object {
        const val DEFAULT_DATE_LABEL = "TODAY"
    }
}
