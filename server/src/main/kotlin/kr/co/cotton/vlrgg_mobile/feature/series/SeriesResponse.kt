package kr.co.cotton.vlrgg_mobile.feature.series

import kotlinx.serialization.Serializable
import kr.co.cotton.vlrgg_mobile.feature.events.EventSummaryResponse

/** App-facing Series detail. Event summaries deliberately share the Events API contract. */
@Serializable
internal data class SeriesResponse(
    val id: String,
    val name: String,
    val description: String?,
    val upcomingEvents: List<EventSummaryResponse>,
    val completedEvents: List<EventSummaryResponse>,
)
