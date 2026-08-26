package kr.co.cotton.vlrgg_mobile.ui.feature.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResults
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kr.co.cotton.vlrgg_mobile.domain.repository.SearchRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Test
    fun validationAcceptsAtMostThirtyCharactersAndRejectsLongerQueries() {
        assertTrue(isSearchQueryValid("a".repeat(MAX_SEARCH_QUERY_LENGTH)))
        assertFalse(isSearchQueryValid("a".repeat(MAX_SEARCH_QUERY_LENGTH + 1)))
    }

    @Test
    fun typingLimitsInputAndNeverRequestsUntilExplicitSubmit() = runViewModelTest {
        val repository = FakeSearchRepository(listOf(AppResult.Success(results())))
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange(" T1!" + "x".repeat(40))

        assertEquals(" T1!" + "x".repeat(26), viewModel.uiState.value.query)
        assertEquals(SearchContentState.Initial, viewModel.uiState.value.contentState)
        assertEquals(0, repository.requestCount)
    }

    @Test
    fun blankAndSymbolOnlySubmitNeverRequest() = runViewModelTest {
        val repository = FakeSearchRepository(listOf(AppResult.Success(results())))
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("  !?  ")
        viewModel.submit()
        viewModel.onQueryChange("   ")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.requestCount)
        assertEquals(SearchContentState.Initial, viewModel.uiState.value.contentState)
    }

    @Test
    fun submitTrimsQueryAndExposesPopulatedOrEmptyState() = runViewModelTest {
        val repository = FakeSearchRepository(
            listOf(
                AppResult.Success(results()),
                AppResult.Success(SearchResults("empty", emptyList())),
            ),
        )
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("  T1  ")
        viewModel.submit()
        assertEquals(SearchContentState.Loading, viewModel.uiState.value.contentState)
        advanceUntilIdle()

        assertEquals("T1", repository.queries.single())
        assertEquals("T1", viewModel.uiState.value.query)
        assertEquals(SearchContentState.Populated(results().items), viewModel.uiState.value.contentState)

        viewModel.onQueryChange("empty")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(SearchContentState.Empty, viewModel.uiState.value.contentState)
    }

    @Test
    fun identicalSubmittedQueryIsDedupedButRetryResubmitsFailureQuery() = runViewModelTest {
        val repository = FakeSearchRepository(
            listOf(AppResult.Failure, AppResult.Success(results())),
        )
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("T1")
        viewModel.submit()
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.requestCount)
        assertEquals(SearchContentState.Error, viewModel.uiState.value.contentState)

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.requestCount)
        assertEquals(SearchContentState.Populated(results().items), viewModel.uiState.value.contentState)
    }

    @Test
    fun clearCancelsRequestAndReturnsToInitialWithoutAllowingStaleResponse() = runViewModelTest {
        val stale = CompletableDeferred<AppResult<SearchResults>>()
        val repository = FakeSearchRepository { withContext(NonCancellable) { stale.await() } }
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("T1")
        viewModel.submit()
        runCurrent()
        viewModel.clear()
        stale.complete(AppResult.Success(results()))
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.query)
        assertEquals(SearchContentState.Initial, viewModel.uiState.value.contentState)
    }

    @Test
    fun newerSubmissionWinsOverCancelledNonCooperativeResponse() = runViewModelTest {
        val stale = CompletableDeferred<AppResult<SearchResults>>()
        val fresh = SearchResults("GEN", listOf(TeamSearchResult("4", "GEN", null)))
        val repository = FakeSearchRepository { callIndex ->
            when (callIndex) {
                0 -> withContext(NonCancellable) { stale.await() }
                1 -> AppResult.Success(fresh)
                else -> error("Unexpected request")
            }
        }
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("T1")
        viewModel.submit()
        runCurrent()
        viewModel.onQueryChange("GEN")
        viewModel.submit()
        advanceUntilIdle()
        stale.complete(AppResult.Success(results()))
        advanceUntilIdle()

        assertEquals(SearchContentState.Populated(fresh.items), viewModel.uiState.value.contentState)
    }

    private fun results() = SearchResults(
        query = "T1",
        items = listOf(TeamSearchResult("3", "T1", "Pacific")),
    )

    private fun runViewModelTest(testBody: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeSearchRepository(
        private val resultProvider: suspend (callIndex: Int) -> AppResult<SearchResults>,
    ) : SearchRepository {
        constructor(results: List<AppResult<SearchResults>>) : this(
            resultProvider = { callIndex ->
                check(callIndex in results.indices) { "No result prepared for search request" }
                results[callIndex]
            },
        )

        var requestCount = 0
            private set
        val queries = mutableListOf<String>()

        override suspend fun getSearch(query: String): AppResult<SearchResults> {
            queries += query
            return resultProvider(requestCount++)
        }
    }
}
