package kr.co.cotton.vlrgg_mobile.feature.events

import kr.co.cotton.vlrgg_mobile.feature.matches.MatchEventResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchSummaryResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchTeamResponse

internal class EventsMapper {
    fun toEventListResponse(source: EventListSource): EventListResponse = EventListResponse(
        ongoing = source.events.filter { it.status == EventStatusSource.ONGOING }.map(::toEventSummaryResponse),
        upcoming = source.events.filter { it.status == EventStatusSource.UPCOMING }.map(::toEventSummaryResponse),
        completedOrPaused = source.events
            .filter { it.status == EventStatusSource.COMPLETED || it.status == EventStatusSource.PAUSED }
            .map(::toEventSummaryResponse),
    )

    fun toEventDetailResponse(source: EventDetailSource): EventDetailResponse = EventDetailResponse(
        id = source.id,
        name = source.name,
        status = source.status?.toResponse(),
        dateLabel = source.dateLabel,
        location = source.location,
        series = source.series,
        description = source.description,
        imageUrl = source.imageUrl,
    )

    fun toEventMatchesResponse(source: EventMatchesSource): EventMatchesResponse = EventMatchesResponse(
        items = source.matches.map { match ->
            MatchSummaryResponse(
                id = match.id,
                status = match.status.toResponse(),
                timeLabel = match.timeLabel,
                relativeTimeLabel = match.relativeTimeLabel,
                homeTeam = MatchTeamResponse(name = match.homeTeam.name),
                awayTeam = MatchTeamResponse(name = match.awayTeam.name),
                homeScore = match.homeScore,
                awayScore = match.awayScore,
                event = MatchEventResponse(
                    name = match.event.name,
                    series = match.event.series,
                    id = match.event.id,
                ),
            )
        },
    )

    fun toEventNewsListResponse(source: EventNewsListSource): EventNewsListResponse = EventNewsListResponse(
        items = source.news.map { news ->
            EventNewsResponse(
                reference = news.reference,
                title = news.title,
                author = news.author,
                publishedAt = news.publishedAt,
            )
        },
    )

    fun toEventStatsResponse(source: EventStatsSource): EventStatsResponse = when (source) {
        is EventStatsSource.Available -> EventStatsResponse(
            availability = EventStatsAvailability.AVAILABLE,
            players = source.players.map { player ->
                EventPlayerStatsResponse(
                    playerId = player.playerId,
                    playerName = player.playerName,
                    teamAbbreviation = player.teamAbbreviation,
                    roundsPlayed = player.roundsPlayed,
                    rating = player.rating,
                    averageCombatScore = player.averageCombatScore,
                    killDeathRatio = player.killDeathRatio,
                    averageDamagePerRound = player.averageDamagePerRound,
                    killAssistSurvivedTradedPercentage = player.killAssistSurvivedTradedPercentage,
                )
            },
        )

        EventStatsSource.NoStatsAvailable -> EventStatsResponse(
            availability = EventStatsAvailability.NOT_AVAILABLE,
            players = emptyList(),
        )
    }

    private fun toEventSummaryResponse(source: EventSummarySource): EventSummaryResponse = EventSummaryResponse(
        id = source.id,
        name = source.name,
        status = source.status.toResponse(),
        dateLabel = source.dateLabel,
        regionCode = source.regionCode,
        imageUrl = source.imageUrl,
    )

    private fun EventStatusSource.toResponse(): EventStatus = when (this) {
        EventStatusSource.ONGOING -> EventStatus.ONGOING
        EventStatusSource.UPCOMING -> EventStatus.UPCOMING
        EventStatusSource.COMPLETED -> EventStatus.COMPLETED
        EventStatusSource.PAUSED -> EventStatus.PAUSED
    }

    private fun EventMatchStatusSource.toResponse(): MatchStatus = when (this) {
        EventMatchStatusSource.UPCOMING -> MatchStatus.UPCOMING
        EventMatchStatusSource.LIVE -> MatchStatus.LIVE
        EventMatchStatusSource.COMPLETED -> MatchStatus.COMPLETED
        EventMatchStatusSource.POSTPONED -> MatchStatus.POSTPONED
        EventMatchStatusSource.CANCELLED -> MatchStatus.CANCELLED
        EventMatchStatusSource.UNAVAILABLE -> MatchStatus.UNAVAILABLE
    }
}
