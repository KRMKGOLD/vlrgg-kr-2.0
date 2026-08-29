package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchDateGroupDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchEventDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchListCategoryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchSummaryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchTeamDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchesPageResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchMapDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.RelatedMatchDto
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchPage
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchMap
import kr.co.cotton.vlrgg_mobile.domain.model.matches.RelatedMatch

internal fun MatchesPageResponseDto.toDomain(): MatchPage = MatchPage(
    category = category.toDomain(),
    page = page,
    groups = groups.map(MatchDateGroupDto::toDomain),
)

private fun MatchDateGroupDto.toDomain(): MatchDateGroup = MatchDateGroup(
    dateLabel = dateLabel,
    matches = matches.map(MatchSummaryDto::toDomain),
)

internal fun MatchSummaryDto.toDomain(): MatchSummary = MatchSummary(
    id = id,
    status = status.toDomain(),
    timeLabel = timeLabel,
    relativeTimeLabel = relativeTimeLabel,
    homeTeam = homeTeam.toDomain(),
    awayTeam = awayTeam.toDomain(),
    homeScore = homeScore,
    awayScore = awayScore,
    event = event.toDomain(),
)

internal fun MatchDetailResponseDto.toDomain(): MatchDetail = MatchDetail(
    id = id,
    status = status.toDomain(),
    timeLabel = timeLabel,
    relativeTimeLabel = relativeTimeLabel,
    scheduledAt = scheduledAt,
    homeTeam = homeTeam.toDomain(),
    awayTeam = awayTeam.toDomain(),
    homeScore = homeScore,
    awayScore = awayScore,
    event = event.toDomain(),
    description = description,
    seriesFormat = seriesFormat,
    maps = maps.map(MatchMapDto::toDomain),
    headToHead = headToHead.map(RelatedMatchDto::toDomain),
    pastMatches = pastMatches.map(RelatedMatchDto::toDomain),
)

private fun MatchTeamDto.toDomain(): MatchTeam = MatchTeam(
    name = name,
    id = id,
)

private fun MatchEventDto.toDomain(): MatchEvent = MatchEvent(
    name = name,
    series = series,
    id = id,
)

private fun MatchMapDto.toDomain(): MatchMap = MatchMap(
    name = name,
    homeScore = homeScore,
    awayScore = awayScore,
)

private fun RelatedMatchDto.toDomain(): RelatedMatch = RelatedMatch(
    id = id,
    homeTeamName = homeTeamName,
    awayTeamName = awayTeamName,
    homeScore = homeScore,
    awayScore = awayScore,
)

private fun MatchListCategoryDto.toDomain(): MatchListCategory = when (this) {
    MatchListCategoryDto.UPCOMING -> MatchListCategory.UPCOMING
    MatchListCategoryDto.RESULTS -> MatchListCategory.RESULTS
}

private fun MatchStatusDto.toDomain(): MatchStatus = when (this) {
    MatchStatusDto.UPCOMING -> MatchStatus.UPCOMING
    MatchStatusDto.LIVE -> MatchStatus.LIVE
    MatchStatusDto.COMPLETED -> MatchStatus.COMPLETED
    MatchStatusDto.POSTPONED -> MatchStatus.POSTPONED
    MatchStatusDto.CANCELLED -> MatchStatus.CANCELLED
    MatchStatusDto.UNAVAILABLE -> MatchStatus.UNAVAILABLE
}
