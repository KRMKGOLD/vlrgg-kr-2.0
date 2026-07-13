package kr.co.cotton.vlrgg_mobile.feature.news

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Versioned contract for GET /api/v1/news. */
@Serializable
data class NewsListResponse(
    val page: Int,
    val nextPage: Int?,
    val items: List<NewsSummaryResponse>,
)

@Serializable
data class NewsSummaryResponse(
    /** Canonical relative article path, used as /api/v1/news/{reference}. */
    val reference: String,
    val title: String,
    val author: String,
    /** Source-provided publication timestamp, intentionally unformatted for the UI. */
    val publishedAt: String,
)

/** Versioned contract for GET /api/v1/news/{articleId}/{slug}. */
@Serializable
data class NewsArticleResponse(
    val reference: String,
    val title: String,
    val author: String,
    val publishedAt: String,
    val blocks: List<NewsArticleBlockResponse>,
)

@Serializable
sealed interface NewsArticleBlockResponse

@Serializable
@SerialName("paragraph")
data class NewsParagraphBlockResponse(
    val content: List<NewsArticleInlineResponse>,
) : NewsArticleBlockResponse

@Serializable
@SerialName("image")
data class NewsImageBlockResponse(
    val imageUrl: String,
    val caption: String?,
) : NewsArticleBlockResponse

@Serializable
@SerialName("list")
data class NewsListBlockResponse(
    val ordered: Boolean,
    val items: List<List<NewsArticleInlineResponse>>,
) : NewsArticleBlockResponse

@Serializable
sealed interface NewsArticleInlineResponse

@Serializable
@SerialName("text")
data class NewsTextInlineResponse(
    val text: String,
) : NewsArticleInlineResponse

@Serializable
@SerialName("link")
data class NewsLinkInlineResponse(
    val label: String,
    val kind: NewsLinkKind,
    /** Only Team and Player links expose an app-routable reference in this MVP. */
    val reference: String?,
) : NewsArticleInlineResponse

@Serializable
enum class NewsLinkKind {
    TEAM,
    PLAYER,
    EVENT,
    MATCH,
    INTERNAL_UNSUPPORTED,
    EXTERNAL,
}
