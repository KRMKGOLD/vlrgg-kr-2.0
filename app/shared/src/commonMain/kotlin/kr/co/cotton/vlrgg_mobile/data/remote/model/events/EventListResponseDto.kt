package kr.co.cotton.vlrgg_mobile.data.remote.model.events

import kotlinx.serialization.Serializable

@Serializable
internal data class EventListResponseDto(
    val ongoing: List<EventSummaryDto>,
    val upcoming: List<EventSummaryDto>,
    val completedOrPaused: List<EventSummaryDto>,
)
