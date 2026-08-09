package kr.co.cotton.vlrgg_mobile.domain.model.news

sealed interface NewsArticleInline {

    data class Text(
        val text: String,
    ) : NewsArticleInline

    data class Link(
        val label: String,
        val kind: NewsLinkKind,
        val reference: String?,
    ) : NewsArticleInline
}
