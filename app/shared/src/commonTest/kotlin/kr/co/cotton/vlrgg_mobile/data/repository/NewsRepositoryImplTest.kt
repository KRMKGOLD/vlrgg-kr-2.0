package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteNewsDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsSummaryDto
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsPage
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class NewsRepositoryImplTest {

    @Test
    fun getNewsPageReturnsMappedSuccess() = runTest {
        val remoteDataSource = FakeRemoteNewsDataSource(
            newsPageHandler = { page -> newsPageDto(page = page) },
        )
        val repository = NewsRepositoryImpl(remoteDataSource)

        val result = repository.getNewsPage(page = 2)

        assertEquals(2, remoteDataSource.requestedPage)
        assertEquals(
            AppResult.Success(
                NewsPage(
                    page = 2,
                    nextPage = 3,
                    items = listOf(newsSummary()),
                ),
            ),
            result,
        )
    }

    @Test
    fun getNewsArticleReturnsMappedSuccess() = runTest {
        val remoteDataSource = FakeRemoteNewsDataSource(
            newsArticleHandler = { articleId, slug ->
                newsArticleDto(reference = "$articleId/$slug")
            },
        )
        val repository = NewsRepositoryImpl(remoteDataSource)

        val result = repository.getNewsArticle(
            articleId = "101",
            slug = "champions-run",
        )

        assertEquals("101", remoteDataSource.requestedArticleId)
        assertEquals("champions-run", remoteDataSource.requestedSlug)
        assertEquals(
            AppResult.Success(
                NewsArticle(
                    articleId = "101",
                    slug = "champions-run",
                    title = "Champions run",
                    author = "Reporter",
                    publishedAt = "2026-08-09T12:00:00Z",
                    blocks = emptyList(),
                ),
            ),
            result,
        )
    }

    @Test
    fun getNewsPageConvertsNonCancellationFailure() = runTest {
        val repository = NewsRepositoryImpl(
            FakeRemoteNewsDataSource(
                newsPageHandler = { throw IllegalStateException("failure") },
            ),
        )

        assertSame(AppResult.Failure, repository.getNewsPage(page = 1))
    }

    @Test
    fun getNewsArticleConvertsNonCancellationFailure() = runTest {
        val repository = NewsRepositoryImpl(
            FakeRemoteNewsDataSource(
                newsArticleHandler = { _, _ -> throw IllegalStateException("failure") },
            ),
        )

        assertSame(
            AppResult.Failure,
            repository.getNewsArticle(
                articleId = "101",
                slug = "champions-run",
            ),
        )
    }

    @Test
    fun getNewsPageRethrowsCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = NewsRepositoryImpl(
            FakeRemoteNewsDataSource(
                newsPageHandler = { throw cancellation },
            ),
        )

        val thrown = assertFailsWith<CancellationException> {
            repository.getNewsPage(page = 1)
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun getNewsArticleRethrowsCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = NewsRepositoryImpl(
            FakeRemoteNewsDataSource(
                newsArticleHandler = { _, _ -> throw cancellation },
            ),
        )

        val thrown = assertFailsWith<CancellationException> {
            repository.getNewsArticle(
                articleId = "101",
                slug = "champions-run",
            )
        }

        assertSame(cancellation, thrown)
    }

    private fun newsPageDto(page: Int) = NewsListResponseDto(
        page = page,
        nextPage = page + 1,
        items = listOf(
            NewsSummaryDto(
                reference = "101/champions-run",
                title = "Champions run",
                author = "Reporter",
                publishedAt = "2026-08-09T12:00:00Z",
            ),
        ),
    )

    private fun newsArticleDto(reference: String) = NewsArticleResponseDto(
        reference = reference,
        title = "Champions run",
        author = "Reporter",
        publishedAt = "2026-08-09T12:00:00Z",
        blocks = emptyList(),
    )

    private fun newsSummary() = NewsSummary(
        articleId = "101",
        slug = "champions-run",
        title = "Champions run",
        author = "Reporter",
        publishedAt = "2026-08-09T12:00:00Z",
    )
}

private class FakeRemoteNewsDataSource(
    private val newsPageHandler: suspend (Int) -> NewsListResponseDto = {
        error("Unexpected getNewsPage call")
    },
    private val newsArticleHandler: suspend (String, String) -> NewsArticleResponseDto = { _, _ ->
        error("Unexpected getNewsArticle call")
    },
) : RemoteNewsDataSource {
    var requestedPage: Int? = null
        private set
    var requestedArticleId: String? = null
        private set
    var requestedSlug: String? = null
        private set

    override suspend fun getNewsPage(page: Int): NewsListResponseDto {
        requestedPage = page
        return newsPageHandler(page)
    }

    override suspend fun getNewsArticle(
        articleId: String,
        slug: String,
    ): NewsArticleResponseDto {
        requestedArticleId = articleId
        requestedSlug = slug
        return newsArticleHandler(articleId, slug)
    }
}
