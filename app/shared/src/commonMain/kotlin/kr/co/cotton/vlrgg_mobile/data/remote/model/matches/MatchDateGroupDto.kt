package kr.co.cotton.vlrgg_mobile.data.remote.model.matches

import kotlinx.serialization.Serializable

@Serializable
internal data class MatchDateGroupDto(
    val dateLabel: String,
    val matches: List<MatchSummaryDto>,
)
