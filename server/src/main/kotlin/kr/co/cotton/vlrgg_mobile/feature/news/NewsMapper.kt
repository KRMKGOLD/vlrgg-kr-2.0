package kr.co.cotton.vlrgg_mobile.feature.news

/** Maps News source structures to the JSON-only public API contract. */
internal class NewsMapper {
    fun toListResponse(page: Int, source: NewsListSource): NewsListResponse = NewsListResponse(
        page = page,
        nextPage = source.nextPage?.takeIf { candidate ->
            candidate == page + 1 && candidate <= MAX_NEWS_PAGE
        },
        items = source.articles.map(::toSummaryResponse),
    )

    fun toArticleResponse(source: NewsArticleSource): NewsArticleResponse = NewsArticleResponse(
        reference = source.reference.value,
        title = source.title,
        author = source.author,
        publishedAt = source.publishedAt,
        blocks = source.blocks.map(::toBlockResponse),
    )

    private fun toSummaryResponse(source: NewsSummarySource) = NewsSummaryResponse(
        reference = source.reference.value,
        title = source.title,
        author = source.author,
        publishedAt = source.publishedAt,
    )

    private fun toBlockResponse(source: NewsSourceBlock): NewsArticleBlockResponse = when (source) {
        is NewsParagraphSourceBlock -> NewsParagraphBlockResponse(source.content.map(::toInlineResponse))
        is NewsImageSourceBlock -> NewsImageBlockResponse(source.imageUrl, source.caption)
        is NewsListSourceBlock -> NewsListBlockResponse(
            ordered = source.ordered,
            items = source.items.map { item -> item.map(::toInlineResponse) },
        )
    }

    private fun toInlineResponse(source: NewsSourceInline): NewsArticleInlineResponse = when (source) {
        is NewsTextSourceInline -> NewsTextInlineResponse(source.text)
        is NewsLinkSourceInline -> {
            val kind = when (source.kind) {
                NewsLinkKindSource.TEAM -> NewsLinkKind.TEAM
                NewsLinkKindSource.PLAYER -> NewsLinkKind.PLAYER
                NewsLinkKindSource.EVENT -> NewsLinkKind.EVENT
                NewsLinkKindSource.MATCH -> NewsLinkKind.MATCH
                NewsLinkKindSource.INTERNAL_UNSUPPORTED -> NewsLinkKind.INTERNAL_UNSUPPORTED
                NewsLinkKindSource.EXTERNAL -> NewsLinkKind.EXTERNAL
            }
            NewsLinkInlineResponse(
                label = source.label,
                kind = kind,
                reference = source.reference.takeIf {
                    kind == NewsLinkKind.TEAM || kind == NewsLinkKind.PLAYER
                },
            )
        }
    }
}
