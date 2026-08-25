package kr.co.cotton.vlrgg_mobile.data.remote.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class EventStatusDto {
    @SerialName("ongoing")
    ONGOING,

    @SerialName("upcoming")
    UPCOMING,

    @SerialName("completed")
    COMPLETED,

    @SerialName("paused")
    PAUSED,
}
