package kr.co.cotton.vlrgg_mobile.feature.news

/** Maps News source structures to the JSON-only public API contract. */
internal class NewsMapper {
    fun toListResponse(page: Int, source: NewsListSource): NewsListResponse = NewsListResponse(
        page = page,
        nextPage = (page + 1).takeIf { source.hasNextPage },
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
        is NewsLinkSourceInline -> NewsLinkInlineResponse(
            label = source.label,
            kind = NewsLinkKind.valueOf(source.kind.name),
            reference = source.reference.takeIf {
                source.kind == NewsLinkKindSource.TEAM || source.kind == NewsLinkKindSource.PLAYER
            },
        )
    }
}
