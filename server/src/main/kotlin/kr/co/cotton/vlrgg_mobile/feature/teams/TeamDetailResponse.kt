package kr.co.cotton.vlrgg_mobile.feature.teams

import kotlinx.serialization.Serializable

/** Version 1 app-facing Team Detail contract. IDs are strings for stable navigation. */
@Serializable
internal data class TeamDetailResponse(
    val id: String,
    val name: String,
    val tag: String?,
    val country: String?,
    val upcomingMatches: List<TeamMatchResponse>,
    val recentMatches: List<TeamMatchResponse>,
    val players: List<TeamRosterMemberResponse>,
    val staff: List<TeamRosterMemberResponse>,
    val news: List<TeamNewsResponse>,
)

@Serializable
internal data class TeamMatchResponse(
    val id: String,
    val eventName: String?,
    val eventStage: String?,
    val teamName: String,
    val opponentName: String,
    val statusText: String?,
    val scheduledAtText: String?,
)

@Serializable
internal data class TeamRosterMemberResponse(
    val id: String,
    val handle: String,
    val realName: String?,
    val roleLabels: List<String>,
)

@Serializable
internal data class TeamNewsResponse(
    val id: String,
    val title: String,
    val publishedDateText: String?,
)
