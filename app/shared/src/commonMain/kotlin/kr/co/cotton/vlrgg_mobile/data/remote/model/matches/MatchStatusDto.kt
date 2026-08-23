package kr.co.cotton.vlrgg_mobile.data.remote.model.matches

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class MatchStatusDto {
    @SerialName("upcoming")
    UPCOMING,

    @SerialName("live")
    LIVE,

    @SerialName("completed")
    COMPLETED,

    @SerialName("postponed")
    POSTPONED,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("unavailable")
    UNAVAILABLE,
}
