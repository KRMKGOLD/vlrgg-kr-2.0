package kr.co.cotton.vlrgg_mobile.data.remote.model.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PlayerDetailResponseDto(
    val id: String,
    val profile: PlayerProfileDto,
    val currentTeam: PlayerCurrentTeamDto? = null,
    val agentStats: List<PlayerAgentStatDto>,
    val recentMatches: List<PlayerRecentMatchDto>,
)

@Serializable
internal data class PlayerProfileDto(
    val handle: String,
    val realName: String? = null,
    val aliases: List<String>,
    val countryCode: String? = null,
    val countryName: String? = null,
)

@Serializable
internal data class PlayerCurrentTeamDto(
    val id: String,
    val name: String,
)

@Serializable
internal data class PlayerAgentStatDto(
    val agentName: String,
    val mapsPlayed: Int,
    val pickRatePercent: Int? = null,
    val roundsPlayed: Int? = null,
    val rating: Double? = null,
    val averageCombatScore: Double? = null,
    val killDeathRatio: Double? = null,
    val kastPercent: Int? = null,
    val averageDamagePerRound: Double? = null,
    val killsPerRound: Double? = null,
    val assistsPerRound: Double? = null,
    val firstKillDeathRatio: Double? = null,
    val kills: Int? = null,
    val deaths: Int? = null,
    val assists: Int? = null,
    val firstKills: Int? = null,
    val firstDeaths: Int? = null,
)

@Serializable
internal data class PlayerRecentMatchDto(
    val id: String,
    val eventName: String,
    val eventStage: String? = null,
    val teamA: PlayerRecentMatchTeamDto,
    val teamB: PlayerRecentMatchTeamDto,
    val teamAScore: Int? = null,
    val teamBScore: Int? = null,
    val outcome: PlayerRecentMatchOutcomeDto,
    val playedOn: String? = null,
)

@Serializable
internal data class PlayerRecentMatchTeamDto(
    val name: String,
    val tag: String? = null,
)

@Serializable
internal enum class PlayerRecentMatchOutcomeDto {
    @SerialName("WIN")
    WIN,

    @SerialName("LOSS")
    LOSS,

    @SerialName("UNKNOWN")
    UNKNOWN,
}
