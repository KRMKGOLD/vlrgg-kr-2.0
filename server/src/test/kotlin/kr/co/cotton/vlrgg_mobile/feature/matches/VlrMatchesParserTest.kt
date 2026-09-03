package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kotlin.test.*

class VlrMatchesParserTest {
    private val parser = VlrMatchesParser()
    private val listUrl = Url("https://www.vlr.gg/matches")
    private val detailUrl = Url("https://www.vlr.gg/709685")

    @Test
    fun `parses scheduled groups and excludes match-like markup outside the list`() {
        val page = parser.parseList(fixtureHtml("upcoming.html"), listUrl)

        assertEquals(1, page.groups.size)
        assertEquals("Mon, July 13, 2026 Today", page.groups.single().dateLabel)
        assertEquals(1, page.groups.single().matches.size)

        val match = page.groups.single().matches.single()
        assertEquals("709685", match.id)
        assertEquals(MatchStatusSource.UPCOMING, match.status)
        assertEquals("5:00 PM", match.timeLabel)
        assertEquals("22m", match.relativeTimeLabel)
        assertEquals("ONSIDE GAMING", match.homeTeam.name)
        assertEquals("Dplus Esports", match.awayTeam.name)
        assertNull(match.homeTeam.imageUrl)
        assertNull(match.awayTeam.imageUrl)
        assertNull(match.homeScore)
        assertNull(match.awayScore)
        assertEquals("Challengers 2026: Korea WDG Split 2", match.event.name)
        assertEquals("Playoffs–Lower Final", match.event.series)
    }

    @Test
    fun `accepts an empty normal list and absent optional result values`() {
        val empty = parser.parseList(fixtureHtml("empty.html"), listUrl)
        assertTrue(empty.groups.isEmpty())

        val result = parser.parseList(fixtureHtml("results-with-missing-optionals.html"), listUrl)
        val match = result.groups.single().matches.single()

        assertEquals(MatchStatusSource.COMPLETED, match.status)
        assertEquals(2, match.homeScore)
        assertEquals(3, match.awayScore)
        assertNull(match.relativeTimeLabel)
        assertNull(match.event.series)
    }

    @Test
    fun `parses complete detail while treating absent maps and related sections as normal optional data`() {
        val detail = parser.parseDetail(fixtureHtml("detail-completed.html"), detailUrl, "709685")

        assertEquals("709685", detail.summary.id)
        assertEquals(MatchStatusSource.COMPLETED, detail.summary.status)
        assertEquals("Saturday, May 30", detail.summary.timeLabel)
        assertEquals("2026-05-30T04:00:00Z", detail.scheduledAt)
        assertEquals("Esports World Cup 2026: Pacific Qualifier", detail.summary.event.name)
        assertEquals("Stage 2: Lower Round 2", detail.summary.event.series)
        assertEquals("Nongshim RedForce", detail.summary.homeTeam.name)
        assertEquals("11060", detail.summary.homeTeam.id)
        assertEquals("https://owcdn.net/img/6399bb707aacb.png", detail.summary.homeTeam.imageUrl)
        assertEquals("KIWOOM DRX", detail.summary.awayTeam.name)
        assertEquals("8185", detail.summary.awayTeam.id)
        assertEquals("https://owcdn.net/img/6a353ee73ab25.png", detail.summary.awayTeam.imageUrl)
        assertEquals("2955", detail.summary.event.id)
        assertEquals(2, detail.summary.homeScore)
        assertEquals(0, detail.summary.awayScore)
        assertEquals("Bo3", detail.seriesFormat)
        assertEquals("KRX ban Ascent; NS ban Lotus", detail.description)
        assertEquals(listOf("Haven", "Breeze"), detail.maps.map { it.name })
        assertEquals(listOf(13, 13), detail.maps.map { it.homeScore })
        assertEquals(listOf(8, 9), detail.maps.map { it.awayScore })
        assertTrue(detail.headToHead.isEmpty())
        assertTrue(detail.pastMatches.isEmpty())

        val limited = parser.parseDetail(fixtureHtml("detail-limited.html"), detailUrl, "709685")
        assertTrue(limited.maps.isEmpty())
        assertNull(limited.summary.homeScore)
        assertNull(limited.summary.awayScore)
    }

    @Test
    fun `normalizes only supported detail team image URLs and preserves missing or unsafe values as null`() {
        val sources = mapOf(
            "//owcdn.net/img/home.png" to "https://owcdn.net/img/home.png",
            "/img/home.png" to "https://www.vlr.gg/img/home.png",
            "https://owcdn.net/img/home.png" to "https://owcdn.net/img/home.png",
            "HTTPS://owcdn.net/img/home.png" to "https://owcdn.net/img/home.png",
            "//" to null,
            "https://" to null,
            "https:///img/home.png" to null,
            "https://bad host/img/home.png" to null,
            "" to null,
            "http://owcdn.net/img/home.png" to null,
            "data:image/png;base64,abc" to null,
            "javascript:alert(1)" to null,
            "home.png" to null,
        )

        sources.forEach { (source, expected) ->
            val html = fixtureHtml("detail-completed.html").replace("//owcdn.net/img/6399bb707aacb.png", source)
            val detail = parser.parseDetail(html, detailUrl, "709685")

            assertEquals(expected, detail.summary.homeTeam.imageUrl, source)
            assertEquals("https://owcdn.net/img/6a353ee73ab25.png", detail.summary.awayTeam.imageUrl, source)
        }
    }

    @Test
    fun `scopes each team image to its validated detail team link`() {
        val html = fixtureHtml("detail-completed.html")
            .replace("<img src=\"//owcdn.net/img/6399bb707aacb.png\">", "")
            .replace(
                "<div class=\"match-header-vs-score\">",
                "<img src=\"//owcdn.net/img/not-a-team.png\"><div class=\"match-header-vs-score\">",
            )
            .replace(
                "<a href=\"/event/2955/esports-world-cup-2026-pacific-qualifier/stage-2\" class=\"match-header-event\">",
                "<a href=\"/event/2955/esports-world-cup-2026-pacific-qualifier/stage-2\" class=\"match-header-event\"><img src=\"//owcdn.net/img/event.png\">",
            )

        val detail = parser.parseDetail(html, detailUrl, "709685")

        assertNull(detail.summary.homeTeam.imageUrl)
        assertEquals("https://owcdn.net/img/6a353ee73ab25.png", detail.summary.awayTeam.imageUrl)
    }

    @Test
    fun `uses only mod prefixed status classes and reads unclassed score slots in team order`() {
        val detail = parser.parseDetail(fixtureHtml("detail-upcoming-unclassed-score.html"), detailUrl, "709685")

        assertEquals(MatchStatusSource.UPCOMING, detail.summary.status)
        assertEquals(12, detail.summary.homeScore)
        assertEquals(10, detail.summary.awayScore)
    }

    @Test
    fun `ignores blank detail notes and accepts canonical VLR link query fragments`() {
        val detail = parser.parseDetail(fixtureHtml("detail-parser-boundaries.html"), detailUrl, "709685")

        assertEquals(MatchStatusSource.COMPLETED, detail.summary.status)
        assertEquals("Bo3", detail.seriesFormat)
        assertEquals("Fixture Event", detail.summary.event.name)
        assertEquals("2955", detail.summary.event.id)
        assertEquals("11060", detail.summary.homeTeam.id)
        assertEquals("8185", detail.summary.awayTeam.id)
        assertEquals(listOf("700001"), detail.headToHead.map { it.id })
    }

    @Test
    fun `parses linked related sections in source order and skips rows without a stable match reference`() {
        val detail = parser.parseDetail(fixtureHtml("detail-related-matches.html"), detailUrl, "709685")

        assertEquals(listOf("700001", "700002"), detail.headToHead.map { it.id })
        assertEquals(listOf("Alpha", "Alpha"), detail.headToHead.map { it.homeTeamName })
        assertEquals(listOf("Bravo", "Bravo"), detail.headToHead.map { it.awayTeamName })
        assertEquals(listOf(2, 3), detail.headToHead.map { it.homeScore })
        assertEquals(listOf(1, 0), detail.headToHead.map { it.awayScore })

        assertEquals(listOf("700101", "700201"), detail.pastMatches.map { it.id })
        assertEquals(listOf("Alpha", "Bravo"), detail.pastMatches.map { it.homeTeamName })
        assertEquals(listOf("Delta", "Echo"), detail.pastMatches.map { it.awayTeamName })
        assertEquals(listOf(13, 8), detail.pastMatches.map { it.homeScore })
        assertEquals(listOf(10, 13), detail.pastMatches.map { it.awayScore })
    }

    @Test
    fun `uses the past match first marker instead of section order while preserving row order`() {
        val detail = parser.parseDetail(fixtureHtml("detail-past-reversed-history-order.html"), detailUrl, "709685")

        assertEquals(listOf("700501", "700601"), detail.pastMatches.map { it.id })
        assertEquals(listOf("Bravo", "Alpha"), detail.pastMatches.map { it.homeTeamName })
        assertEquals(listOf("Foxtrot", "Delta"), detail.pastMatches.map { it.awayTeamName })
        assertEquals(listOf(8, 13), detail.pastMatches.map { it.homeScore })
        assertEquals(listOf(13, 10), detail.pastMatches.map { it.awayScore })
    }

    @Test
    fun `keeps linked H2H rows when their score markup is absent or malformed`() {
        val detail = parser.parseDetail(fixtureHtml("detail-h2h-without-score.html"), detailUrl, "709685")

        assertEquals(listOf("700301", "700302"), detail.headToHead.map { it.id })
        assertEquals(listOf("Alpha", "Alpha"), detail.headToHead.map { it.homeTeamName })
        assertEquals(listOf("Bravo", "Bravo"), detail.headToHead.map { it.awayTeamName })
        assertEquals(listOf(null, null), detail.headToHead.map { it.homeScore })
        assertEquals(listOf(null, null), detail.headToHead.map { it.awayScore })
    }

    @Test
    fun `keeps linked past rows when their score markup is absent or malformed`() {
        val detail = parser.parseDetail(fixtureHtml("detail-past-without-score.html"), detailUrl, "709685")

        assertEquals(listOf("700401", "700402"), detail.pastMatches.map { it.id })
        assertEquals(listOf("Alpha", "Alpha"), detail.pastMatches.map { it.homeTeamName })
        assertEquals(listOf("Delta", "Echo"), detail.pastMatches.map { it.awayTeamName })
        assertEquals(listOf(null, null), detail.pastMatches.map { it.homeScore })
        assertEquals(listOf(null, null), detail.pastMatches.map { it.awayScore })
    }

    @Test
    fun `accepts BO1 BO3 BO5 and forfeit detail variants without inventing unavailable maps`() {
        val cases = listOf(
            SeriesCase("detail-bo1.html", "Bo1", 1, 1, 0),
            SeriesCase("detail-completed.html", "Bo3", 2, 2, 0),
            SeriesCase("detail-bo3-2-1.html", "Bo3", 3, 2, 1),
            SeriesCase("detail-bo5-3-1.html", "Bo5", 4, 3, 1),
            SeriesCase("detail-bo5-3-2.html", "Bo5", 5, 3, 2),
            SeriesCase("detail-ffw.html", "Bo3", 0, null, null),
        )

        cases.forEach { case ->
            val detail = parser.parseDetail(fixtureHtml(case.fixture), detailUrl, "709685")

            assertEquals(MatchStatusSource.COMPLETED, detail.summary.status, case.fixture)
            assertEquals(case.seriesFormat, detail.seriesFormat, case.fixture)
            assertEquals(case.homeScore, detail.summary.homeScore, case.fixture)
            assertEquals(case.awayScore, detail.summary.awayScore, case.fixture)
            assertEquals(case.mapCount, detail.maps.size, case.fixture)
        }
    }

    @Test
    fun `fails safely when a required match structure is broken`() {
        val failure = assertFailsWith<SourceParsingFailure> {
            parser.parseDetail(fixtureHtml("detail-broken.html"), detailUrl, "709685")
        }

        assertEquals("https://www.vlr.gg/", failure.canonicalUpstreamUrl)
        assertNotNull(failure.cause)
    }

    @Test
    fun `propagates parser cancellation without converting it to a source parsing failure`() {
        val cancellation = CancellationException("request cancelled")
        val cancellingParser = VlrMatchesParser { throw cancellation }

        val thrown = assertFailsWith<CancellationException> {
            cancellingParser.parseList("<html></html>", listUrl)
        }

        assertSame(cancellation, thrown)
    }

    private data class SeriesCase(
        val fixture: String,
        val seriesFormat: String,
        val mapCount: Int,
        val homeScore: Int?,
        val awayScore: Int?,
    )
}
