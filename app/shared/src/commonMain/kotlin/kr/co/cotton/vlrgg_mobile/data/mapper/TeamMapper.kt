package kr.co.cotton.vlrgg_mobile.data.mapper

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
    logoUrl = logoUrl,
)

private fun TeamMatchDto.toDomain(): TeamMatch {
    // The contract nulls both values when the date is unreadable, so a time never stands alone.
    val date = scheduledDate?.let(LocalDate.Formats.ISO::parseOrNull)
    return TeamMatch(
        id = id,
        eventName = eventName,
        eventStage = eventStage,
        teamName = teamName,
        opponentName = opponentName,
        statusText = statusText,
        scheduledAtText = scheduledAtText,
        scheduledDate = date,
        scheduledTime = date?.let { scheduledTime?.let(LocalTime.Formats.ISO::parseOrNull) },
    )
}

private fun TeamRosterMemberDto.toDomain(): TeamRosterMember = TeamRosterMember(
    id = id,
    handle = handle,
    realName = realName,
    roleLabels = roleLabels,
    imageUrl = imageUrl,
)

private fun TeamNewsDto.toDomain(): TeamNews {
    val (articleId, slug) = reference.toArticleReferenceSegments()

    return TeamNews(
        articleId = articleId,
        slug = slug,
        title = title,
        publishedDateText = publishedDateText,
        publishedDate = publishedDate?.let(LocalDate.Formats.ISO::parseOrNull),
    )
}
