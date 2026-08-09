package kr.co.cotton.vlrgg_mobile.data.remote.model.news

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface NewsArticleInlineDto {

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
    ) : NewsArticleInlineDto

    @Serializable
    @SerialName("link")
    data class Link(
        val label: String,
        val kind: NewsLinkKindDto,
        val reference: String?,
    ) : NewsArticleInlineDto
}
