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
    /** ISO-8601 local date of [dateLabel] in VLR.GG's display time zone; not an instant. */
    val date: String? = null,
    val matches: List<MatchSummaryResponse>,
)

@Serializable
data class MatchSummaryResponse(
    val id: String,
    val status: MatchStatus,
    val timeLabel: String,
    /** ISO-8601 local time (`HH:mm`) of [timeLabel] in VLR.GG's display time zone; not an instant. */
    val time: String? = null,
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
    /** Scheduled start as an ISO-8601 UTC instant, e.g. `2026-10-26T18:00:00Z`. */
    val scheduledAt: String? = null,
    /**
     * Same instant as [scheduledAt]. Servers before the US Eastern fix send a valid but 4-5 hour early
     * [scheduledAt] and no `scheduledAtUtc`, so clients that need a trustworthy instant must read this field.
     */
    val scheduledAtUtc: String? = null,
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
