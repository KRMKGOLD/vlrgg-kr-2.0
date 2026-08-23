package kr.co.cotton.vlrgg_mobile.data.remote.model.matches

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class MatchListCategoryDto {
    @SerialName("upcoming")
    UPCOMING,

    @SerialName("results")
    RESULTS,
}
