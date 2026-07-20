package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.feature.news.NewsReference

/** Raw, feature-internal material returned by the Team overview and news pages. */
internal data class TeamDetailUpstreamContent(
    val overviewHtml: String,
    val newsHtml: String,
    val overviewUrl: Url,
    val newsUrl: Url,
)

/** Server-internal representation of VLR.GG's Team pages. It is never serialized. */
internal data class TeamDetailSource(
    val profile: TeamProfileSource,
    val upcomingMatches: List<TeamMatchSource>,
    val recentMatches: List<TeamMatchSource>,
    val players: List<TeamRosterMemberSource>,
    val staff: List<TeamRosterMemberSource>,
    val news: List<TeamNewsSource>,
)

internal data class TeamProfileSource(
    val name: String,
    val tag: String?,
    val country: String?,
)

internal data class TeamMatchSource(
    val id: String,
    val eventName: String?,
    val eventStage: String?,
    val teamName: String,
    val opponentName: String,
    val statusText: String?,
    val scheduledAtText: String?,
)

internal data class TeamRosterMemberSource(
    val id: String,
    val handle: String,
    val realName: String?,
    val roleLabels: List<String>,
)

internal data class TeamNewsSource(
    val reference: NewsReference,
    val title: String,
    val publishedDateText: String?,
)
