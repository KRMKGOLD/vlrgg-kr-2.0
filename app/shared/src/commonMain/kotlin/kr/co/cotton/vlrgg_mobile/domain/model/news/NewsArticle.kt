package kr.co.cotton.vlrgg_mobile.domain.model.news

import kotlin.time.Instant

data class NewsArticle(
    val articleId: String,
    val slug: String,
    val title: String,
    val author: String,
    val publishedAt: String,
    val publishedAtUtc: Instant?,
    val blocks: List<NewsArticleBlock>,
)
