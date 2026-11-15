package kr.co.cotton.vlrgg_mobile.domain.model.team

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class TeamDetail(
    val id: String,
    val name: String,
    val tag: String?,
    val country: String?,
    val upcomingMatches: List<TeamMatch>,
    val recentMatches: List<TeamMatch>,
    val players: List<TeamRosterMember>,
    val staff: List<TeamRosterMember>,
    val news: List<TeamNews>,
    val logoUrl: String? = null,
)

data class TeamMatch(
    val id: String,
    val eventName: String?,
    val eventStage: String?,
    val teamName: String,
    val opponentName: String,
    val statusText: String?,
    val scheduledAtText: String?,
    /** [scheduledAtText]'s calendar date in the vlr.gg display time zone; null when not provided or unreadable. */
    val scheduledDate: LocalDate? = null,
    /** [scheduledAtText]'s time; null without a readable [scheduledDate]. */
    val scheduledTime: LocalTime? = null,
)

data class TeamRosterMember(
    val id: String,
    val handle: String,
    val realName: String?,
    val roleLabels: List<String>,
    val imageUrl: String? = null,
)

data class TeamNews(
    val articleId: String,
    val slug: String,
    val title: String,
    val publishedDateText: String?,
    val publishedDate: LocalDate? = null,
)
