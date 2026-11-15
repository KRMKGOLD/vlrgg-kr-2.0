package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventSummaryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventPlayerStatsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsAvailabilityDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsResponseDto
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatsAvailability
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kotlinx.datetime.LocalDate
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventMatchesResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchEventDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchSummaryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchTeamDto
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventMonthDay
import kotlin.test.Test
import kotlin.test.assertEquals

class EventMapperTest {

    @Test
    fun responseMapsEachStatusGroupWithoutChangingOrder() {
        val response = EventListResponseDto(
            ongoing = listOf(event(id = "100", status = EventStatusDto.ONGOING)),
            upcoming = listOf(event(id = "200", status = EventStatusDto.UPCOMING)),
            completedOrPaused = listOf(
                event(id = "300", status = EventStatusDto.COMPLETED),
                event(id = "400", status = EventStatusDto.PAUSED),
            ),
        )

        val events = response.toDomain()

        assertEquals(listOf("100"), events.ongoing.map { it.id })
        assertEquals(listOf("200"), events.upcoming.map { it.id })
        assertEquals(listOf("300", "400"), events.completedOrPaused.map { it.id })
    }

    @Test
    fun optionalSummaryFieldsArePreservedWithoutSynthesizingDefaults() {
        val event = EventListResponseDto(
            ongoing = listOf(
                event(
                    id = "100",
                    status = EventStatusDto.ONGOING,
                    dateLabel = null,
                    regionCode = null,
                    imageUrl = null,
                ),
            ),
            upcoming = emptyList(),
            completedOrPaused = emptyList(),
        ).toDomain().ongoing.single()

        assertEquals(EventStatus.ONGOING, event.status)
        assertEquals(null, event.dateLabel)
        assertEquals(null, event.regionCode)
        assertEquals(null, event.imageUrl)
    }

    @Test
    fun statusValuesMapExplicitly() {
        val response = EventListResponseDto(
            ongoing = listOf(event(id = "1", status = EventStatusDto.ONGOING)),
            upcoming = listOf(event(id = "2", status = EventStatusDto.UPCOMING)),
            completedOrPaused = listOf(
                event(id = "3", status = EventStatusDto.COMPLETED),
                event(id = "4", status = EventStatusDto.PAUSED),
            ),
        )

        val statuses = response.toDomain().let { events ->
            events.ongoing + events.upcoming + events.completedOrPaused
        }.map { it.status }

        assertEquals(
            listOf(EventStatus.ONGOING, EventStatus.UPCOMING, EventStatus.COMPLETED, EventStatus.PAUSED),
            statuses,
        )
    }

    @Test
    fun detailAndNewsPreserveOptionalValuesAndParseCanonicalReference() {
        val detail = EventDetailResponseDto(
            id = "100",
            name = "Masters Seoul",
            status = null,
            dateLabel = null,
            location = null,
            series = "VCT 2026",
            description = null,
            imageUrl = null,
        ).toDomain()
        val news = EventNewsListResponseDto(
            items = listOf(
                EventNewsDto(
                    reference = "101/masters-seoul",
                    title = "Masters Seoul begins",
                    author = null,
                    publishedAt = "2026-08-25",
                ),
            ),
        ).toDomain().single()

        assertEquals(null, detail.status)
        assertEquals(null, detail.location)
        assertEquals("101", news.articleId)
        assertEquals("masters-seoul", news.slug)
        assertEquals(null, news.author)
    }

    @Test
    fun statsAvailabilityAndNullMetricsAreNotSynthesized() {
        val stats = EventStatsResponseDto(
            availability = EventStatsAvailabilityDto.AVAILABLE,
            players = listOf(
                EventPlayerStatsDto(
                    playerId = "player-1",
                    playerName = "Meteor",
                    teamAbbreviation = null,
                    roundsPlayed = null,
                    rating = null,
                    averageCombatScore = null,
                    killDeathRatio = null,
                    averageDamagePerRound = null,
                    killAssistSurvivedTradedPercentage = null,
                ),
            ),
        ).toDomain()

        assertEquals(EventStatsAvailability.AVAILABLE, stats.availability)
        assertEquals(null, stats.players.single().roundsPlayed)
        assertEquals(null, stats.players.single().rating)
        assertEquals(null, stats.players.single().teamAbbreviation)
    }

    @Test
    fun malformedEventNewsReferenceFailsMapping() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            EventNewsListResponseDto(
                items = listOf(EventNewsDto("invalid", "Title", null, "2026-08-25")),
            ).toDomain()
        }
    }

    @Test
    fun periodFieldsParseIsoMonthDayAndKeepAnUndecidedEnd() {
        fun period(start: String?, end: String?) = EventListResponseDto(
            ongoing = listOf(event(id = "1", status = EventStatusDto.ONGOING, startDate = start, endDate = end)),
            upcoming = emptyList(),
            completedOrPaused = emptyList(),
        ).toDomain().ongoing.single().let { it.startDate to it.endDate }

        assertEquals(EventMonthDay(9, 12) to EventMonthDay(10, 4), period("--09-12", "--10-04"))
        assertEquals(EventMonthDay(12, 17) to EventMonthDay(12, 17), period("--12-17", "--12-17"))
        assertEquals(EventMonthDay(2, 29) to EventMonthDay(3, 1), period("--02-29", "--03-01"))
        // `Nov 1—TBD`: only the end is unknown.
        assertEquals(EventMonthDay(11, 1) to null, period("--11-01", null))
        // Missing or unreadable values drop the whole period, so the screen falls back to `dateLabel`.
        assertEquals(null to null, period(null, null))
        assertEquals(null to null, period(null, "--10-04"))
        assertEquals(null to null, period("--09-12", "--02-30"))
        listOf("09-12", "--9-12", "--13-01", "--00-10", "--04-31", "2026-09-12", "--09-12 ").forEach { invalid ->
            assertEquals(null to null, period(invalid, "--10-04"), "'$invalid' must not parse")
        }
    }

    @Test
    fun detailPeriodMatchesAndNewsDatesParseKoreanDisplayFields() {
        val detail = EventDetailResponseDto(
            id = "100",
            name = "Masters Seoul",
            dateLabel = "Sep 13 – Dec 25, 2026",
            startDate = "--09-13",
            endDate = "--12-25",
        ).toDomain()
        val matches = EventMatchesResponseDto(
            items = listOf(
                matchDto(id = "m1", date = "2026-09-13"),
                matchDto(id = "m2", date = null),
                matchDto(id = "m3", date = "Sun, September 13"),
            ),
        ).toDomain()
        val news = EventNewsListResponseDto(
            items = listOf(
                EventNewsDto("101/a", "A", null, "2026/09/15", publishedDate = "2026-09-15"),
                EventNewsDto("102/b", "B", null, "2026/09/16"),
                EventNewsDto("103/c", "C", null, "2026/02/30", publishedDate = "2026-02-30"),
            ),
        ).toDomain()

        assertEquals(EventMonthDay(9, 13) to EventMonthDay(12, 25), detail.startDate to detail.endDate)
        assertEquals("Sep 13 – Dec 25, 2026", detail.dateLabel)
        assertEquals(listOf(LocalDate(2026, 9, 13), null, null), matches.map { it.date })
        assertEquals(listOf(LocalDate(2026, 9, 15), null, null), news.map { it.publishedDate })
        assertEquals("2026/09/16", news[1].publishedAt)
    }

    private fun matchDto(id: String, date: String?) = MatchSummaryDto(
        id = id,
        status = MatchStatusDto.COMPLETED,
        timeLabel = "6:00 PM",
        time = "18:00",
        date = date,
        homeTeam = MatchTeamDto(name = "Alpha"),
        awayTeam = MatchTeamDto(name = "Beta"),
        event = MatchEventDto(name = "Masters Seoul", series = "Playoffs", id = "100"),
    )

    private fun event(
        id: String,
        status: EventStatusDto,
        dateLabel: String? = "May 1—20",
        regionCode: String? = "kr",
        imageUrl: String? = "https://owcdn.net/img/masters.png",
        startDate: String? = null,
        endDate: String? = null,
    ) = EventSummaryDto(
        id = id,
        name = "Masters Seoul",
        status = status,
        dateLabel = dateLabel,
        startDate = startDate,
        endDate = endDate,
        regionCode = regionCode,
        imageUrl = imageUrl,
    )
}
