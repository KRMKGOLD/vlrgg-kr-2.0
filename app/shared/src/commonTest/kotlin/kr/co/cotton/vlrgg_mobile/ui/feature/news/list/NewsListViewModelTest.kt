package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

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
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsPage
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NewsListViewModelTest {

    @Test
    fun newViewModelInitiallyExposesLoading() = runViewModelTest {
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(newsSummary()))),
            ),
        )

        val viewModel = NewsListViewModel(repository)

        assertEquals(NewsListUiState(), viewModel.uiState.value)
        assertTrue(repository.requestedPages.isEmpty())
    }

    @Test
    fun initialPageSuccessExposesContent() = runViewModelTest {
        val item = newsSummary()
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(item))),
            ),
        )
        val viewModel = NewsListViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf(1), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(item)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun initialEmptyPageExposesEmpty() = runViewModelTest {
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = emptyList(), nextPage = null)),
            ),
        )
        val viewModel = NewsListViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf(1), repository.requestedPages)
        assertEquals(
            NewsListUiState(contentState = NewsListContentState.Empty),
            viewModel.uiState.value,
        )
    }

    @Test
    fun initialFailureExposesErrorAndRetryRequestsFirstPageAgain() = runViewModelTest {
        val item = newsSummary(articleId = "retry", slug = "retry-news")
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Failure,
                AppResult.Success(newsPage(items = listOf(item))),
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            NewsListUiState(contentState = NewsListContentState.Error),
            viewModel.uiState.value,
        )

        viewModel.retryInitial()

        assertEquals(NewsListUiState(), viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(listOf(1, 1), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(item)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun retryInitialOutsideErrorDoesNotRequestAgain() = runViewModelTest {
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(newsSummary()))),
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.retryInitial()
        advanceUntilIdle()

        assertEquals(listOf(1), repository.requestedPages)
    }

    @Test
    fun initialPageSuppressesDuplicateArticleReferences() = runViewModelTest {
        val first = newsSummary(
            articleId = "same-id",
            slug = "same-slug",
            title = "First title",
        )
        val duplicate = first.copy(title = "Duplicate title")
        val distinct = newsSummary(articleId = "other-id", slug = "other-slug")
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(first, duplicate, distinct))),
            ),
        )
        val viewModel = NewsListViewModel(repository)

        advanceUntilIdle()

        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(first, distinct)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun loadMoreSuccessAppendsItems() = runViewModelTest {
        val first = newsSummary(articleId = "first-id", slug = "first-slug")
        val second = newsSummary(articleId = "second-id", slug = "second-slug")
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(first), nextPage = 2)),
                AppResult.Success(newsPage(page = 2, items = listOf(second), nextPage = null)),
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()

        assertTrue(viewModel.uiState.value.isLoadingMore)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(first, second)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun loadMoreFailureKeepsItemsAndExposesPaginationError() = runViewModelTest {
        val first = newsSummary()
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(first), nextPage = 2)),
                AppResult.Failure,
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(first)),
                hasPaginationError = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun retryLoadMoreRequestsFailedPageAgain() = runViewModelTest {
        val first = newsSummary(articleId = "first-id", slug = "first-slug")
        val second = newsSummary(articleId = "second-id", slug = "second-slug")
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(first), nextPage = 2)),
                AppResult.Failure,
                AppResult.Success(newsPage(page = 2, items = listOf(second), nextPage = null)),
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        viewModel.retryLoadMore()

        assertTrue(viewModel.uiState.value.isLoadingMore)
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(first, second)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun concurrentLoadMoreRequestsRepositoryOnce() = runViewModelTest {
        val first = newsSummary(articleId = "first-id", slug = "first-slug")
        val second = newsSummary(articleId = "second-id", slug = "second-slug")
        val pendingLoadMore = CompletableDeferred<AppResult<NewsPage>>()
        val repository = FakeNewsRepository { page, callIndex ->
            when (callIndex) {
                0 -> AppResult.Success(newsPage(items = listOf(first), nextPage = 2))
                1 -> pendingLoadMore.await()
                else -> error("Unexpected request for page $page")
            }
        }
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        assertEquals(listOf(1, 2), repository.requestedPages)
        pendingLoadMore.complete(
            AppResult.Success(newsPage(page = 2, items = listOf(second), nextPage = null)),
        )
        advanceUntilIdle()
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(first, second)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun loadMoreWithNoNextPageDoesNotRequestRepository() = runViewModelTest {
        val first = newsSummary()
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(first), nextPage = null)),
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(first)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun loadMoreSuppressesDuplicateArticleReferences() = runViewModelTest {
        val first = newsSummary(
            articleId = "same-id",
            slug = "same-slug",
            title = "First title",
        )
        val duplicate = first.copy(title = "Duplicate title")
        val distinct = newsSummary(articleId = "other-id", slug = "other-slug")
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(first), nextPage = 2)),
                AppResult.Success(
                    newsPage(
                        page = 2,
                        items = listOf(duplicate, distinct),
                        nextPage = null,
                    ),
                ),
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(first, distinct)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun refreshSuccessReplacesExistingItemsWithFirstPage() = runViewModelTest {
        val existing = newsSummary(articleId = "existing-id", slug = "existing-slug")
        val refreshed = newsSummary(articleId = "refreshed-id", slug = "refreshed-slug")
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(existing), nextPage = 2)),
                AppResult.Success(newsPage(items = listOf(refreshed), nextPage = 3)),
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.refresh()

        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Loading,
                isRefreshing = true,
            ),
            viewModel.uiState.value,
        )
        advanceUntilIdle()
        assertEquals(listOf(1, 1), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(refreshed)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun refreshFailureExposesInitialError() = runViewModelTest {
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(newsSummary()), nextPage = 2)),
                AppResult.Failure,
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(1, 1), repository.requestedPages)
        assertEquals(
            NewsListUiState(contentState = NewsListContentState.Error),
            viewModel.uiState.value,
        )
    }

    @Test
    fun refreshCancelsInFlightLoadMoreAndRejectsItsLateResult() = runViewModelTest {
        val existing = newsSummary(articleId = "existing-id", slug = "existing-slug")
        val stale = newsSummary(articleId = "stale-id", slug = "stale-slug")
        val refreshed = newsSummary(articleId = "refreshed-id", slug = "refreshed-slug")
        val pendingLoadMore = CompletableDeferred<AppResult<NewsPage>>()
        val repository = FakeNewsRepository { page, callIndex ->
            when (callIndex) {
                0 -> AppResult.Success(newsPage(items = listOf(existing), nextPage = 2))
                1 -> pendingLoadMore.await()
                2 -> AppResult.Success(newsPage(items = listOf(refreshed), nextPage = 2))
                else -> error("Unexpected request for page $page")
            }
        }
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        runCurrent()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 1), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(refreshed)),
            ),
            viewModel.uiState.value,
        )

        pendingLoadMore.complete(
            AppResult.Success(newsPage(page = 2, items = listOf(stale), nextPage = null)),
        )
        advanceUntilIdle()
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(refreshed)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun concurrentRefreshRequestsFirstPageOnce() = runViewModelTest {
        val existing = newsSummary(articleId = "existing-id", slug = "existing-slug")
        val refreshed = newsSummary(articleId = "refreshed-id", slug = "refreshed-slug")
        val pendingRefresh = CompletableDeferred<AppResult<NewsPage>>()
        val repository = FakeNewsRepository { page, callIndex ->
            when (callIndex) {
                0 -> AppResult.Success(newsPage(items = listOf(existing), nextPage = 2))
                1 -> pendingRefresh.await()
                else -> error("Unexpected request for page $page")
            }
        }
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(1, 1), repository.requestedPages)
        pendingRefresh.complete(
            AppResult.Success(newsPage(items = listOf(refreshed), nextPage = null)),
        )
        advanceUntilIdle()
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(refreshed)),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun loadMoreAfterRefreshUsesRefreshedNextPage() = runViewModelTest {
        val existing = newsSummary(articleId = "existing-id", slug = "existing-slug")
        val refreshed = newsSummary(articleId = "refreshed-id", slug = "refreshed-slug")
        val next = newsSummary(articleId = "next-id", slug = "next-slug")
        val repository = FakeNewsRepository(
            pageResults = listOf(
                AppResult.Success(newsPage(items = listOf(existing), nextPage = 9)),
                AppResult.Success(newsPage(items = listOf(refreshed), nextPage = 2)),
                AppResult.Success(newsPage(page = 2, items = listOf(next), nextPage = null)),
            ),
        )
        val viewModel = NewsListViewModel(repository)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 1, 2), repository.requestedPages)
        assertEquals(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(refreshed, next)),
            ),
            viewModel.uiState.value,
        )
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

    private fun newsPage(
        page: Int = 1,
        items: List<NewsSummary>,
        nextPage: Int? = 2,
    ) = NewsPage(
        page = page,
        nextPage = nextPage,
        items = items,
    )

    private fun newsSummary(
        articleId: String = "article-id",
        slug: String = "article-slug",
        title: String = "News title",
    ) = NewsSummary(
        articleId = articleId,
        slug = slug,
        title = title,
        author = "Author",
        publishedAt = "2026-08-10",
    )

    private class FakeNewsRepository(
        private val pageResultProvider: suspend (page: Int, callIndex: Int) -> AppResult<NewsPage>,
    ) : NewsRepository {

        constructor(pageResults: List<AppResult<NewsPage>>) : this(
            pageResultProvider = { page, callIndex ->
                check(callIndex in pageResults.indices) {
                    "No result prepared for page $page"
                }
                pageResults[callIndex]
            },
        )

        val requestedPages = mutableListOf<Int>()

        override suspend fun getNewsPage(page: Int): AppResult<NewsPage> {
            val callIndex = requestedPages.size
            requestedPages += page
            return pageResultProvider(page, callIndex)
        }

        override suspend fun getNewsArticle(
            articleId: String,
            slug: String,
        ): AppResult<NewsArticle> = error("News article is not used in list ViewModel tests")
    }
}
