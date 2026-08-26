package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamMatchDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamNewsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamRosterMemberDto
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember

internal fun TeamDetailResponseDto.toDomain(): TeamDetail = TeamDetail(
    id = id,
    name = name,
    tag = tag,
    country = country,
    upcomingMatches = upcomingMatches.map(TeamMatchDto::toDomain),
    recentMatches = recentMatches.map(TeamMatchDto::toDomain),
    players = players.map(TeamRosterMemberDto::toDomain),
    staff = staff.map(TeamRosterMemberDto::toDomain),
    news = news.map(TeamNewsDto::toDomain),
)

private fun TeamMatchDto.toDomain(): TeamMatch = TeamMatch(
    id = id,
    eventName = eventName,
    eventStage = eventStage,
    teamName = teamName,
    opponentName = opponentName,
    statusText = statusText,
    scheduledAtText = scheduledAtText,
)

private fun TeamRosterMemberDto.toDomain(): TeamRosterMember = TeamRosterMember(
    id = id,
    handle = handle,
    realName = realName,
    roleLabels = roleLabels,
)

private fun TeamNewsDto.toDomain(): TeamNews {
    val (articleId, slug) = reference.toArticleReferenceSegments()

    return TeamNews(
        articleId = articleId,
        slug = slug,
        title = title,
        publishedDateText = publishedDateText,
    )
}
