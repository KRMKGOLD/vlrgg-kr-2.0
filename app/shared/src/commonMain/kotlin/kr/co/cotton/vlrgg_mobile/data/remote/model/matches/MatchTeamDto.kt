package kr.co.cotton.vlrgg_mobile.data.remote.model.matches

import kotlinx.serialization.Serializable

@Serializable
internal data class MatchTeamDto(
    val name: String,
    val id: String? = null,
    val imageUrl: String? = null,
)
