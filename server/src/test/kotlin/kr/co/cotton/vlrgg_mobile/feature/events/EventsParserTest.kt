package kr.co.cotton.vlrgg_mobile.feature.events

import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kotlin.test.*

class EventsParserTest {
    private val parser = EventsParser()

    @Test
    fun `event list parses operational status metadata image and empty state`() {
        val result = parser.parseEventList(fixture("event-list.html"))
        val empty = parser.parseEventList(fixture("event-list-empty.html"))

        assertEquals(4, result.events.size)
        assertEquals(EventStatusSource.ONGOING, result.events.single { it.id == "100" }.status)
        assertEquals("May 1—20", result.events.single { it.id == "100" }.dateLabel)
        assertEquals("kr", result.events.single { it.id == "100" }.regionCode)
        assertEquals("https://owcdn.net/img/masters.png", result.events.single { it.id == "100" }.imageUrl)
        assertEquals(EventStatusSource.UPCOMING, result.events.single { it.id == "200" }.status)
        assertEquals(EventStatusSource.COMPLETED, result.events.single { it.id == "300" }.status)
        assertEquals(EventStatusSource.PAUSED, result.events.single { it.id == "400" }.status)
        assertTrue(empty.events.isEmpty())
    }

    @Test
    fun `event list selector drift is a parsing failure rather than a false empty response`() {
        assertFailsWith<SourceParsingFailure> {
            parser.parseEventList(fixture("event-list-drifted.html"))
        }
    }

    @Test
    fun `event detail parses source metadata without inventing unavailable status`() {
        val result = parser.parseEventDetail(fixture("event-detail.html"), eventId = "100")

        assertEquals("100", result.id)
        assertEquals("Masters Seoul", result.name)
        assertNull(result.status)
        assertEquals("May 1 – May 20, 2026", result.dateLabel)
        assertEquals("Seoul", result.location)
        assertEquals("Valorant Champions Tour 2026", result.series)
        assertEquals("International competition.", result.description)
        assertEquals("https://owcdn.net/img/masters.png", result.imageUrl)
    }

    @Test
    fun `event status reads source attribute when visible text and modifier class are absent`() {
        val listHtml = fixture("event-list.html").replace(
            "<span class=\"event-item-desc-item-status mod-ongoing\">ongoing</span>",
            "<span class=\"event-item-desc-item-status\" data-event-status=\"ongoing\"></span>",
        )
        val detailHtml = fixture("event-detail.html").replace(
            "<h1 class=\"event-header-main-title\">Masters Seoul</h1>",
            "<h1 class=\"event-header-main-title\">Masters Seoul</h1><span data-event-status=\"ongoing\"></span>",
        )

        assertEquals(
            EventStatusSource.ONGOING,
            parser.parseEventList(listHtml).events.single { it.id == "100" }.status,
        )
        assertEquals(
            EventStatusSource.ONGOING,
            parser.parseEventDetail(detailHtml, "100").status,
        )
    }

    @Test
    fun `event matches parse all summaries statuses scores and absent scores`() {
        val result = parser.parseEventMatches(fixture("event-matches.html"), "100")
        val empty = parser.parseEventMatches(fixture("event-matches-empty.html"), "200")

        assertEquals(listOf("501", "502"), result.matches.map { it.id })
        assertEquals(EventMatchStatusSource.COMPLETED, result.matches.first().status)
        assertEquals(2, result.matches.first().homeScore)
        assertEquals(1, result.matches.first().awayScore)
        assertEquals(EventMatchStatusSource.UPCOMING, result.matches.last().status)
        assertNull(result.matches.last().homeScore)
        assertNull(result.matches.last().awayScore)
        assertEquals("1d 3h", result.matches.last().relativeTimeLabel)
        assertEquals(listOf("5:00 PM", "8:00 PM"), result.matches.map { it.timeLabel })
        assertEquals(listOf("17:00", "20:00"), result.matches.map { it.time })
        assertEquals("Masters Seoul", result.matches.first().event.name)
        assertEquals("100", result.matches.first().event.id)
        assertEquals("Playoffs Upper Final", result.matches.first().event.series)
        assertEquals("Group Stage Week 1", result.matches.last().event.series)
        assertTrue(empty.matches.isEmpty())
    }

    @Test
    fun `event matches mirror postponed cancelled and unavailable match statuses`() {
        val result = parser.parseEventMatches(
            fixture("event-matches-statuses.html"),
            "100",
        )

        assertEquals(
            listOf(
                EventMatchStatusSource.POSTPONED,
                EventMatchStatusSource.CANCELLED,
                EventMatchStatusSource.UNAVAILABLE,
            ),
            result.matches.map { it.status },
        )
        assertEquals(listOf("17:00", "20:00", null), result.matches.map { it.time })
    }

    @Test
    fun `event match count drift and missing required time are parsing failures`() {
        val fixture = fixture("event-matches.html")

        assertFailsWith<SourceParsingFailure> {
            parser.parseEventMatches(
                fixture.replace("<sup>(2)</sup>", "<sup>(3)</sup>"),
                "100",
            )
        }
        assertFailsWith<SourceParsingFailure> {
            parser.parseEventMatches(
                fixture.replace("<div class=\"match-item-time\">8:00 PM</div>", ""),
                "100",
            )
        }
    }

    @Test
    fun `event match keeps event identity when optional per-match series is absent`() {
        val fixture = fixture("event-matches.html").replace(
            "<div class=\"match-item-event\"><div class=\"match-item-event-series\">Playoffs</div>Upper Final</div>",
            "",
        )

        val match = parser.parseEventMatches(
            fixture,
            "100",
        ).matches.first()

        assertEquals("Masters Seoul", match.event.name)
        assertEquals("100", match.event.id)
        assertNull(match.event.series)
    }

    @Test
    fun `event news parses canonical references and normal empty state without inventing author`() {
        val populated = parser.parseEventNews(fixture("event-news.html"))
        val empty = parser.parseEventNews(fixture("event-news-empty.html"))

        assertEquals("701/masters-recap", populated.news.single().reference)
        assertEquals("Masters recap", populated.news.single().title)
        assertEquals("2026/05/20", populated.news.single().publishedAt)
        assertNull(populated.news.single().author)
        assertTrue(empty.news.isEmpty())
    }

    @Test
    fun `event stats parse operational data columns and normal no-stats state`() {
        val available = parser.parseEventStats(fixture("event-stats.html"))
        val unavailable = parser.parseEventStats(fixture("event-stats-empty.html"))

        val player = assertIs<EventStatsSource.Available>(available).players.first()
        assertEquals("10", player.playerId)
        assertEquals("Player One", player.playerName)
        assertEquals("ALP", player.teamAbbreviation)
        assertEquals(125, player.roundsPlayed)
        assertEquals(1.32, player.rating)
        assertEquals(82.0, player.killAssistSurvivedTradedPercentage)
        assertIs<EventStatsSource.NoStatsAvailable>(unavailable)
    }

    @Test
    fun `event stats reject non-finite numeric source values`() {
        val fixture = fixture("event-stats.html")
        val nanResult = parser.parseEventStats(fixture.replace(">1.32<", ">NaN<"))
        val infinityResult = parser.parseEventStats(
            fixture.replace(">1.32<", ">Infinity<"),
        )

        assertNull(assertIs<EventStatsSource.Available>(nanResult).players.first().rating)
        assertNull(assertIs<EventStatsSource.Available>(infinityResult).players.first().rating)
    }

    @Test
    fun `missing required resource structure maps to parsing failure`() {
        val failure = assertFailsWith<SourceParsingFailure> {
            parser.parseEventDetail(fixture("event-detail-broken.html"), eventId = "100")
        }

        assertIs<IllegalStateException>(failure.cause)
    }

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("events/$name"),
    ).readText()
}
