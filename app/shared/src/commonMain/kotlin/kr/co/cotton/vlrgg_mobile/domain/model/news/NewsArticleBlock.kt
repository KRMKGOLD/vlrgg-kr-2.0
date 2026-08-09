package kr.co.cotton.vlrgg_mobile.domain.model.news

sealed interface NewsArticleBlock {

    data class Paragraph(
        val content: List<NewsArticleInline>,
    ) : NewsArticleBlock

    data class Image(
        val imageUrl: String,
        val caption: String?,
    ) : NewsArticleBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<List<NewsArticleInline>>,
    ) : NewsArticleBlock
}
