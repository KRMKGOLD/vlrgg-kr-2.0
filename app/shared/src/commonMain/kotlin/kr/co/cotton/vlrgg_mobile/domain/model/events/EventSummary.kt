package kr.co.cotton.vlrgg_mobile.domain.model.events

data class EventSummary(
    val id: String,
    val name: String,
    val status: EventStatus,
    val dateLabel: String? = null,
    val regionCode: String? = null,
    val imageUrl: String? = null,
)
