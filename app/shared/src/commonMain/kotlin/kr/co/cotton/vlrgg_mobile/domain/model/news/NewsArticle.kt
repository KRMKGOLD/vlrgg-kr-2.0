package kr.co.cotton.vlrgg_mobile.domain.model.news

data class NewsArticle(
    val articleId: String,
    val slug: String,
    val title: String,
    val author: String,
    val publishedAt: String,
    val blocks: List<NewsArticleBlock>,
)
