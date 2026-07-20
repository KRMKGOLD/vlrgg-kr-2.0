package kr.co.cotton.vlrgg_mobile.feature.news

import kotlin.test.Test
import kotlin.test.assertEquals

class NewsMapperTest {

    @Test
    fun `mapper maps every source link kind and exposes only routable references`() {
        val sourceKinds = NewsLinkKindSource.entries
        val response = NewsMapper().toArticleResponse(
            NewsArticleSource(
                reference = newsReference("111", "link-kinds"),
                title = "Link kinds",
                author = "author",
                publishedAt = "July 14, 2026",
                blocks = listOf(
                    NewsParagraphSourceBlock(
                        sourceKinds.map { kind ->
                            NewsLinkSourceInline(
                                label = kind.name,
                                kind = kind,
                                reference = "1/${kind.name.lowercase()}",
                            )
                        },
                    ),
                ),
            ),
        )

        val links = ((response.blocks.single() as NewsParagraphBlockResponse).content)
            .filterIsInstance<NewsLinkInlineResponse>()

        assertEquals(NewsLinkKind.entries, links.map(NewsLinkInlineResponse::kind))
        assertEquals(
            listOf("1/team", "1/player", null, null, null, null),
            links.map(NewsLinkInlineResponse::reference),
        )
    }
}
