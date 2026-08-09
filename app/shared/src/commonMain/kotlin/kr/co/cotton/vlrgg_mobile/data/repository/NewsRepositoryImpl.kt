package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteNewsDataSource
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsPage
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository

@Inject
internal class NewsRepositoryImpl(
    private val remoteNewsDataSource: RemoteNewsDataSource,
) : NewsRepository {

    override suspend fun getNewsPage(page: Int): AppResult<NewsPage> =
        wrapAsAppResult {
            remoteNewsDataSource.getNewsPage(page).toDomain()
        }

    override suspend fun getNewsArticle(
        articleId: String,
        slug: String,
    ): AppResult<NewsArticle> = wrapAsAppResult {
        remoteNewsDataSource.getNewsArticle(
            articleId = articleId,
            slug = slug,
        ).toDomain()
    }
}
