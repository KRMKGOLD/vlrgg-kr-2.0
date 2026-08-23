package kr.co.cotton.vlrgg_mobile.data.remote.model.matches

import kotlinx.serialization.Serializable

@Serializable
internal data class MatchSummaryDto(
    val id: String,
    val status: MatchStatusDto,
    val timeLabel: String,
    val relativeTimeLabel: String? = null,
    val homeTeam: MatchTeamDto,
    val awayTeam: MatchTeamDto,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val event: MatchEventDto,
)
