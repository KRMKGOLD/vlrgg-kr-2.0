package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kotlin.test.*

class TeamDetailParserTest {
    private val parser = TeamDetailParser()

    @Test
    fun `parser separates active team sections and excludes contaminated links`() {
        val source = parser.parse(activeContent())

        assertEquals(TeamProfileSource("KIWOOM DRX", "KRX", "South Korea"), source.profile)
        assertEquals(listOf("698887"), source.upcomingMatches.map(TeamMatchSource::id))
        assertEquals("VCT 26: PAC Stage 2", source.upcomingMatches.single().eventName)
        assertEquals(listOf("675209"), source.recentMatches.map(TeamMatchSource::id))
        assertEquals(listOf("4462"), source.players.map(TeamRosterMemberSource::id))
        assertEquals(listOf("775"), source.staff.map(TeamRosterMemberSource::id))
        assertEquals(
            listOf("700755/kiwoom-drx-releases-rookie-hermes", "672565/rrq-and-krx-eliminate-dfm"),
            source.news.map { it.reference.value },
        )
    }

    @Test
    fun `parser accepts sparse team and missing news section as empty optional content`() {
        val source = parser.parse(sparseContent())

        assertEquals(TeamProfileSource("One-Off Team", null, null), source.profile)
        assertTrue(source.upcomingMatches.isEmpty())
        assertTrue(source.recentMatches.isEmpty())
        assertTrue(source.players.isEmpty())
        assertTrue(source.staff.isEmpty())
        assertTrue(source.news.isEmpty())
    }

    @Test
    fun `parser accepts a verified empty Team news container`() {
        val content = sparseContent().copy(
            newsHtml = fixture("sparse-team-news.html").replace("</body>", "<div class=\"wf-card\"></div></body>"),
        )

        assertTrue(parser.parse(content).news.isEmpty())
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
    fun `parser fails closed when an observed match section drifts from supported candidates`() {
        val content = activeContent().copy(
            overviewHtml = fixture("active-team-overview.html")
                .replace("class=\"wf-card fc-flex m-item\"", "class=\"wf-card fc-flex team-match\""),
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
            newsHtml = fixture("active-team-news.html").replaceFirst("class=\"wf-card\"", "class=\"team-news-card\""),
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

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("fixtures/teams/$name"),
    ).readText()
}
