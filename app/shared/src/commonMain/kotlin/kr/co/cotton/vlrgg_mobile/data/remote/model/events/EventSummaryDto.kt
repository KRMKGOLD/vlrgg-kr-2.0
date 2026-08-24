package kr.co.cotton.vlrgg_mobile.data.remote.model.events

import kotlinx.serialization.Serializable

@Serializable
internal data class EventSummaryDto(
    val id: String,
    val name: String,
    val status: EventStatusDto,
    val dateLabel: String? = null,
    val regionCode: String? = null,
    val imageUrl: String? = null,
)
