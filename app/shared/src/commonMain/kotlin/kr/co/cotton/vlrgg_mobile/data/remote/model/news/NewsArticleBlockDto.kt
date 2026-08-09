package kr.co.cotton.vlrgg_mobile.data.remote.model.news

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface NewsArticleBlockDto {

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val content: List<NewsArticleInlineDto>,
    ) : NewsArticleBlockDto

    @Serializable
    @SerialName("image")
    data class Image(
        val imageUrl: String,
        val caption: String?,
    ) : NewsArticleBlockDto

    @Serializable
    @SerialName("list")
    data class ListBlock(
        val ordered: Boolean,
        val items: List<List<NewsArticleInlineDto>>,
    ) : NewsArticleBlockDto
}
