package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kotlin.test.*

class TeamDetailParserTest {
    private val parser = TeamDetailParser()

    @Test
    fun `parser reads live wrapped Team news and excludes contaminated links`() {
        val source = parser.parse(activeContent())

        assertEquals(
            TeamProfileSource("KIWOOM DRX", "KRX", "South Korea", "https://owcdn.net/img/kiwoom-drx.png"),
            source.profile,
        )
        assertEquals(listOf("698887"), source.upcomingMatches.map(TeamMatchSource::id))
        assertEquals("VCT 26: PAC Stage 2", source.upcomingMatches.single().eventName)
        assertEquals(listOf("675209"), source.recentMatches.map(TeamMatchSource::id))
        assertEquals(listOf("4462"), source.players.map(TeamRosterMemberSource::id))
        assertEquals(listOf("775"), source.staff.map(TeamRosterMemberSource::id))
        assertEquals("https://owcdn.net/img/players/mako.png", source.players.single().imageUrl)
        assertEquals("https://www.vlr.gg/img/base/ph/sil.png", source.staff.single().imageUrl)
        assertEquals(
            listOf("700755/kiwoom-drx-releases-rookie-hermes", "672565/rrq-and-krx-eliminate-dfm"),
            source.news.map { it.reference.value },
        )
    }

    @Test
    fun `parser excludes a match module contaminating the verified Team news card`() {
        val content = activeContent().copy(
            newsHtml = fixture("active-team-news.html").replace(
                "</div><div class=\"wf-card\">",
                """  <a href="/601/alpha-vs-bravo" class="wf-module-item match-item">
    <div class="match-item-time">5:00 PM</div>
    <div class="match-item-vs"><div class="match-item-vs-team">Alpha</div><div class="match-item-vs-team">Bravo</div></div>
    <div class="match-item-event"><div class="match-item-event-series">Group</div>Stage</div>
  </a>
</div><div class="wf-card">""",
            ),
        )

        val source = parser.parse(content)

        assertEquals(
            listOf("700755/kiwoom-drx-releases-rookie-hermes", "672565/rrq-and-krx-eliminate-dfm"),
            source.news.map { it.reference.value },
        )
    }

    @Test
    fun `parser excludes direct known match anchors without the Team news class`() {
        listOf(
            """<a href="/601/alpha-vs-bravo" class="match-item"><div class="match-item-time">5:00 PM</div><div class="match-item-vs">Alpha vs Bravo</div><div class="match-item-event">Group</div></a>""",
            """<a href="/698887/kiwoom-drx-vs-detonation-focusme" class="wf-card fc-flex m-item"><div class="m-item-event">VCT 26: PAC Stage 2</div><div class="m-item-team">KIWOOM DRX</div><div class="m-item-result">4d 2h</div><div class="m-item-team">DetonatioN FocusMe</div><div class="m-item-date">2026/07/17</div></a>""",
        ).forEach { matchAnchor ->
            val content = activeContent().copy(
                newsHtml = """<html><body><div class="wf-card mod-header"><div class="team-header"></div></div><div class="wf-card">$matchAnchor</div></body></html>""",
            )

            assertTrue(parser.parse(content).news.isEmpty())
        }
    }

    @Test
    fun `parser excludes an overview match module contaminating the verified Team news card`() {
        val content = activeContent().copy(
            newsHtml = fixture("active-team-news.html").replace(
                "</div><div class=\"wf-card\">",
                """  <a href="/698887/kiwoom-drx-vs-detonation-focusme" class="wf-module-item wf-card fc-flex m-item">
    <div class="m-item-event"><div>VCT 26: PAC Stage 2</div>Group Stage · W1</div>
    <div class="m-item-team"><span class="m-item-team-name">KIWOOM DRX</span></div>
    <div class="m-item-result"><span>4d 2h</span></div>
    <div class="m-item-team"><span class="m-item-team-name">DetonatioN FocusMe</span></div>
    <div class="m-item-date"><div>2026/07/17</div>7:00 pm</div>
  </a>
</div><div class="wf-card">""",
            ),
        )

        assertEquals(expectedNewsReferences, parser.parse(content).news.map { it.reference.value })
    }

    @Test
    fun `parser fails closed for malformed trusted absolute Team news hrefs`() {
        listOf(
            "https://www.vlr.gg/not-a-news-reference",
            "http://vlr.gg/not-a-news-reference",
            "//www.vlr.gg/not-a-news-reference",
        ).forEach { malformedHref ->
            val content = activeContent().copy(
                newsHtml = fixture("active-team-news.html")
                    .replace("/700755/kiwoom-drx-releases-rookie-hermes", malformedHref),
            )

            assertFailsWith<SourceParsingFailure> { parser.parse(content) }
        }
    }

    @Test
    fun `parser excludes malformed untrusted Team news contamination`() {
        val content = activeContent().copy(
            newsHtml = fixture("active-team-news.html")
                .replace("https://untrusted.example/700000/private", "https://untrusted.example/not-a-news-reference"),
        )

        assertEquals(expectedNewsReferences, parser.parse(content).news.map { it.reference.value })
    }

    @Test
    fun `parser fails closed for a non-anchor Team news candidate`() {
        val content = activeContent().copy(
            newsHtml = fixture("active-team-news.html").replace(
                "</div><div class=\"wf-card\">",
                """<div class="wf-module-item" href="/700700/drifted-news"><div class="ge-text-light">2026/06/18</div><div>Drifted news</div></div></div><div class="wf-card">""",
            ),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser accepts sparse team and missing news section as empty optional content`() {
        val source = parser.parse(sparseContent())

        assertEquals(TeamProfileSource("One-Off Team", null, null, null), source.profile)
        assertNull(source.profile.logoUrl)
        assertTrue(source.upcomingMatches.isEmpty())
        assertTrue(source.recentMatches.isEmpty())
        assertTrue(source.players.isEmpty())
        assertTrue(source.staff.isEmpty())
        assertTrue(source.news.isEmpty())
    }

    @Test
    fun `parser ignores missing unsupported or malformed Team image URLs without failing`() {
        listOf(
            "",
            "//",
            "https://",
            "https:///image.png",
            "https://bad host/image.png",
            "http://cdn.example/image.png",
            "data:image/png;base64,abc",
            "javascript:alert(1)",
            "images/image.png",
        )
            .forEach { unsupportedUrl ->
                val source = parser.parse(activeContent().copy(
                    overviewHtml = fixture("active-team-overview.html")
                        .replace("//owcdn.net/img/kiwoom-drx.png", unsupportedUrl)
                        .replace("//owcdn.net/img/players/mako.png", unsupportedUrl)
                        .replace("/img/base/ph/sil.png", unsupportedUrl),
                ))

                assertNull(source.profile.logoUrl)
                assertNull(source.players.single().imageUrl)
                assertNull(source.staff.single().imageUrl)
            }
    }

    @Test
    fun `parser accepts uppercase HTTPS Team image URLs and normalizes their scheme`() {
        val source = parser.parse(activeContent().copy(
            overviewHtml = fixture("active-team-overview.html")
                .replace("//owcdn.net/img/kiwoom-drx.png", "HTTPS://owcdn.net/img/kiwoom-drx.png")
                .replace("//owcdn.net/img/players/mako.png", "HTTPS://owcdn.net/img/players/mako.png")
                .replace("/img/base/ph/sil.png", "HTTPS://www.vlr.gg/img/base/ph/sil.png"),
        ))

        assertEquals("https://owcdn.net/img/kiwoom-drx.png", source.profile.logoUrl)
        assertEquals("https://owcdn.net/img/players/mako.png", source.players.single().imageUrl)
        assertEquals("https://www.vlr.gg/img/base/ph/sil.png", source.staff.single().imageUrl)
    }

    @Test
    fun `parser scopes optional Team images to their Team header and roster item`() {
        val source = parser.parse(activeContent().copy(
            overviewHtml = fixture("active-team-overview.html")
                .replace("<div class=\"team-header-logo\"><img src=\"//owcdn.net/img/kiwoom-drx.png\"></div>", "")
                .replace(
                    "<div class=\"team-summary-container\">",
                    """<div class="team-header-logo"><img src="https://unrelated.example/logo.png"></div>
<div class="team-roster-item-img"><img src="https://unrelated.example/member.png"></div>
<div class="team-summary-container">""",
                )
                .replace("//owcdn.net/img/players/mako.png", ""),
        ))

        assertNull(source.profile.logoUrl)
        assertNull(source.players.single().imageUrl)
        assertEquals("https://www.vlr.gg/img/base/ph/sil.png", source.staff.single().imageUrl)
    }

    @Test
    fun `parser accepts a verified empty Team news container`() {
        val content = sparseContent().copy(
            newsHtml = fixture("sparse-team-news.html").replace("</body>", "<div class=\"wf-card\"></div></body>"),
        )

        assertTrue(parser.parse(content).news.isEmpty())
    }

    @Test
    fun `parser preserves the canonical TBD opponent in DOM order`() {
        val source = parser.parse(alternateAttaxRubyTbdContent())

        assertEquals(
            listOf(TeamMatchSource(
                id = "747668",
                eventName = "POKAL 2026",
                eventStage = "Playoffs ⋅ LR2",
                teamName = "ALTERNATE aTTaX Ruby",
                opponentName = "TBD",
                statusText = "1d 6h",
                scheduledAtText = "2026/09/05 12:00 am",
            )),
            source.upcomingMatches,
        )
    }

    @Test
    fun `parser fails closed for a non-TBD classless opponent span`() {
        val content = alternateAttaxRubyTbdContent().copy(
            overviewHtml = fixture("alternate-attax-ruby-tbd-overview.html").replace(
                ">TBD</span>",
                ">Unconfirmed</span>",
            ),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed for malformed canonical match item`() {
        val content = activeContent().copy(
            overviewHtml = fixture("active-team-overview.html")
                .replace("<span class=\"m-item-team-name\">DetonatioN FocusMe</span>", ""),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed for every noncanonical selected match candidate`() {
        listOf("/not-a-match/unsafe", "/675208/bad?token=private").forEach { invalidHref ->
            val content = activeContent().copy(
                overviewHtml = fixture("active-team-overview.html")
                    .replace("/698887/kiwoom-drx-vs-detonation-focusme", invalidHref),
            )

            assertFailsWith<SourceParsingFailure> { parser.parse(content) }
        }
    }

    @Test
    fun `parser fails closed when an observed match section drifts from supported candidates`() {
        val content = activeContent().copy(
            overviewHtml = fixture("active-team-overview.html")
                .replace("class=\"wf-card fc-flex m-item\"", "class=\"wf-card fc-flex team-match\""),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed for a non-anchor match candidate in an observed section`() {
        val content = activeContent().copy(
            overviewHtml = fixture("active-team-overview.html").replace(
                "<h2 class=\"wf-label mod-large\">Upcoming matches</h2><div>",
                """<h2 class="wf-label mod-large">Upcoming matches</h2><div><div class="m-item"><div class="m-item-team">Unexpected</div></div>""",
            ),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed for malformed player candidate in an observed roster section`() {
        val content = activeContent().copy(
            overviewHtml = fixture("active-team-overview.html")
                .replace("/player/4462/mako", "/player/4462/"),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed for a noncanonical selected roster candidate`() {
        val content = activeContent().copy(
            overviewHtml = fixture("active-team-overview.html")
                .replace("/player/4462/mako", "/player/not-an-id/unsafe"),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed for malformed Team news item in its verified container`() {
        val content = activeContent().copy(
            newsHtml = fixture("active-team-news.html")
                .replace("title=\"Kiwoom DRX releases rookie Hermes\"", "")
                .replace(">Kiwoom DRX releases rookie Hermes</div>", "></div>"),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed for malformed Team news href in its verified container`() {
        val content = activeContent().copy(
            newsHtml = fixture("active-team-news.html")
                .replace("/700755/kiwoom-drx-releases-rookie-hermes", "/not-a-news-reference"),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed when the observed Team news container drifts`() {
        val content = activeContent().copy(
            newsHtml = fixture("active-team-news.html")
                .replaceFirst("class=\"wf-card\">\n<a", "class=\"team-news-card\">\n<a"),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser fails closed when the live Team news header card drifts`() {
        val content = activeContent().copy(
            newsHtml = fixture("active-team-news.html")
                .replaceFirst("class=\"wf-card mod-header\"", "class=\"team-header-card\""),
        )

        assertFailsWith<SourceParsingFailure> { parser.parse(content) }
    }

    @Test
    fun `parser wraps missing required team header with overview source context`() {
        val failure = assertFailsWith<SourceParsingFailure> {
            parser.parse(activeContent().copy(overviewHtml = "<html><body>not a team page</body></html>"))
        }

        assertEquals("https://www.vlr.gg/", failure.canonicalUpstreamUrl)
    }

    private fun activeContent() = TeamDetailUpstreamContent(
        overviewHtml = fixture("active-team-overview.html"),
        newsHtml = fixture("active-team-news.html"),
        overviewUrl = Url("https://www.vlr.gg/team/8185/"),
        newsUrl = Url("https://www.vlr.gg/team/news/8185/"),
    )

    private fun sparseContent() = TeamDetailUpstreamContent(
        overviewHtml = fixture("sparse-team-overview.html"),
        newsHtml = fixture("sparse-team-news.html"),
        overviewUrl = Url("https://www.vlr.gg/team/19296/"),
        newsUrl = Url("https://www.vlr.gg/team/news/19296/"),
    )

    private fun alternateAttaxRubyTbdContent() = TeamDetailUpstreamContent(
        overviewHtml = fixture("alternate-attax-ruby-tbd-overview.html"),
        newsHtml = fixture("sparse-team-news.html"),
        overviewUrl = Url("https://www.vlr.gg/team/11496/"),
        newsUrl = Url("https://www.vlr.gg/team/news/11496/"),
    )

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("fixtures/teams/$name"),
    ).readText()

    private companion object {
        val expectedNewsReferences = listOf(
            "700755/kiwoom-drx-releases-rookie-hermes",
            "672565/rrq-and-krx-eliminate-dfm",
        )
    }
}
