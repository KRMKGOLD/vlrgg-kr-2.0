package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleBlock
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsPage
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NewsDetailViewModelTest {

    @Test
    fun newViewModelInitiallyExposesLoadingWithoutRequestingRepository() = runViewModelTest {
        val repository = FakeNewsRepository(
            articleResults = listOf(AppResult.Success(newsArticle())),
        )

        val viewModel = NewsDetailViewModel(
            newsRepository = repository,
            articleId = "article-id",
            slug = "article-slug",
        )

        assertEquals(NewsDetailUiState(), viewModel.uiState.value)
        assertEquals(emptyList(), repository.requestedArticles)
    }

    @Test
    fun initialSuccessRequestsIdentityAndExposesContent() = runViewModelTest {
        val article = newsArticle(articleId = "success-id", slug = "success-slug")
        val repository = FakeNewsRepository(
            articleResults = listOf(AppResult.Success(article)),
        )
        val viewModel = NewsDetailViewModel(
            newsRepository = repository,
            articleId = article.articleId,
            slug = article.slug,
        )

        advanceUntilIdle()

        assertEquals(
            listOf(ArticleRequest(article.articleId, article.slug)),
            repository.requestedArticles,
        )
        assertEquals(
            NewsDetailUiState(NewsDetailContentState.Content(article)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun initialSuccessWithNoBlocksExposesEmpty() = runViewModelTest {
        val article = newsArticle(blocks = emptyList())
        val repository = FakeNewsRepository(
            articleResults = listOf(AppResult.Success(article)),
        )
        val viewModel = NewsDetailViewModel(
            newsRepository = repository,
            articleId = article.articleId,
            slug = article.slug,
        )

        advanceUntilIdle()

        assertEquals(
            NewsDetailUiState(NewsDetailContentState.Empty(article)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun initialFailureExposesError() = runViewModelTest {
        val repository = FakeNewsRepository(
            articleResults = listOf(AppResult.Failure),
        )
        val viewModel = NewsDetailViewModel(
            newsRepository = repository,
            articleId = "failed-id",
            slug = "failed-slug",
        )

        advanceUntilIdle()

        assertEquals(
            NewsDetailUiState(NewsDetailContentState.Error),
            viewModel.uiState.value,
        )
    }

    @Test
    fun retryAfterErrorImmediatelyExposesLoadingAndRequestsSameIdentity() = runViewModelTest {
        val article = newsArticle(articleId = "retry-id", slug = "retry-slug")
        val repository = FakeNewsRepository(
            articleResults = listOf(
                AppResult.Failure,
                AppResult.Success(article),
            ),
        )
        val viewModel = NewsDetailViewModel(
            newsRepository = repository,
            articleId = article.articleId,
            slug = article.slug,
        )
        advanceUntilIdle()
        assertEquals(
            NewsDetailUiState(NewsDetailContentState.Error),
            viewModel.uiState.value,
        )

        viewModel.retry()

        assertEquals(NewsDetailUiState(), viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(
            listOf(
                ArticleRequest(article.articleId, article.slug),
                ArticleRequest(article.articleId, article.slug),
            ),
            repository.requestedArticles,
        )
        assertEquals(
            NewsDetailUiState(NewsDetailContentState.Content(article)),
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

    private fun newsArticle(
        articleId: String = "article-id",
        slug: String = "article-slug",
        blocks: List<NewsArticleBlock> = listOf(
            NewsArticleBlock.Paragraph(
                content = listOf(NewsArticleInline.Text("Article content")),
            ),
        ),
    ) = NewsArticle(
        articleId = articleId,
        slug = slug,
        title = "News title",
        author = "Author",
        publishedAt = "2026-08-10",
        blocks = blocks,
    )

    private data class ArticleRequest(
        val articleId: String,
        val slug: String,
    )

    private class FakeNewsRepository(
        private val articleResults: List<AppResult<NewsArticle>>,
    ) : NewsRepository {
        val requestedArticles = mutableListOf<ArticleRequest>()

        override suspend fun getNewsPage(page: Int): AppResult<NewsPage> =
            error("News page is not used in detail ViewModel tests")

        override suspend fun getNewsArticle(
            articleId: String,
            slug: String,
        ): AppResult<NewsArticle> {
            val callIndex = requestedArticles.size
            requestedArticles += ArticleRequest(articleId, slug)
            check(callIndex in articleResults.indices) {
                "No result prepared for article $articleId/$slug"
            }
            return articleResults[callIndex]
        }
    }
}
