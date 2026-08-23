package kr.co.cotton.vlrgg_mobile.data.remote.model.matches

import kotlinx.serialization.Serializable

@Serializable
internal data class MatchEventDto(
    val name: String,
    val series: String? = null,
    val id: String? = null,
)
