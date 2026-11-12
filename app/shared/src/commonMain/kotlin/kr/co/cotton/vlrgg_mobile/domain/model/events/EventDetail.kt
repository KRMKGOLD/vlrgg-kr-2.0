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
    /** 기간 시작. 서버가 주지 않거나 형식을 알 수 없으면 [endDate]와 함께 null이다. */
    val startDate: EventMonthDay? = null,
    /** 기간 끝. 끝이 미정(`TBD`)이면 [startDate]만 있고 이 값은 null이다. */
    val endDate: EventMonthDay? = null,
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
