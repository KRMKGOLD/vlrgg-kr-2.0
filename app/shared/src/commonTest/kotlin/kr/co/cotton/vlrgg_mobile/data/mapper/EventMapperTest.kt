package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventSummaryDto
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
