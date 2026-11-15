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
import kotlinx.datetime.LocalDate
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventMonthDay
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

internal fun EventDetailResponseDto.toDomain(): EventDetail {
    val (start, end) = eventPeriod(startDate, endDate)
    return EventDetail(
        id = id,
        name = name,
        status = status?.toDomain(),
        dateLabel = dateLabel,
        startDate = start,
        endDate = end,
        location = location,
        series = series,
        description = description,
        imageUrl = imageUrl,
    )
}

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
        publishedDate = publishedDate?.let(LocalDate.Formats.ISO::parseOrNull),
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

internal fun EventSummaryDto.toDomain(): EventSummary {
    val (start, end) = eventPeriod(startDate, endDate)
    return EventSummary(
        id = id,
        name = name,
        status = status.toDomain(),
        dateLabel = dateLabel,
        startDate = start,
        endDate = end,
        regionCode = regionCode,
        imageUrl = imageUrl,
    )
}

/**
 * 기간 field 쌍을 해석한다. 시작을 해석하지 못하거나, 끝 값이 왔는데 해석하지 못하면 둘 다 null로 두어
 * 화면이 `dateLabel` 원문을 쓰게 한다. 끝 값이 없을 때(`TBD`)만 시작만 남긴다.
 */
private fun eventPeriod(startDate: String?, endDate: String?): Pair<EventMonthDay?, EventMonthDay?> {
    val start = startDate?.let(::parseIsoMonthDay) ?: return null to null
    if (endDate == null) return start to null
    val end = parseIsoMonthDay(endDate) ?: return null to null
    return start to end
}

/** ISO-8601 month-day `--MM-DD`. 달력에 없는 날짜(`--02-30`)는 null이다. */
internal fun parseIsoMonthDay(value: String): EventMonthDay? {
    val match = IsoMonthDay.matchEntire(value) ?: return null
    val month = match.groupValues[1].toInt()
    val day = match.groupValues[2].toInt()
    if (month !in 1..12 || day !in 1..MaxDaysInMonth[month - 1]) return null
    return EventMonthDay(month = month, day = day)
}

private val IsoMonthDay = Regex("""--(\d{2})-(\d{2})""")
private val MaxDaysInMonth = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

private fun EventStatusDto.toDomain(): EventStatus = when (this) {
    EventStatusDto.ONGOING -> EventStatus.ONGOING
    EventStatusDto.UPCOMING -> EventStatus.UPCOMING
    EventStatusDto.COMPLETED -> EventStatus.COMPLETED
    EventStatusDto.PAUSED -> EventStatus.PAUSED
}
