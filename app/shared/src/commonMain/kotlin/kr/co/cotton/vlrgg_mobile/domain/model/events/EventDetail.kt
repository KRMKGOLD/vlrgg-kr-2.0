package kr.co.cotton.vlrgg_mobile.domain.model.events

data class EventDetail(
    val id: String,
    val name: String,
    val status: EventStatus?,
    val dateLabel: String?,
    val location: String?,
    val series: String?,
    val description: String?,
    val imageUrl: String?,
)

data class EventStats(
    val availability: EventStatsAvailability,
    val players: List<EventPlayerStats>,
)

enum class EventStatsAvailability {
    AVAILABLE,
    NOT_AVAILABLE,
}

data class EventPlayerStats(
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
