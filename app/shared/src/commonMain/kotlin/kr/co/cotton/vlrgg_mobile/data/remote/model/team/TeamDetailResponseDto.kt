package kr.co.cotton.vlrgg_mobile.data.remote.model.team

import kotlinx.serialization.Serializable

@Serializable
internal data class TeamDetailResponseDto(
    val id: String,
    val name: String,
    val tag: String? = null,
    val country: String? = null,
    val upcomingMatches: List<TeamMatchDto>,
    val recentMatches: List<TeamMatchDto>,
    val players: List<TeamRosterMemberDto>,
    val staff: List<TeamRosterMemberDto>,
    val news: List<TeamNewsDto>,
)

@Serializable
internal data class TeamMatchDto(
    val id: String,
    val eventName: String? = null,
    val eventStage: String? = null,
    val teamName: String,
    val opponentName: String,
    val statusText: String? = null,
    val scheduledAtText: String? = null,
)

@Serializable
internal data class TeamRosterMemberDto(
    val id: String,
    val handle: String,
    val realName: String? = null,
    val roleLabels: List<String>,
)

@Serializable
internal data class TeamNewsDto(
    val reference: String,
    val title: String,
    val publishedDateText: String? = null,
)
