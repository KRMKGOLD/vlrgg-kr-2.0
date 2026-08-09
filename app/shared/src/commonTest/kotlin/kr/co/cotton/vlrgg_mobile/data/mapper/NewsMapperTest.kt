package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleBlockDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleInlineDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsLinkKindDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsSummaryDto
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleBlock
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsLinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NewsMapperTest {

    @Test
    fun listResponseMapsPageAndSummary() {
        val response = NewsListResponseDto(
            page = 2,
            nextPage = 3,
            items = listOf(
                NewsSummaryDto(
                    reference = "101/champions-run",
                    title = "Champions run",
                    author = "Reporter",
                    publishedAt = "2026-08-09T12:00:00Z",
                ),
            ),
        )

        val newsPage = response.toDomain()

        assertEquals(2, newsPage.page)
        assertEquals(3, newsPage.nextPage)
        assertEquals(1, newsPage.items.size)
        assertEquals("101", newsPage.items.single().articleId)
        assertEquals("champions-run", newsPage.items.single().slug)
        assertEquals("Champions run", newsPage.items.single().title)
        assertEquals("Reporter", newsPage.items.single().author)
        assertEquals("2026-08-09T12:00:00Z", newsPage.items.single().publishedAt)
    }

    @Test
    fun articleResponseMapsAllBlocksAndPreservesOrder() {
        val response = articleResponse(
            blocks = listOf(
                NewsArticleBlockDto.Paragraph(
                    content = listOf(
                        NewsArticleInlineDto.Text("Opening text"),
                        NewsArticleInlineDto.Link(
                            label = "Team",
                            kind = NewsLinkKindDto.TEAM,
                            reference = "team/sen",
                        ),
                    ),
                ),
                NewsArticleBlockDto.Image(
                    imageUrl = "https://example.invalid/image.jpg",
                    caption = "Final stage",
                ),
                NewsArticleBlockDto.ListBlock(
                    ordered = true,
                    items = listOf(
                        listOf(NewsArticleInlineDto.Text("First item")),
                    ),
                ),
            ),
        )

        val article = response.toDomain()

        assertEquals("101", article.articleId)
        assertEquals("champions-run", article.slug)
        assertEquals("Champions run", article.title)
        assertEquals("Reporter", article.author)
        assertEquals("2026-08-09T12:00:00Z", article.publishedAt)

        val paragraph = assertIs<NewsArticleBlock.Paragraph>(article.blocks[0])
        assertEquals(NewsArticleInline.Text("Opening text"), paragraph.content[0])
        assertEquals(
            NewsArticleInline.Link(
                label = "Team",
                kind = NewsLinkKind.TEAM,
                reference = "team/sen",
            ),
            paragraph.content[1],
        )

        assertEquals(
            NewsArticleBlock.Image(
                imageUrl = "https://example.invalid/image.jpg",
                caption = "Final stage",
            ),
            article.blocks[1],
        )
        assertEquals(
            NewsArticleBlock.ListBlock(
                ordered = true,
                items = listOf(
                    listOf(NewsArticleInline.Text("First item")),
                ),
            ),
            article.blocks[2],
        )
    }

    @Test
    fun linkKindsMapExplicitly() {
        val expectedKinds = listOf(
            NewsLinkKind.TEAM,
            NewsLinkKind.PLAYER,
            NewsLinkKind.EVENT,
            NewsLinkKind.MATCH,
            NewsLinkKind.INTERNAL_UNSUPPORTED,
            NewsLinkKind.EXTERNAL,
        )
        val response = articleResponse(
            blocks = listOf(
                NewsArticleBlockDto.Paragraph(
                    content = NewsLinkKindDto.entries.map { kind ->
                        NewsArticleInlineDto.Link(
                            label = kind.name,
                            kind = kind,
                            reference = null,
                        )
                    },
                ),
            ),
        )

        val paragraph = assertIs<NewsArticleBlock.Paragraph>(
            response.toDomain().blocks.single(),
        )
        val actualKinds = paragraph.content.map { inline ->
            assertIs<NewsArticleInline.Link>(inline).kind
        }

        assertEquals(expectedKinds, actualKinds)
    }

    @Test
    fun malformedSummaryReferenceIsRejected() {
        val response = NewsListResponseDto(
            page = 1,
            nextPage = null,
            items = listOf(
                NewsSummaryDto(
                    reference = "101/champions/run",
                    title = "Champions run",
                    author = "Reporter",
                    publishedAt = "2026-08-09T12:00:00Z",
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            response.toDomain()
        }
    }

    @Test
    fun malformedArticleReferenceIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            articleResponse(reference = "/champions-run").toDomain()
        }
    }

    private fun articleResponse(
        reference: String = "101/champions-run",
        blocks: List<NewsArticleBlockDto> = emptyList(),
    ) = NewsArticleResponseDto(
        reference = reference,
        title = "Champions run",
        author = "Reporter",
        publishedAt = "2026-08-09T12:00:00Z",
        blocks = blocks,
    )
}
