package kr.co.cotton.vlrgg_mobile.feature.player

/** Server-internal representation of a VLR player page. It is never serialized. */
internal data class PlayerDetailSource(
    val profile: PlayerProfileSource,
    val currentTeam: PlayerTeamSource?,
    val agentStats: List<AgentStatSource>,
    val recentMatches: List<PlayerRecentMatchSource>,
)

internal data class PlayerProfileSource(
    val handle: String,
    val realName: String?,
    val aliases: List<String>,
    val countryCode: String?,
    val countryName: String?,
    val imageUrl: String? = null,
)

internal data class PlayerTeamSource(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
)

internal data class AgentStatSource(
    val agentName: String,
    val mapsPlayed: Int,
    val pickRatePercent: Int?,
    val roundsPlayed: Int?,
    val rating: Double?,
    val averageCombatScore: Double?,
    val killDeathRatio: Double?,
    val kastPercent: Int?,
    val averageDamagePerRound: Double?,
    val killsPerRound: Double?,
    val assistsPerRound: Double?,
    val firstKillDeathRatio: Double?,
    val kills: Int?,
    val deaths: Int?,
    val assists: Int?,
    val firstKills: Int?,
    val firstDeaths: Int?,
)

internal data class PlayerRecentMatchSource(
    val id: String,
    val eventName: String,
    val eventStage: String?,
    val teamA: PlayerMatchTeamSource,
    val teamB: PlayerMatchTeamSource,
    val teamAScore: Int?,
    val teamBScore: Int?,
    val outcome: PlayerMatchOutcomeSource,
    val playedOn: String?,
)

internal data class PlayerMatchTeamSource(val name: String, val tag: String?)

internal enum class PlayerMatchOutcomeSource { WIN, LOSS, UNKNOWN }
