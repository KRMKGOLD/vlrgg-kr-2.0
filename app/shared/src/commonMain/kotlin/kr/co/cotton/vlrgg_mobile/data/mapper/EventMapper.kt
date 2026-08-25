package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventMatchesResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventPlayerStatsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsAvailabilityDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventSummaryDto
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventPlayerStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatsAvailability
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary

internal fun EventListResponseDto.toDomain(): EventList = EventList(
    ongoing = ongoing.map(EventSummaryDto::toDomain),
    upcoming = upcoming.map(EventSummaryDto::toDomain),
    completedOrPaused = completedOrPaused.map(EventSummaryDto::toDomain),
)

internal fun EventDetailResponseDto.toDomain(): EventDetail = EventDetail(
    id = id,
    name = name,
    status = status?.toDomain(),
    dateLabel = dateLabel,
    location = location,
    series = series,
    description = description,
    imageUrl = imageUrl,
)

internal fun EventMatchesResponseDto.toDomain(): List<MatchSummary> = items.map { it.toDomain() }

internal fun EventNewsListResponseDto.toDomain(): List<NewsSummary> = items.map(EventNewsDto::toDomain)

internal fun EventStatsResponseDto.toDomain(): EventStats = EventStats(
    availability = availability.toDomain(),
    players = players.map(EventPlayerStatsDto::toDomain),
)

private fun EventNewsDto.toDomain(): NewsSummary {
    val (articleId, slug) = reference.toArticleReferenceSegments()
    return NewsSummary(
        articleId = articleId,
        slug = slug,
        title = title,
        author = author,
        publishedAt = publishedAt,
    )
}

private fun EventStatsAvailabilityDto.toDomain(): EventStatsAvailability = when (this) {
    EventStatsAvailabilityDto.AVAILABLE -> EventStatsAvailability.AVAILABLE
    EventStatsAvailabilityDto.NOT_AVAILABLE -> EventStatsAvailability.NOT_AVAILABLE
}

private fun EventPlayerStatsDto.toDomain(): EventPlayerStats = EventPlayerStats(
    playerId = playerId,
    playerName = playerName,
    teamAbbreviation = teamAbbreviation,
    roundsPlayed = roundsPlayed,
    rating = rating,
    averageCombatScore = averageCombatScore,
    killDeathRatio = killDeathRatio,
    averageDamagePerRound = averageDamagePerRound,
    killAssistSurvivedTradedPercentage = killAssistSurvivedTradedPercentage,
)

private fun EventSummaryDto.toDomain(): EventSummary = EventSummary(
    id = id,
    name = name,
    status = status.toDomain(),
    dateLabel = dateLabel,
    regionCode = regionCode,
    imageUrl = imageUrl,
)

private fun EventStatusDto.toDomain(): EventStatus = when (this) {
    EventStatusDto.ONGOING -> EventStatus.ONGOING
    EventStatusDto.UPCOMING -> EventStatus.UPCOMING
    EventStatusDto.COMPLETED -> EventStatus.COMPLETED
    EventStatusDto.PAUSED -> EventStatus.PAUSED
}
