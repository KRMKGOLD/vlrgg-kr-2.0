package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleBlockDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleInlineDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsLinkKindDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsSummaryDto
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleBlock
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsLinkKind
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsPage
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary

internal fun NewsListResponseDto.toDomain(): NewsPage = NewsPage(
    page = page,
    nextPage = nextPage,
    items = items.map(NewsSummaryDto::toDomain),
)

internal fun NewsArticleResponseDto.toDomain(): NewsArticle {
    val (articleId, slug) = reference.toArticleReferenceSegments()

    return NewsArticle(
        articleId = articleId,
        slug = slug,
        title = title,
        author = author,
        publishedAt = publishedAt,
        blocks = blocks.map(NewsArticleBlockDto::toDomain),
    )
}

private fun NewsSummaryDto.toDomain(): NewsSummary {
    val (articleId, slug) = reference.toArticleReferenceSegments()

    return NewsSummary(
        articleId = articleId,
        slug = slug,
        title = title,
        author = author,
        publishedAt = publishedAt,
    )
}

private fun NewsArticleBlockDto.toDomain(): NewsArticleBlock = when (this) {
    is NewsArticleBlockDto.Paragraph -> NewsArticleBlock.Paragraph(
        content = content.map(NewsArticleInlineDto::toDomain),
    )

    is NewsArticleBlockDto.Image -> NewsArticleBlock.Image(
        imageUrl = imageUrl,
        caption = caption,
    )

    is NewsArticleBlockDto.ListBlock -> NewsArticleBlock.ListBlock(
        ordered = ordered,
        items = items.map { item -> item.map(NewsArticleInlineDto::toDomain) },
    )
}

private fun NewsArticleInlineDto.toDomain(): NewsArticleInline = when (this) {
    is NewsArticleInlineDto.Text -> NewsArticleInline.Text(text = text)
    is NewsArticleInlineDto.Link -> NewsArticleInline.Link(
        label = label,
        kind = kind.toDomain(),
        reference = reference,
    )
}

private fun NewsLinkKindDto.toDomain(): NewsLinkKind = when (this) {
    NewsLinkKindDto.TEAM -> NewsLinkKind.TEAM
    NewsLinkKindDto.PLAYER -> NewsLinkKind.PLAYER
    NewsLinkKindDto.EVENT -> NewsLinkKind.EVENT
    NewsLinkKindDto.MATCH -> NewsLinkKind.MATCH
    NewsLinkKindDto.INTERNAL_UNSUPPORTED -> NewsLinkKind.INTERNAL_UNSUPPORTED
    NewsLinkKindDto.EXTERNAL -> NewsLinkKind.EXTERNAL
}

internal fun String.toArticleReferenceSegments(): Pair<String, String> {
    val separatorIndex = indexOf('/')
    require(
        separatorIndex > 0 &&
            separatorIndex == lastIndexOf('/') &&
            separatorIndex < lastIndex,
    ) {
        "News reference must contain exactly two non-empty segments"
    }

    return substring(0, separatorIndex) to substring(separatorIndex + 1)
}
