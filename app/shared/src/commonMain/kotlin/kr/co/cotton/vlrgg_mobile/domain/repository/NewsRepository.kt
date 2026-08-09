package kr.co.cotton.vlrgg_mobile.domain.repository

import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsPage

interface NewsRepository {

    suspend fun getNewsPage(page: Int): AppResult<NewsPage>

    suspend fun getNewsArticle(
        articleId: String,
        slug: String,
    ): AppResult<NewsArticle>
}
