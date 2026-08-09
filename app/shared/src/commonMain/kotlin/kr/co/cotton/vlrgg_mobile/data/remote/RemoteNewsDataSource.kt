package kr.co.cotton.vlrgg_mobile.data.remote

import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsListResponseDto

internal interface RemoteNewsDataSource {

    suspend fun getNewsPage(page: Int): NewsListResponseDto

    suspend fun getNewsArticle(
        articleId: String,
        slug: String,
    ): NewsArticleResponseDto
}