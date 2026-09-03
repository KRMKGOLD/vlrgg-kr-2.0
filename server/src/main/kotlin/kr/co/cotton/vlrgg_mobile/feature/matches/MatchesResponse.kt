package kr.co.cotton.vlrgg_mobile.feature.matches

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Public API contract for `GET /api/v1/matches/{upcoming|results}`. */
@Serializable
data class MatchesPageResponse(
    val category: MatchListCategory,
    val page: Int,
    val groups: List<MatchDateGroupResponse>,
)

@Serializable
data class MatchDateGroupResponse(
    val dateLabel: String,
    val matches: List<MatchSummaryResponse>,
)

@Serializable
data class MatchSummaryResponse(
    val id: String,
    val status: MatchStatus,
    val timeLabel: String,
    val relativeTimeLabel: String? = null,
    val homeTeam: MatchTeamResponse,
    val awayTeam: MatchTeamResponse,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val event: MatchEventResponse,
)

/** Public API contract for `GET /api/v1/matches/{matchId}`. */
@Serializable
data class MatchDetailResponse(
    val id: String,
    val status: MatchStatus,
    val timeLabel: String,
    val relativeTimeLabel: String? = null,
    val scheduledAt: String? = null,
    val homeTeam: MatchTeamResponse,
    val awayTeam: MatchTeamResponse,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val event: MatchEventResponse,
    val description: String? = null,
    val seriesFormat: String? = null,
    val maps: List<MatchMapResponse>,
    val headToHead: List<RelatedMatchResponse>,
    val pastMatches: List<RelatedMatchResponse>,
)

@Serializable
data class MatchTeamResponse(
    val name: String,
    val id: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class MatchEventResponse(
    val name: String,
    val series: String? = null,
    val id: String? = null,
)

@Serializable
data class MatchMapResponse(
    val name: String,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
)

@Serializable
data class RelatedMatchResponse(
    val id: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
)

@Serializable
enum class MatchListCategory {
    @SerialName("upcoming")
    UPCOMING,

    @SerialName("results")
    RESULTS,
}

@Serializable
enum class MatchStatus {
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
