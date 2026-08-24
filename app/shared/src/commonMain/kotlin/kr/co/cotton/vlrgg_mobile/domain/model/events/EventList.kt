package kr.co.cotton.vlrgg_mobile.domain.model.events

data class EventList(
    val ongoing: List<EventSummary>,
    val upcoming: List<EventSummary>,
    val completedOrPaused: List<EventSummary>,
)
