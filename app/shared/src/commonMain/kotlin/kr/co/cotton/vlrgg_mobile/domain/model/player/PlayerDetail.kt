package kr.co.cotton.vlrgg_mobile.domain.model.player

data class PlayerDetail(
    val id: String,
    val profile: PlayerProfile,
    val currentTeam: PlayerCurrentTeam?,
    val agentStats: List<PlayerAgentStat>,
    val recentMatches: List<PlayerRecentMatch>,
)

data class PlayerProfile(
    val handle: String,
    val realName: String?,
    val aliases: List<String>,
    val countryCode: String?,
    val countryName: String?,
    val imageUrl: String? = null,
)

data class PlayerCurrentTeam(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
)

data class PlayerAgentStat(
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

data class PlayerRecentMatch(
    val id: String,
    val eventName: String,
    val eventStage: String?,
    val teamA: PlayerRecentMatchTeam,
    val teamB: PlayerRecentMatchTeam,
    val teamAScore: Int?,
    val teamBScore: Int?,
    val outcome: PlayerRecentMatchOutcome,
    val playedOn: String?,
)

data class PlayerRecentMatchTeam(
    val name: String,
    val tag: String?,
)

enum class PlayerRecentMatchOutcome {
    WIN,
    LOSS,
    UNKNOWN,
}
