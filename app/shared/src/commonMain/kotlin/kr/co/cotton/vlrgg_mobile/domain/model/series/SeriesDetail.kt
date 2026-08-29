package kr.co.cotton.vlrgg_mobile.domain.model.series

import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary

data class SeriesDetail(
    val id: String,
    val name: String,
    val description: String?,
    val upcomingEvents: List<EventSummary>,
    val completedEvents: List<EventSummary>,
)
