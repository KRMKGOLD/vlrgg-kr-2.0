package kr.co.cotton.vlrgg_mobile.feature.news

import kr.co.cotton.vlrgg_mobile.common.scraping.NewsReference
import kotlin.test.*

class NewsParserTest {

    private val parser = NewsParser()

    @Test
    fun `list parser reads summaries and the next page from a fixture`() {
        val page = parser.parseList(readFixture("news-list-page-1.html"), currentPage = 1)

        assertEquals(
            listOf(
                NewsSummarySource(
                    reference = newsReference("101", "champions-run"),
                    title = "Champions run",
                    author = "raezeri",
                    publishedAt = "July 13, 2026",
                ),
                NewsSummarySource(
                    reference = newsReference("102", "roster-update"),
                    title = "Roster update",
                    author = "jenopelle",
                    publishedAt = "July 12, 2026",
                ),
            ),
            page.articles,
        )
        assertEquals(2, page.nextPage)
    }

    @Test
    fun `list parser supports the operational card item title layout`() {
        val page = parser.parseList(readFixture("news-list-operational-layout.html"), currentPage = 1)

        assertEquals(
            listOf(
                NewsSummarySource(
                    reference = newsReference("201", "operational-layout-news"),
                    title = "Operational layout news",
                    author = "operational-author",
                    publishedAt = "July 13, 2026",
                ),
            ),
            page.articles,
        )
        assertEquals(2, page.nextPage)
    }

    @Test
    fun `list parser limits module item fallback to the news list card`() {
        val page = parser.parseList(readFixture("news-list-with-mixed-cards.html"), currentPage = 1)

        assertEquals(
            listOf(
                NewsSummarySource(
                    reference = newsReference("202", "actual-news-card"),
                    title = "Actual news card",
                    author = "news-author",
                    publishedAt = "July 13, 2026",
                ),
            ),
            page.articles,
        )
    }

    @Test
    fun `list parser ignores non pagination and non canonical page links`() {
        val page = parser.parseList(readFixture("news-list-with-unrelated-page-links.html"), currentPage = 1)

        assertNull(page.nextPage)
    }

    @Test
    fun `list parser and mapper never return a page over the documented boundary`() {
        val parsedPage = parser.parseList(readFixture("news-list-page-boundary.html"), currentPage = 10_000)

        assertNull(parsedPage.nextPage)
        assertNull(
            NewsMapper().toListResponse(
                page = 10_000,
                source = NewsListSource(articles = emptyList(), nextPage = 10_001),
            ).nextPage,
        )
    }

    @Test
    fun `news reference accepts exact VLR href forms but rejects untrusted hosts`() {
        val expected = newsReference("111", "canonical-news")

        assertEquals(expected, NewsReference.fromHref("/111/canonical-news"))
        assertEquals(expected, NewsReference.fromHref("https://www.vlr.gg/111/canonical-news?source=list"))
        assertEquals(expected, NewsReference.fromHref("http://vlr.gg/111/canonical-news#article"))
        assertEquals(expected, NewsReference.fromHref("//www.vlr.gg/111/canonical-news"))
        assertNull(NewsReference.fromHref("//evil.example/111/canonical-news"))
        assertNull(NewsReference.fromHref("https://www.vlr.gg.evil/111/canonical-news"))
        assertTrue(NewsReference.isTrustedHref("https://www.vlr.gg/not-a-news-reference"))
        assertTrue(NewsReference.isTrustedHref("http://vlr.gg/not-a-news-reference"))
        assertTrue(NewsReference.isTrustedHref("//www.vlr.gg/not-a-news-reference"))
        assertFalse(NewsReference.isTrustedHref("https://untrusted.example/not-a-news-reference"))
    }

    @Test
    fun `news reference exists only for canonical values and preserves value equality`() {
        val expected = newsReference("111", "canonical-news")

        assertEquals(expected, NewsReference.fromPath("111", "canonical-news"))
        assertEquals(expected, NewsReference.fromHref("/111/canonical-news"))
        assertEquals("111/canonical-news", expected.value)
        assertEquals(expected.hashCode(), newsReference("111", "canonical-news").hashCode())

        listOf("0", "01", "10000000000", "not-a-number").forEach { articleId ->
            assertNull(NewsReference.fromPath(articleId, "canonical-news"))
        }
        listOf("", "Uppercase", "contains_underscore", "too/many-segments").forEach { slug ->
            assertNull(NewsReference.fromPath("111", slug))
        }
        listOf("/0/canonical-news", "/01/canonical-news", "/111/Uppercase", "/111/").forEach { href ->
            assertNull(NewsReference.fromHref(href))
        }
    }

    @Test
    fun `article parser preserves supported blocks and excludes non article text`() {
        val article = parser.parseArticle(
            html = readFixture("news-article.html"),
            reference = newsReference("101", "champions-run"),
        )

        assertEquals("Champions run", article.title)
        assertEquals("raezeri", article.author)
        assertEquals("July 13, 2026", article.publishedAt)
        assertNull(article.publishedAtUtc)
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
    fun `article parser ignores missing optional image data and preserves readable content`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-without-optional-image.html"),
            reference = newsReference("103", "text-only-article"),
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
    fun `article parser exposes only HTTPS body image URLs`() {
        val fixture = readFixture("news-article.html")
        fun imageUrls(source: String): List<String> = parser.parseArticle(
            html = fixture.replace("/img/champions.png", source),
            reference = newsReference("101", "champions-run"),
        ).blocks.filterIsInstance<NewsImageSourceBlock>().map(NewsImageSourceBlock::imageUrl)

        assertEquals(listOf("https://cdn.example/a.png"), imageUrls("https://cdn.example/a.png"))
        assertEquals(listOf("https://cdn.example/a.png"), imageUrls("//cdn.example/a.png"))
        listOf(
            "http://192.168.0.1/a.png",
            "http://cdn.example/a.png",
            "file:///data/a.png",
            "content://media/a.png",
            "data:image/png;base64,AAAA",
            "javascript:void(0)",
        ).forEach { source ->
            assertEquals(emptyList(), imageUrls(source), "Rejected image source: $source")
        }
    }

    @Test
    fun `article parser keeps protocol relative links external`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-protocol-relative-link.html"),
            reference = newsReference("106", "protocol-relative-link"),
        )

        val link = (article.blocks.single() as NewsParagraphSourceBlock).content[1] as NewsLinkSourceInline

        assertEquals(NewsLinkKindSource.EXTERNAL, link.kind)
        assertNull(link.reference)
    }

    @Test
    fun `article parser preserves spaces between adjacent links`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-adjacent-links.html"),
            reference = newsReference("111", "adjacent-links"),
        )

        assertEquals(
            listOf(
                NewsLinkSourceInline("Alpha", NewsLinkKindSource.TEAM, "2/alpha"),
                NewsTextSourceInline(" "),
                NewsLinkSourceInline("Bravo", NewsLinkKindSource.TEAM, "3/bravo"),
            ),
            (article.blocks.single() as NewsParagraphSourceBlock).content,
        )
    }

    @Test
    fun `article parser collapses whitespace across text and link boundaries`() {
        val blocks = parseInlineWhitespaceBlocks()

        assertEquals(
            listOf(
                NewsLinkSourceInline("Eternal Fire", NewsLinkKindSource.TEAM, "1/eternal-fire"),
                NewsTextSourceInline(" has "),
                NewsLinkSourceInline("signed", NewsLinkKindSource.PLAYER, "2/signed"),
                NewsTextSourceInline(" its fourth player, signing former "),
                NewsLinkSourceInline("FUT Esports", NewsLinkKindSource.TEAM, "3/fut-esports"),
                NewsTextSourceInline(" Initiator Okan \""),
                NewsLinkSourceInline("KROSTALY", NewsLinkKindSource.PLAYER, "4/krostaly"),
                NewsTextSourceInline("\" Alaçam."),
            ),
            blocks[0].content,
        )
    }

    @Test
    fun `article parser drops edge whitespace inside inline-block link wrappers`() {
        val content = parseInlineWhitespaceBlocks()[1].content

        assertEquals(
            listOf(
                NewsTextSourceInline("retain just "),
                NewsLinkSourceInline("yetujey", NewsLinkKindSource.PLAYER, "8369/yetujey"),
                NewsTextSourceInline(" as part of a rebuild that added former "),
                NewsLinkSourceInline("Team Heretics", NewsLinkKindSource.TEAM, "6/team-heretics"),
                NewsTextSourceInline(" player "),
                NewsLinkSourceInline("Wo0t", NewsLinkKindSource.PLAYER, "7/wo0t"),
                NewsTextSourceInline(", and former "),
                NewsLinkSourceInline("Other", NewsLinkKindSource.PLAYER, "8/other"),
                NewsTextSourceInline(" ."),
            ),
            content,
        )
    }

    @Test
    fun `article parser trims edge whitespace of elements nested inside a link wrapper`() {
        assertEquals(
            listOf(
                NewsLinkSourceInline("Alpha", NewsLinkKindSource.TEAM, "1/alpha"),
                NewsTextSourceInline(", B"),
            ),
            parseInlineWhitespaceBlocks()[3].content,
        )
    }

    @Test
    fun `article parser ignores inline-block text that is not a display declaration`() {
        assertEquals(
            listOf(
                NewsTextSourceInline("Meet "),
                NewsLinkSourceInline("Alpha", NewsLinkKindSource.TEAM, "1/alpha"),
                NewsTextSourceInline("."),
            ),
            parseInlineWhitespaceBlocks()[4].content,
        )
    }

    @Test
    fun `article parser drops wrapper whitespace for display inline-block in any spacing or case`() {
        val blocks = parseInlineWhitespaceBlocks()
        val expected = listOf(
            NewsTextSourceInline("Meet"),
            NewsLinkSourceInline("Alpha", NewsLinkKindSource.TEAM, "1/alpha"),
            NewsTextSourceInline("."),
        )

        assertEquals(expected, blocks[5].content)
        assertEquals(expected, blocks[6].content)
    }

    @Test
    fun `article parser resolves display importance like CSS`() {
        val blocks = parseInlineWhitespaceBlocks()
        val trimmed = listOf(
            NewsTextSourceInline("Meet"),
            NewsLinkSourceInline("Alpha", NewsLinkKindSource.TEAM, "1/alpha"),
            NewsTextSourceInline("."),
        )
        val kept = listOf(
            NewsTextSourceInline("Meet "),
            NewsLinkSourceInline("Alpha", NewsLinkKindSource.TEAM, "1/alpha"),
            NewsTextSourceInline("."),
        )

        assertEquals(trimmed, blocks[7].content) // !IMPORTANT in any case
        assertEquals(trimmed, blocks[8].content) // "! important" wins over a later normal declaration
        assertEquals(kept, blocks[9].content) // an earlier !important inline beats a later normal inline-block
    }

    @Test
    fun `article parser drops the space before a line break that starts a link wrapper`() {
        assertEquals(
            listOf(
                NewsTextSourceInline("Before\n"),
                NewsLinkSourceInline("Alpha", NewsLinkKindSource.TEAM, "1/alpha"),
                NewsTextSourceInline("."),
            ),
            parseInlineWhitespaceBlocks()[10].content,
        )
    }

    @Test
    fun `article parser keeps br line breaks without surrounding spaces and trims paragraph edges`() {
        val blocks = parseInlineWhitespaceBlocks()

        assertEquals(
            listOf(NewsTextSourceInline("First line\nsecond line\n\nthird")),
            blocks[2].content,
        )
        assertFalse((blocks[0].content.first() as? NewsTextSourceInline)?.text?.startsWith(" ") == true)
        assertFalse((blocks[0].content.last() as? NewsTextSourceInline)?.text?.endsWith(" ") == true)
    }

    private fun parseInlineWhitespaceBlocks(): List<NewsParagraphSourceBlock> =
        parser.parseArticle(
            html = readFixture("news-article-inline-whitespace.html"),
            reference = newsReference("112", "inline-whitespace"),
        ).blocks.map { it as NewsParagraphSourceBlock }

    @Test
    fun `article parser recognizes only VLR numeric paths as matches`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-match-links.html"),
            reference = newsReference("110", "match-links"),
        )

        val links = (article.blocks.single() as NewsParagraphSourceBlock).content
            .filterIsInstance<NewsLinkSourceInline>()

        assertEquals(
            listOf(
                NewsLinkSourceInline("Local match", NewsLinkKindSource.MATCH, "12345/local-match"),
                NewsLinkSourceInline("VLR match", NewsLinkKindSource.MATCH, "23456/host-match"),
                NewsLinkSourceInline("HTTP VLR match", NewsLinkKindSource.MATCH, "34567/http-host-match"),
                NewsLinkSourceInline("HTTP www VLR match", NewsLinkKindSource.MATCH, "45678/http-www-host-match"),
                NewsLinkSourceInline("Protocol relative", NewsLinkKindSource.EXTERNAL, null),
                NewsLinkSourceInline("External", NewsLinkKindSource.EXTERNAL, null),
                NewsLinkSourceInline("Legacy match route", NewsLinkKindSource.INTERNAL_UNSUPPORTED, null),
                NewsLinkSourceInline("Team", NewsLinkKindSource.TEAM, "2/team"),
                NewsLinkSourceInline("Player", NewsLinkKindSource.PLAYER, "3/player"),
                NewsLinkSourceInline("Event", NewsLinkKindSource.EVENT, "4/event"),
            ),
            links,
        )
    }

    @Test
    fun `article parser reads header metadata only from the article header`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-header-scope.html"),
            reference = newsReference("107", "header-scope"),
        )

        assertEquals("Actual article title", article.title)
        assertEquals("actual-author", article.author)
        assertEquals("July 13, 2026", article.publishedAt)
    }

    @Test
    fun `article parser exposes the header datetime as a UTC instant without changing the source label`() {
        val html = readFixture("news-article-operational-metadata.html")
        val reference = newsReference("780118", "wendler-to-leave-paper-rex-after-nearly-one-year")

        val article = parser.parseArticle(html, reference)
        assertEquals("4kanders0ny", article.author)
        assertEquals("16 hours ago", article.publishedAt)
        assertEquals("2026-10-25T15:09:14Z", article.publishedAtUtc)

        listOf(
            "2026-10-26 00:09:14",
            "2026-10-26T00:09:14",
            "2026-10-26T00:09:14+25:00",
            "2026-10-26T00:09:14+0900x",
            "2026-02-30T00:09:14+09:00",
            "",
        ).forEach { invalid ->
            val invalidHtml = html.replace("2026-10-26T00:09:14+09:00", invalid)
            assertNotEquals(html, invalidHtml)
            val invalidArticle = parser.parseArticle(invalidHtml, reference)
            assertEquals("16 hours ago", invalidArticle.publishedAt)
            assertNull(invalidArticle.publishedAtUtc)
        }

        val withoutDatetime = parser.parseArticle(html.replace(" datetime=\"2026-10-26T00:09:14+09:00\"", ""), reference)
        assertEquals("16 hours ago", withoutDatetime.publishedAt)
        assertNull(withoutDatetime.publishedAtUtc)
    }

    @Test
    fun `article parser turns direct prose into ordered paragraph blocks`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-mixed-direct-prose.html"),
            reference = newsReference("108", "mixed-direct-prose"),
        )

        assertEquals(
            listOf(
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Leading direct prose."))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Wrapped direct prose."))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Structured paragraph."))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Middle direct prose."))),
                NewsListSourceBlock(ordered = false, items = listOf(listOf(NewsTextSourceInline("List item")))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Trailing direct prose."))),
            ),
            article.blocks,
        )
    }

    @Test
    fun `article parser keeps section h1 in its original block order`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-section-heading.html"),
            reference = newsReference("109", "section-heading"),
        )

        assertEquals(
            listOf(
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Before the section."))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Section heading"))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("After the section."))),
            ),
            article.blocks,
        )
    }

    @Test
    fun `article parser preserves supported content nested in unknown direct children`() {
        val article = parser.parseArticle(
            html = readFixture("news-article-unknown-wrappers.html"),
            reference = newsReference("112", "unknown-wrappers"),
        )

        assertEquals(
            listOf(
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Before wrappers."))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Table paragraph."))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Span prose."))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("Preformatted prose."))),
                NewsParagraphSourceBlock(listOf(NewsTextSourceInline("After wrappers."))),
            ),
            article.blocks,
        )
    }

    @Test
    fun `article parser requires a title and supported body content`() {
        assertFailsWith<NewsParsingException> {
            parser.parseArticle(
                html = """
                    <article class="article-body"><p>Body without a title.</p></article>
                """.trimIndent(),
                reference = newsReference("104", "missing-title"),
            )
        }
        assertFailsWith<NewsParsingException> {
            parser.parseArticle(
                html = """
                    <h1 class="article-header-title">Empty body</h1>
                    <header class="article-header">
                        <div class="article-header-desc">
                            <span class="article-meta-time">July 11, 2026</span>
                            <a class="article-meta-author">author</a>
                        </div>
                    </header>
                    <article class="article-body"><iframe>unsupported</iframe></article>
                """.trimIndent(),
                reference = newsReference("105", "empty-body"),
            )
        }
    }

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture: $name" }.readText()
}
