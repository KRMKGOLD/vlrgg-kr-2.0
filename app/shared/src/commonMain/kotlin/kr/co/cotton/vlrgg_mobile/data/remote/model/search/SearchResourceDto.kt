package kr.co.cotton.vlrgg_mobile.data.remote.model.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class SearchResourceDto {
    @SerialName("series")
    SERIES,

    @SerialName("event")
    EVENT,

    @SerialName("team")
    TEAM,

    @SerialName("player")
    PLAYER,
}
