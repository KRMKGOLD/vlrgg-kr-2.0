package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventSummaryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.series.SeriesDetailResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeriesMapperTest {

    @Test
    fun detailPreservesSourceOrderAndNullableMetadata() {
        val detail = SeriesDetailResponseDto(
            id = "2",
            name = "Champions Tour",
            description = null,
            upcomingEvents = listOf(
                event(
                    id = "upcoming-2",
                    status = EventStatusDto.ONGOING,
                    dateLabel = "Sep 12",
                    regionCode = "INT",
                    imageUrl = "https://example.invalid/upcoming-2.png",
                ),
                event("upcoming-1"),
            ),
            completedEvents = listOf(event("completed-2"), event("completed-1")),
        ).toDomain()

        assertEquals("2", detail.id)
        assertEquals("Champions Tour", detail.name)
        assertNull(detail.description)
        assertEquals(listOf("upcoming-2", "upcoming-1"), detail.upcomingEvents.map { it.id })
        assertEquals(listOf("completed-2", "completed-1"), detail.completedEvents.map { it.id })
        with(detail.upcomingEvents.first()) {
            assertEquals("Event upcoming-2", name)
            assertEquals(kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus.ONGOING, status)
            assertEquals("Sep 12", dateLabel)
            assertEquals("INT", regionCode)
            assertEquals("https://example.invalid/upcoming-2.png", imageUrl)
        }
        assertNull(detail.upcomingEvents[1].dateLabel)
        assertNull(detail.upcomingEvents[1].regionCode)
        assertNull(detail.upcomingEvents[1].imageUrl)
    }

    @Test
    fun emptySectionsRemainSuccessfulEmptyLists() {
        val detail = SeriesDetailResponseDto("2", "Champions Tour", null, emptyList(), emptyList()).toDomain()

        assertEquals(emptyList(), detail.upcomingEvents)
        assertEquals(emptyList(), detail.completedEvents)
    }

    private fun event(
        id: String,
        status: EventStatusDto = EventStatusDto.UPCOMING,
        dateLabel: String? = null,
        regionCode: String? = null,
        imageUrl: String? = null,
    ) = EventSummaryDto(
        id = id,
        name = "Event $id",
        status = status,
        dateLabel = dateLabel,
        regionCode = regionCode,
        imageUrl = imageUrl,
    )
}
