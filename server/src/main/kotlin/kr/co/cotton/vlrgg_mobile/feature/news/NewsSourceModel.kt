package kr.co.cotton.vlrgg_mobile.feature.news

import kr.co.cotton.vlrgg_mobile.common.scraping.NewsReference

internal const val DEFAULT_NEWS_PAGE = 1
internal const val MAX_NEWS_PAGE = 10_000

internal data class NewsListSource(
    val articles: List<NewsSummarySource>,
    val nextPage: Int?,
)

internal data class NewsSummarySource(
    val reference: NewsReference,
    val title: String,
    val author: String,
    val publishedAt: String,
)

internal data class NewsArticleSource(
    val reference: NewsReference,
    val title: String,
    val author: String,
    val publishedAt: String,
    val blocks: List<NewsSourceBlock>,
)

internal sealed interface NewsSourceBlock

internal data class NewsParagraphSourceBlock(
    val content: List<NewsSourceInline>,
) : NewsSourceBlock

internal data class NewsImageSourceBlock(
    val imageUrl: String,
    val caption: String?,
) : NewsSourceBlock

internal data class NewsListSourceBlock(
    val ordered: Boolean,
    val items: List<List<NewsSourceInline>>,
) : NewsSourceBlock

internal sealed interface NewsSourceInline

internal data class NewsTextSourceInline(
    val text: String,
) : NewsSourceInline

internal data class NewsLinkSourceInline(
    val label: String,
    val kind: NewsLinkKindSource,
    val reference: String?,
) : NewsSourceInline

internal enum class NewsLinkKindSource {
    TEAM,
    PLAYER,
    EVENT,
    MATCH,
    INTERNAL_UNSUPPORTED,
    EXTERNAL,
}
