package kr.co.cotton.vlrgg_mobile.data.mapper

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchDateGroupDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchEventDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchListCategoryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchMapDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.RelatedMatchDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchSummaryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchTeamDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchesPageResponseDto
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.scheduleLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MatchMapperTest {

    @Test
    fun responseMapsPageGroupsAndMatchesWithoutChangingOrder() {
        val response = MatchesPageResponseDto(
            category = MatchListCategoryDto.UPCOMING,
            page = 2,
            groups = listOf(
                group(dateLabel = "TODAY", matchIds = listOf("4001", "4002")),
                group(dateLabel = "TOMORROW", matchIds = listOf("4003")),
            ),
        )

        val page = response.toDomain()

        assertEquals(MatchListCategory.UPCOMING, page.category)
        assertEquals(2, page.page)
        assertEquals(listOf("TODAY", "TOMORROW"), page.groups.map { it.dateLabel })
        assertEquals(listOf("4001", "4002"), page.groups[0].matches.map { it.id })
        assertEquals(listOf("4003"), page.groups[1].matches.map { it.id })
    }

    @Test
    fun structuredDateAndTimeAreParsedAndInvalidOrMissingValuesBecomeNull() {
        val page = MatchesPageResponseDto(
            category = MatchListCategoryDto.UPCOMING,
            page = 1,
            groups = listOf(
                group(
                    dateLabel = "Tue, October 27, 2026",
                    matchIds = listOf("4001"),
                    date = "2026-10-27",
                    time = "03:00",
                ),
                group(dateLabel = "Wed, October 28, 2026", matchIds = listOf("4002"), date = "October 28", time = "TBD"),
                group(dateLabel = "TBD", matchIds = listOf("4003")),
            ),
        ).toDomain()

        assertEquals(LocalDate(2026, 10, 27), page.groups[0].date)
        assertEquals(LocalTime(3, 0), page.groups[0].matches.single().time)
        assertEquals("10:00 AM", page.groups[0].matches.single().timeLabel)
        assertNull(page.groups[1].date)
        assertNull(page.groups[1].matches.single().time)
        assertNull(page.groups[2].date)
        assertNull(page.groups[2].matches.single().time)
    }

    @Test
    fun oldServerDetailIgnoresWrongScheduledAtAndShowsTimeLabel() {
        val detail = detailDto(
            scheduledAt = "2026-10-26T14:00:00Z",
            scheduledAtUtc = null,
        ).toDomain()

        assertNull(detail.scheduledAtUtc)
        val label = detail.scheduleLabel(SEOUL)
        assertEquals("Tuesday, October 27 3:00 AM KST", label)
        assertFalse(label.contains("2026년 10월 26일 (월) 오후 11:00"))
    }

    @Test
    fun newServerDetailFormatsScheduledAtUtcInDeviceTimeZone() {
        val detail = detailDto(
            scheduledAt = "2026-10-26T18:00:00Z",
            scheduledAtUtc = "2026-10-26T18:00:00Z",
        ).toDomain()

        assertEquals(Instant.parse("2026-10-26T18:00:00Z"), detail.scheduledAtUtc)
        assertEquals("2026년 10월 27일 (화) 오전 3:00", detail.scheduleLabel(SEOUL))
    }

    @Test
    fun invalidDetailScheduledAtUtcFallsBackToTimeLabelInsteadOfFailing() {
        val detail = detailDto(
            scheduledAt = "2026-10-26T18:00:00Z",
            scheduledAtUtc = "2026-10-26 14:00:00",
        ).toDomain()

        assertNull(detail.scheduledAtUtc)
        assertEquals("Tuesday, October 27 3:00 AM KST", detail.timeLabel)
        assertEquals("Tuesday, October 27 3:00 AM KST", detail.scheduleLabel(SEOUL))
    }

    @Test
    fun optionalTeamEventAndScoreFieldsArePreserved() {
        val match = match(
            id = "4001",
            status = MatchStatusDto.LIVE,
            relativeTimeLabel = "2h 10m",
            awayTeamId = null,
            homeScore = null,
            awayScore = null,
            eventSeries = null,
            eventId = null,
        )

        val actual = MatchesPageResponseDto(
            category = MatchListCategoryDto.UPCOMING,
            page = 1,
            groups = listOf(MatchDateGroupDto("TODAY", listOf(match))),
        ).toDomain().groups.single().matches.single()

        assertEquals(MatchStatus.LIVE, actual.status)
        assertEquals("2h 10m", actual.relativeTimeLabel)
        assertEquals(null, actual.awayTeam.id)
        assertEquals(null, actual.homeTeam.imageUrl)
        assertEquals(null, actual.awayTeam.imageUrl)
        assertEquals(null, actual.homeScore)
        assertEquals(null, actual.awayScore)
        assertEquals(null, actual.event.series)
        assertEquals(null, actual.event.id)
    }

    @Test
    fun categoriesAndStatusesMapExplicitly() {
        val expectedCategories = listOf(
            MatchListCategory.UPCOMING,
            MatchListCategory.RESULTS,
        )
        val actualCategories = MatchListCategoryDto.entries.map { category ->
            MatchesPageResponseDto(category, page = 1, groups = emptyList()).toDomain().category
        }
        assertEquals(expectedCategories, actualCategories)

        val expectedStatuses = listOf(
            MatchStatus.UPCOMING,
            MatchStatus.LIVE,
            MatchStatus.COMPLETED,
            MatchStatus.POSTPONED,
            MatchStatus.CANCELLED,
            MatchStatus.UNAVAILABLE,
        )
        val response = MatchesPageResponseDto(
            category = MatchListCategoryDto.RESULTS,
            page = 1,
            groups = listOf(
                MatchDateGroupDto(
                    dateLabel = "RESULTS",
                    matches = MatchStatusDto.entries.mapIndexed { index, status ->
                        match(id = index.toString(), status = status)
                    },
                ),
            ),
        )

        val actualStatuses = response.toDomain().groups.single().matches.map { it.status }

        assertEquals(expectedStatuses, actualStatuses)
    }

    @Test
    fun detailMapsEveryServerFieldAndPreservesMapAndRelatedMatchOrder() {
        val detail = MatchDetailResponseDto(
            id = "7000",
            status = MatchStatusDto.COMPLETED,
            timeLabel = "2026-08-29 17:00",
            relativeTimeLabel = "1h ago",
            scheduledAt = "2026-08-29T06:00:00Z",
            scheduledAtUtc = "2026-08-29T08:00:00Z",
            homeTeam = MatchTeamDto(
                name = "Alpha",
                id = "alpha",
                imageUrl = "https://owcdn.net/img/alpha.png",
            ),
            awayTeam = MatchTeamDto(
                name = "Beta",
                id = null,
                imageUrl = "https://owcdn.net/img/beta.png",
            ),
            homeScore = 0,
            awayScore = null,
            event = MatchEventDto(name = "Champions", series = "Playoffs", id = null),
            description = "Grand final",
            seriesFormat = "Bo5",
            maps = listOf(
                MatchMapDto(name = "Lotus", homeScore = 0, awayScore = 13),
                MatchMapDto(name = "Haven", homeScore = null, awayScore = null),
            ),
            headToHead = listOf(
                RelatedMatchDto("6999", "Alpha", "Beta", 2, 0),
                RelatedMatchDto("6998", "Beta", "Alpha", null, null),
            ),
            pastMatches = listOf(
                RelatedMatchDto("6997", "Alpha", "Gamma", 13, 11),
                RelatedMatchDto("6996", "Beta", "Delta", null, 0),
            ),
        )

        val actual = detail.toDomain()

        assertEquals("7000", actual.id)
        assertEquals(MatchStatus.COMPLETED, actual.status)
        assertEquals("2026-08-29 17:00", actual.timeLabel)
        assertEquals("1h ago", actual.relativeTimeLabel)
        assertEquals(Instant.parse("2026-08-29T08:00:00Z"), actual.scheduledAtUtc)
        assertEquals("alpha", actual.homeTeam.id)
        assertEquals(null, actual.awayTeam.id)
        assertEquals("https://owcdn.net/img/alpha.png", actual.homeTeam.imageUrl)
        assertEquals("https://owcdn.net/img/beta.png", actual.awayTeam.imageUrl)
        assertEquals(0, actual.homeScore)
        assertEquals(null, actual.awayScore)
        assertEquals("Playoffs", actual.event.series)
        assertEquals(null, actual.event.id)
        assertEquals("Grand final", actual.description)
        assertEquals("Bo5", actual.seriesFormat)
        assertEquals(listOf("Lotus", "Haven"), actual.maps.map { it.name })
        assertEquals(0, actual.maps.first().homeScore)
        assertEquals(null, actual.maps.last().awayScore)
        assertEquals(listOf("6999", "6998"), actual.headToHead.map { it.id })
        assertEquals(listOf("6997", "6996"), actual.pastMatches.map { it.id })
        assertEquals(0, actual.pastMatches.last().awayScore)
    }

    @Test
    fun detailMapsAllStatusesWithoutOmission() {
        val actualStatuses = MatchStatusDto.entries.map { status ->
            MatchDetailResponseDto(
                id = status.name,
                status = status,
                timeLabel = "label",
                homeTeam = MatchTeamDto("Alpha"),
                awayTeam = MatchTeamDto("Beta"),
                event = MatchEventDto("Champions"),
                maps = emptyList(),
                headToHead = emptyList(),
                pastMatches = emptyList(),
            ).toDomain().status
        }

        assertEquals(MatchStatus.entries, actualStatuses)
    }

    private fun detailDto(
        scheduledAt: String?,
        scheduledAtUtc: String?,
    ) = MatchDetailResponseDto(
        id = "7001",
        status = MatchStatusDto.UPCOMING,
        timeLabel = "Tuesday, October 27 3:00 AM KST",
        scheduledAt = scheduledAt,
        scheduledAtUtc = scheduledAtUtc,
        homeTeam = MatchTeamDto(name = "Alpha"),
        awayTeam = MatchTeamDto(name = "Beta"),
        event = MatchEventDto(name = "Champions"),
        maps = emptyList(),
        headToHead = emptyList(),
        pastMatches = emptyList(),
    )

    private fun group(
        dateLabel: String,
        matchIds: List<String>,
        date: String? = null,
        time: String? = null,
    ) = MatchDateGroupDto(
        dateLabel = dateLabel,
        matches = matchIds.map { id -> match(id = id, time = time) },
        date = date,
    )

    private fun match(
        id: String,
        status: MatchStatusDto = MatchStatusDto.UPCOMING,
        relativeTimeLabel: String? = null,
        awayTeamId: String? = "beta",
        homeScore: Int? = 13,
        awayScore: Int? = 9,
        eventSeries: String? = "Playoffs",
        eventId: String? = "champions",
        time: String? = null,
    ) = MatchSummaryDto(
        id = id,
        status = status,
        timeLabel = "10:00 AM",
        time = time,
        relativeTimeLabel = relativeTimeLabel,
        homeTeam = MatchTeamDto(name = "Alpha", id = "alpha"),
        awayTeam = MatchTeamDto(name = "Beta", id = awayTeamId),
        homeScore = homeScore,
        awayScore = awayScore,
        event = MatchEventDto(
            name = "Champions",
            series = eventSeries,
            id = eventId,
        ),
    )

    private companion object {
        val SEOUL = TimeZone.of("Asia/Seoul")
    }
}
