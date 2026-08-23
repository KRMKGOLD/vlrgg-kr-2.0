package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchDateGroupDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchEventDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchListCategoryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchSummaryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchTeamDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchesPageResponseDto
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun group(
        dateLabel: String,
        matchIds: List<String>,
    ) = MatchDateGroupDto(
        dateLabel = dateLabel,
        matches = matchIds.map { id -> match(id = id) },
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
    ) = MatchSummaryDto(
        id = id,
        status = status,
        timeLabel = "10:00 AM",
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
}
