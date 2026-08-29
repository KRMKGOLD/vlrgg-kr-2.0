package kr.co.cotton.vlrgg_mobile.data.remote.model.series

import kotlinx.serialization.Serializable
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventSummaryDto

@Serializable
internal data class SeriesDetailResponseDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val upcomingEvents: List<EventSummaryDto>,
    val completedEvents: List<EventSummaryDto>,
)
