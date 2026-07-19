package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.http.*

internal data class EventListSource(
    val events: List<EventSummarySource>,
)

internal data class EventSummarySource(
    val id: String,
    val name: String,
    val status: EventStatusSource,
    val dateLabel: String?,
    val regionCode: String?,
    val imageUrl: String?,
)

internal enum class EventStatusSource {
    ONGOING,
    UPCOMING,
    COMPLETED,
    PAUSED,
}

internal data class EventDetailSource(
    val id: String,
    val name: String,
    val status: EventStatusSource?,
    val dateLabel: String?,
    val location: String?,
    val series: String?,
    val description: String?,
    val imageUrl: String?,
)

internal data class EventMatchesSource(
    val matches: List<EventMatchSource>,
)

internal data class EventMatchSource(
    val id: String,
    val status: EventMatchStatusSource,
    val timeLabel: String,
    val relativeTimeLabel: String?,
    val homeTeam: EventMatchTeamSource,
    val awayTeam: EventMatchTeamSource,
    val homeScore: Int?,
    val awayScore: Int?,
    val event: EventMatchEventSource,
)

internal enum class EventMatchStatusSource {
    UPCOMING,
    LIVE,
    COMPLETED,
    POSTPONED,
    CANCELLED,
    UNAVAILABLE,
}

internal data class EventMatchTeamSource(
    val name: String,
)

internal data class EventMatchEventSource(
    val name: String,
    val series: String?,
    val id: String,
)

internal data class EventNewsListSource(
    val news: List<EventNewsSource>,
)

internal data class EventNewsSource(
    val reference: String,
    val title: String,
    val author: String?,
    val publishedAt: String,
)

internal sealed interface EventStatsSource {
    data class Available(
        val players: List<EventPlayerStatsSource>,
    ) : EventStatsSource

    data object NoStatsAvailable : EventStatsSource
}

internal data class EventPlayerStatsSource(
    val playerId: String,
    val playerName: String,
    val teamAbbreviation: String?,
    val roundsPlayed: Int?,
    val rating: Double?,
    val averageCombatScore: Int?,
    val killDeathRatio: Double?,
    val averageDamagePerRound: Double?,
    val killAssistSurvivedTradedPercentage: Double?,
)

internal data class EventHtmlPage(
    val upstreamUrl: Url,
    val html: String,
)
