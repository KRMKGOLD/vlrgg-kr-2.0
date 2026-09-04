package kr.co.cotton.vlrgg_mobile.feature.player

import kotlinx.serialization.Serializable

/** Version 1 app-facing Player Detail contract. IDs are strings for direct navigation. */
@Serializable
internal data class PlayerDetailResponse(
    val id: String,
    val profile: PlayerProfileResponse,
    val currentTeam: PlayerTeamResponse?,
    val agentStats: List<PlayerAgentStatResponse>,
    val recentMatches: List<PlayerRecentMatchResponse>,
)

@Serializable
internal data class PlayerProfileResponse(
    val handle: String,
    val realName: String?,
    val aliases: List<String>,
    val countryCode: String?,
    val countryName: String?,
    val imageUrl: String? = null,
)

@Serializable
internal data class PlayerTeamResponse(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
)

@Serializable
internal data class PlayerAgentStatResponse(
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

@Serializable
internal data class PlayerRecentMatchResponse(
    val id: String,
    val eventName: String,
    val eventStage: String?,
    val teamA: PlayerMatchTeamResponse,
    val teamB: PlayerMatchTeamResponse,
    val teamAScore: Int?,
    val teamBScore: Int?,
    val outcome: PlayerMatchOutcome,
    val playedOn: String?,
)

@Serializable
internal data class PlayerMatchTeamResponse(val name: String, val tag: String?)

@Serializable
internal enum class PlayerMatchOutcome { WIN, LOSS, UNKNOWN }
