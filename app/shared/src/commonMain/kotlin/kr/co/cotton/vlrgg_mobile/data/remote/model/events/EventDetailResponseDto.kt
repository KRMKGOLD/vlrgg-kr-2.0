package kr.co.cotton.vlrgg_mobile.data.remote.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchSummaryDto

@Serializable
internal data class EventDetailResponseDto(
    val id: String,
    val name: String,
    val status: EventStatusDto? = null,
    val dateLabel: String? = null,
    val location: String? = null,
    val series: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)

@Serializable
internal data class EventMatchesResponseDto(
    val items: List<MatchSummaryDto>,
)

@Serializable
internal data class EventNewsListResponseDto(
    val items: List<EventNewsDto>,
)

@Serializable
internal data class EventNewsDto(
    val reference: String,
    val title: String,
    val author: String? = null,
    val publishedAt: String,
)

@Serializable
internal data class EventStatsResponseDto(
    val availability: EventStatsAvailabilityDto,
    val players: List<EventPlayerStatsDto>,
)

@Serializable
internal enum class EventStatsAvailabilityDto {
    @SerialName("available")
    AVAILABLE,

    @SerialName("not_available")
    NOT_AVAILABLE,
}

@Serializable
internal data class EventPlayerStatsDto(
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
