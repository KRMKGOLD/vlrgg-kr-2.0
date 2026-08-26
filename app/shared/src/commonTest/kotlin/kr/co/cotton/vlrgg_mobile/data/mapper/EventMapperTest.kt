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

    private fun event(
        id: String,
        status: EventStatusDto,
        dateLabel: String? = "May 1—20",
        regionCode: String? = "kr",
        imageUrl: String? = "https://owcdn.net/img/masters.png",
    ) = EventSummaryDto(
        id = id,
        name = "Masters Seoul",
        status = status,
        dateLabel = dateLabel,
        regionCode = regionCode,
        imageUrl = imageUrl,
    )
}
