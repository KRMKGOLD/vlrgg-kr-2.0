package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerAgentStatDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerCurrentTeamDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerProfileDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerRecentMatchDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerRecentMatchOutcomeDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerRecentMatchTeamDto
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerCurrentTeam
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerProfile
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchTeam

internal fun PlayerDetailResponseDto.toDomain(): PlayerDetail = PlayerDetail(
    id = id,
    profile = profile.toDomain(),
    currentTeam = currentTeam?.toDomain(),
    agentStats = agentStats.map(PlayerAgentStatDto::toDomain),
    recentMatches = recentMatches.map(PlayerRecentMatchDto::toDomain),
)

private fun PlayerProfileDto.toDomain(): PlayerProfile = PlayerProfile(
    handle = handle,
    realName = realName,
    aliases = aliases,
    countryCode = countryCode,
    countryName = countryName,
)

private fun PlayerCurrentTeamDto.toDomain(): PlayerCurrentTeam = PlayerCurrentTeam(
    id = id,
    name = name,
    imageUrl = imageUrl,
)

private fun PlayerAgentStatDto.toDomain(): PlayerAgentStat = PlayerAgentStat(
    agentName = agentName,
    mapsPlayed = mapsPlayed,
    pickRatePercent = pickRatePercent,
    roundsPlayed = roundsPlayed,
    rating = rating,
    averageCombatScore = averageCombatScore,
    killDeathRatio = killDeathRatio,
    kastPercent = kastPercent,
    averageDamagePerRound = averageDamagePerRound,
    killsPerRound = killsPerRound,
    assistsPerRound = assistsPerRound,
    firstKillDeathRatio = firstKillDeathRatio,
    kills = kills,
    deaths = deaths,
    assists = assists,
    firstKills = firstKills,
    firstDeaths = firstDeaths,
)

private fun PlayerRecentMatchDto.toDomain(): PlayerRecentMatch = PlayerRecentMatch(
    id = id,
    eventName = eventName,
    eventStage = eventStage,
    teamA = teamA.toDomain(),
    teamB = teamB.toDomain(),
    teamAScore = teamAScore,
    teamBScore = teamBScore,
    outcome = outcome.toDomain(),
    playedOn = playedOn,
)

private fun PlayerRecentMatchTeamDto.toDomain(): PlayerRecentMatchTeam = PlayerRecentMatchTeam(
    name = name,
    tag = tag,
)

private fun PlayerRecentMatchOutcomeDto.toDomain(): PlayerRecentMatchOutcome = when (this) {
    PlayerRecentMatchOutcomeDto.WIN -> PlayerRecentMatchOutcome.WIN
    PlayerRecentMatchOutcomeDto.LOSS -> PlayerRecentMatchOutcome.LOSS
    PlayerRecentMatchOutcomeDto.UNKNOWN -> PlayerRecentMatchOutcome.UNKNOWN
}
