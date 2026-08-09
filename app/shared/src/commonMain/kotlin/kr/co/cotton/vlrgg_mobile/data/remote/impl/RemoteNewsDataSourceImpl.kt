package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.appendPathSegments
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteNewsDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsListResponseDto

@Inject
internal class RemoteNewsDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteNewsDataSource {

    override suspend fun getNewsPage(page: Int): NewsListResponseDto =
        httpClient.get(NEWS_PATH) {
            parameter("page", page)
        }.body()

    override suspend fun getNewsArticle(
        articleId: String,
        slug: String,
    ): NewsArticleResponseDto =
        httpClient.get(NEWS_PATH) {
            url {
                appendPathSegments(articleId, slug)
            }
        }.body()

    private companion object {
        const val NEWS_PATH = "/api/v1/news"
    }
}
