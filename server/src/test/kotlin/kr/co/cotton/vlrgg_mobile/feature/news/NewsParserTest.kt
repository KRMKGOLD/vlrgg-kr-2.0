package kr.co.cotton.vlrgg_mobile.feature.news

import kotlin.test.*

class NewsParserTest {

    private val parser = NewsParser()

    @Test
    fun `list parser reads summaries and the next page from a fixture`() {
        val page = parser.parseList(readFixture("news-list-page-1.html"), currentPage = 1)

        assertEquals(
            listOf(
                NewsSummarySource(
                    reference = NewsReference("101", "champions-run"),
                    title = "Champions run",
                    author = "raezeri",
                    publishedAt = "July 13, 2026",
                ),
                NewsSummarySource(
                    reference = NewsReference("102", "roster-update"),
                    title = "Roster update",
                    author = "jenopelle",
                    publishedAt = "July 12, 2026",
                ),
            ),
            page.articles,
        )
        assertTrue(page.hasNextPage)
    }

    @Test
    fun `article parser preserves supported blocks and excludes non article text`() {
        val article = parser.parseArticle(
            html = readFixture("news-article.html"),
            reference = NewsReference("101", "champions-run"),
        )

        assertEquals("Champions run", article.title)
        assertEquals("raezeri", article.author)
        assertEquals("July 13, 2026", article.publishedAt)
        assertEquals(
            listOf(
                NewsParagraphSourceBlock(
                    listOf(
                        NewsTextSourceInline("Opening "),
                        NewsLinkSourceInline(
                            label = "Sentinels",
                            kind = NewsLinkKindSource.TEAM,
                            reference = "2/sentinels",
                        ),
                        NewsTextSourceInline(" story."),
                    ),
                ),
                NewsImageSourceBlock(
                    imageUrl = "https://www.vlr.gg/img/champions.png",
                    caption = "Champions trophy",
                ),
                NewsListSourceBlock(
                    ordered = true,
                    items = listOf(
                        listOf(
                            NewsTextSourceInline("First "),
                            NewsLinkSourceInline("TenZ", NewsLinkKindSource.PLAYER, "3/tenz"),
                        ),
                        listOf(NewsTextSourceInline("Second step")),
                    ),
                ),
                NewsListSourceBlock(
                    ordered = false,
                    items = listOf(
                        listOf(NewsTextSourceInline("Bullet one")),
                        listOf(NewsTextSourceInline("Bullet two")),
                    ),
                ),
                NewsParagraphSourceBlock(
                    listOf(
                        NewsTextSourceInline("Final "),
                        NewsLinkSourceInline("event", NewsLinkKindSource.EVENT, "4/champions"),
                        NewsTextSourceInline(", "),
                        NewsLinkSourceInline("match", NewsLinkKindSource.MATCH, "5/final"),
                        NewsTextSourceInline(", "),
                        NewsLinkSourceInline("internal", NewsLinkKindSource.INTERNAL_UNSUPPORTED, null),
                        NewsTextSourceInline(", and "),
                        NewsLinkSourceInline("external", NewsLinkKindSource.EXTERNAL, null),
                        NewsTextSourceInline(" links."),
                    ),
                ),
            ),
            article.blocks,
        )

        assertFalse(article.blocks.toString().contains("hidden hover-card text"))
        assertFalse(article.blocks.toString().contains("reference card text"))
        assertFalse(article.blocks.toString().contains("sidebar text"))
        assertFalse(article.blocks.toString().contains("comment text"))
        assertFalse(article.blocks.toString().contains("script text"))
        assertFalse(article.blocks.toString().contains("unsupported embed text"))
    }

    @Test
    fun `article parser treats missing optional image data as a partial article instead of a failure`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-without-optional-image.html"),
            reference = NewsReference("103", "text-only-article"),
        )

        assertEquals(
            listOf(
                NewsParagraphSourceBlock(
                    listOf(NewsTextSourceInline("This article intentionally has no supported image.")),
                ),
            ),
            article.blocks,
        )
    }

    @Test
    fun `article parser requires a title and supported body content`() {
        assertFailsWith<IllegalStateException> {
            parser.parseArticle(
                html = """
                    <article class="article-body"><p>Body without a title.</p></article>
                """.trimIndent(),
                reference = NewsReference("104", "missing-title"),
            )
        }
        assertFailsWith<IllegalStateException> {
            parser.parseArticle(
                html = """
                    <h1 class="article-header-title">Empty body</h1>
                    <div class="article-header-desc">
                        <span class="article-meta-time">July 11, 2026</span>
                        <a class="article-meta-author">author</a>
                    </div>
                    <article class="article-body"><iframe>unsupported</iframe></article>
                """.trimIndent(),
                reference = NewsReference("105", "empty-body"),
            )
        }
    }

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture: $name" }.readText()
}
