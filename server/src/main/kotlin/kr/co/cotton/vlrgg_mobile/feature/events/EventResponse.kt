package kr.co.cotton.vlrgg_mobile.feature.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchSummaryResponse

@Serializable
data class EventListResponse(
    val ongoing: List<EventSummaryResponse>,
    val upcoming: List<EventSummaryResponse>,
    val completedOrPaused: List<EventSummaryResponse>,
)

@Serializable
data class EventSummaryResponse(
    val id: String,
    val name: String,
    val status: EventStatus,
    val dateLabel: String? = null,
    val regionCode: String? = null,
    val imageUrl: String? = null,
)

@Serializable
enum class EventStatus {
    @SerialName("ongoing")
    ONGOING,

    @SerialName("upcoming")
    UPCOMING,

    @SerialName("completed")
    COMPLETED,

    @SerialName("paused")
    PAUSED,
}

@Serializable
data class EventDetailResponse(
    val id: String,
    val name: String,
    /** VLR.GG does not expose an Event status on every detail page. */
    val status: EventStatus? = null,
    val dateLabel: String? = null,
    val location: String? = null,
    val series: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class EventMatchesResponse(
    val items: List<MatchSummaryResponse>,
)

@Serializable
data class EventNewsListResponse(
    val items: List<EventNewsResponse>,
)

@Serializable
data class EventNewsResponse(
    /** Canonical relative article path, suitable for /api/v1/news/{reference}. */
    val reference: String,
    val title: String,
    /** Event news pages do not always expose an author. */
    val author: String? = null,
    val publishedAt: String,
)

@Serializable
data class EventStatsResponse(
    val availability: EventStatsAvailability,
    val players: List<EventPlayerStatsResponse>,
)

@Serializable
enum class EventStatsAvailability {
    @SerialName("available")
    AVAILABLE,

    @SerialName("not_available")
    NOT_AVAILABLE,
}

@Serializable
data class EventPlayerStatsResponse(
    val playerId: String,
    val playerName: String,
    val teamAbbreviation: String? = null,
    val roundsPlayed: Int? = null,
    val rating: Double? = null,
    val averageCombatScore: Int? = null,
    val killDeathRatio: Double? = null,
    val averageDamagePerRound: Double? = null,
    val killAssistSurvivedTradedPercentage: Double? = null,
)
