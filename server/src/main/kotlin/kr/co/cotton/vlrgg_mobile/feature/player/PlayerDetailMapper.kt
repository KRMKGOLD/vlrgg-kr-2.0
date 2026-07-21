package kr.co.cotton.vlrgg_mobile.feature.player

/** Converts internal VLR-shaped Player data into the stable v1 app contract. */
internal class PlayerDetailMapper {
    fun map(playerId: PlayerId, source: PlayerDetailSource): PlayerDetailResponse = PlayerDetailResponse(
        id = playerId.value,
        profile = PlayerProfileResponse(
            handle = source.profile.handle,
            realName = source.profile.realName,
            aliases = source.profile.aliases,
            countryCode = source.profile.countryCode,
            countryName = source.profile.countryName,
        ),
        currentTeam = source.currentTeam?.let { PlayerTeamResponse(it.id, it.name) },
        agentStats = source.agentStats.map { stat ->
            PlayerAgentStatResponse(
                agentName = stat.agentName,
                mapsPlayed = stat.mapsPlayed,
                pickRatePercent = stat.pickRatePercent,
                roundsPlayed = stat.roundsPlayed,
                rating = stat.rating,
                averageCombatScore = stat.averageCombatScore,
                killDeathRatio = stat.killDeathRatio,
                kastPercent = stat.kastPercent,
                averageDamagePerRound = stat.averageDamagePerRound,
                killsPerRound = stat.killsPerRound,
                assistsPerRound = stat.assistsPerRound,
                firstKillDeathRatio = stat.firstKillDeathRatio,
                kills = stat.kills,
                deaths = stat.deaths,
                assists = stat.assists,
                firstKills = stat.firstKills,
                firstDeaths = stat.firstDeaths,
            )
        },
        recentMatches = source.recentMatches.take(MAX_RECENT_MATCHES).map { match ->
            PlayerRecentMatchResponse(
                id = match.id,
                eventName = match.eventName,
                eventStage = match.eventStage,
                teamA = PlayerMatchTeamResponse(match.teamA.name, match.teamA.tag),
                teamB = PlayerMatchTeamResponse(match.teamB.name, match.teamB.tag),
                teamAScore = match.teamAScore,
                teamBScore = match.teamBScore,
                outcome = when (match.outcome) {
                    PlayerMatchOutcomeSource.WIN -> PlayerMatchOutcome.WIN
                    PlayerMatchOutcomeSource.LOSS -> PlayerMatchOutcome.LOSS
                    PlayerMatchOutcomeSource.UNKNOWN -> PlayerMatchOutcome.UNKNOWN
                },
                playedOn = match.playedOn,
            )
        },
    )

    private companion object { const val MAX_RECENT_MATCHES = 5 }
}
