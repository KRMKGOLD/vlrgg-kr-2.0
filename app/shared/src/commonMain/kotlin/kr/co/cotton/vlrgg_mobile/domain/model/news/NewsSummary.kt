package kr.co.cotton.vlrgg_mobile.domain.model.news

data class NewsSummary(
    val articleId: String,
    val slug: String,
    val title: String,
    val author: String?,
    val publishedAt: String,
)
