package kr.co.cotton.vlrgg_mobile.domain.model.news

data class NewsPage(
    val page: Int,
    val nextPage: Int?,
    val items: List<NewsSummary>,
)
