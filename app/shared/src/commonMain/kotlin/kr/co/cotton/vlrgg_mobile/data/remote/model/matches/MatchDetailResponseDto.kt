package kr.co.cotton.vlrgg_mobile.data.remote.model.matches

import kotlinx.serialization.Serializable

@Serializable
internal data class MatchDetailResponseDto(
    val id: String,
    val status: MatchStatusDto,
    val timeLabel: String,
    val relativeTimeLabel: String? = null,
    val scheduledAt: String? = null,
    val homeTeam: MatchTeamDto,
    val awayTeam: MatchTeamDto,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val event: MatchEventDto,
    val description: String? = null,
    val seriesFormat: String? = null,
    val maps: List<MatchMapDto>,
    val headToHead: List<RelatedMatchDto>,
    val pastMatches: List<RelatedMatchDto>,
)

@Serializable
internal data class MatchMapDto(
    val name: String,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
)

@Serializable
internal data class RelatedMatchDto(
    val id: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
)
