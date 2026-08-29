package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerAgentStatDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerCurrentTeamDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerProfileDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerRecentMatchDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerRecentMatchOutcomeDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerRecentMatchTeamDto
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerMapperTest {

    @Test
    fun responseMapsAllPlayerDetailFieldsWithoutChangingSourceOrder() {
        val detail = completeResponse().toDomain()

        assertEquals("488", detail.id)
        assertEquals("Rb", detail.profile.handle)
        assertEquals("Goo Sang-min", detail.profile.realName)
        assertEquals(listOf("ClokingRb", "Rb"), detail.profile.aliases)
        assertEquals("kr", detail.profile.countryCode)
        assertEquals("SOUTH KOREA", detail.profile.countryName)
        assertEquals(PlayerCurrentTeamDto("11060", "Nongshim RedForce", "https://owcdn.net/img/6399bb707aacb.png").id, detail.currentTeam?.id)
        assertEquals("Nongshim RedForce", detail.currentTeam?.name)
        assertEquals("https://owcdn.net/img/6399bb707aacb.png", detail.currentTeam?.imageUrl)

        val stat = detail.agentStats.single()
        assertEquals("jett", stat.agentName)
        assertEquals(134, stat.mapsPlayed)
        assertEquals(25, stat.pickRatePercent)
        assertEquals(2680, stat.roundsPlayed)
        assertEquals(1.07, stat.rating)
        assertEquals(235.1, stat.averageCombatScore)
        assertEquals(1.3, stat.killDeathRatio)
        assertEquals(72, stat.kastPercent)
        assertEquals(140.5, stat.averageDamagePerRound)
        assertEquals(0.83, stat.killsPerRound)
        assertEquals(0.13, stat.assistsPerRound)
        assertEquals(1.25, stat.firstKillDeathRatio)
        assertEquals(2224, stat.kills)
        assertEquals(1712, stat.deaths)
        assertEquals(355, stat.assists)
        assertEquals(545, stat.firstKills)
        assertEquals(435, stat.firstDeaths)

        assertEquals(listOf("708427", "708426"), detail.recentMatches.map { it.id })
        assertEquals("Playoffs · CF", detail.recentMatches.first().eventStage)
        assertEquals("NS", detail.recentMatches.first().teamA.tag)
        assertEquals(PlayerRecentMatchOutcome.WIN, detail.recentMatches.first().outcome)
        assertEquals("2026-07-12", detail.recentMatches.first().playedOn)
    }

    @Test
    fun nullableFieldsAndEmptySectionsArePreservedWithoutSynthesizingValues() {
        val detail = PlayerDetailResponseDto(
            id = "488",
            profile = PlayerProfileDto(
                handle = "Rb",
                realName = null,
                aliases = emptyList(),
                countryCode = null,
                countryName = null,
            ),
            currentTeam = null,
            agentStats = listOf(
                PlayerAgentStatDto(agentName = "jett", mapsPlayed = 0),
            ),
            recentMatches = listOf(
                recentMatch(
                    eventStage = null,
                    teamAScore = null,
                    teamBScore = null,
                    playedOn = null,
                ),
            ),
        ).toDomain()

        assertNull(detail.currentTeam)
        assertEquals(emptyList(), detail.profile.aliases)
        assertNull(detail.profile.realName)
        assertNull(detail.profile.countryCode)
        assertNull(detail.profile.countryName)
        assertNull(detail.agentStats.single().pickRatePercent)
        assertNull(detail.agentStats.single().roundsPlayed)
        assertNull(detail.agentStats.single().rating)
        assertNull(detail.agentStats.single().averageCombatScore)
        assertNull(detail.agentStats.single().killDeathRatio)
        assertNull(detail.agentStats.single().kastPercent)
        assertNull(detail.agentStats.single().averageDamagePerRound)
        assertNull(detail.agentStats.single().killsPerRound)
        assertNull(detail.agentStats.single().assistsPerRound)
        assertNull(detail.agentStats.single().firstKillDeathRatio)
        assertNull(detail.agentStats.single().kills)
        assertNull(detail.agentStats.single().deaths)
        assertNull(detail.agentStats.single().assists)
        assertNull(detail.agentStats.single().firstKills)
        assertNull(detail.agentStats.single().firstDeaths)
        assertNull(detail.recentMatches.single().eventStage)
        assertNull(detail.recentMatches.single().teamAScore)
        assertNull(detail.recentMatches.single().teamBScore)
        assertNull(detail.recentMatches.single().playedOn)
    }

    @Test
    fun emptyStatsAndRecentMatchesStayEmpty() {
        val detail = PlayerDetailResponseDto(
            id = "488",
            profile = PlayerProfileDto("Rb", null, emptyList(), null, null),
            currentTeam = null,
            agentStats = emptyList(),
            recentMatches = emptyList(),
        ).toDomain()

        assertEquals(emptyList(), detail.agentStats)
        assertEquals(emptyList(), detail.recentMatches)
    }

    @Test
    fun currentTeamImageUrlIsNullableAndMissingValueStaysNull() {
        val detail = PlayerDetailResponseDto(
            id = "488",
            profile = PlayerProfileDto("Rb", null, emptyList(), null, null),
            currentTeam = PlayerCurrentTeamDto("11060", "Nongshim RedForce"),
            agentStats = emptyList(),
            recentMatches = emptyList(),
        ).toDomain()

        assertNull(detail.currentTeam?.imageUrl)
    }

    @Test
    fun everyRecentMatchOutcomeMapsExactly() {
        val outcomes = PlayerRecentMatchOutcomeDto.entries.map { outcome ->
            PlayerDetailResponseDto(
                id = "488",
                profile = PlayerProfileDto("Rb", null, emptyList(), null, null),
                currentTeam = null,
                agentStats = emptyList(),
                recentMatches = listOf(recentMatch(outcome = outcome)),
            ).toDomain().recentMatches.single().outcome
        }

        assertEquals(
            listOf(
                PlayerRecentMatchOutcome.WIN,
                PlayerRecentMatchOutcome.LOSS,
                PlayerRecentMatchOutcome.UNKNOWN,
            ),
            outcomes,
        )
    }

    private fun completeResponse() = PlayerDetailResponseDto(
        id = "488",
        profile = PlayerProfileDto(
            handle = "Rb",
            realName = "Goo Sang-min",
            aliases = listOf("ClokingRb", "Rb"),
            countryCode = "kr",
            countryName = "SOUTH KOREA",
        ),
        currentTeam = PlayerCurrentTeamDto(
            "11060",
            "Nongshim RedForce",
            "https://owcdn.net/img/6399bb707aacb.png",
        ),
        agentStats = listOf(
            PlayerAgentStatDto(
                agentName = "jett",
                mapsPlayed = 134,
                pickRatePercent = 25,
                roundsPlayed = 2680,
                rating = 1.07,
                averageCombatScore = 235.1,
                killDeathRatio = 1.3,
                kastPercent = 72,
                averageDamagePerRound = 140.5,
                killsPerRound = 0.83,
                assistsPerRound = 0.13,
                firstKillDeathRatio = 1.25,
                kills = 2224,
                deaths = 1712,
                assists = 355,
                firstKills = 545,
                firstDeaths = 435,
            ),
        ),
        recentMatches = listOf(
            recentMatch(id = "708427", outcome = PlayerRecentMatchOutcomeDto.WIN),
            recentMatch(id = "708426", outcome = PlayerRecentMatchOutcomeDto.LOSS),
        ),
    )

    private fun recentMatch(
        id: String = "708427",
        eventStage: String? = "Playoffs · CF",
        teamAScore: Int? = 2,
        teamBScore: Int? = 0,
        outcome: PlayerRecentMatchOutcomeDto = PlayerRecentMatchOutcomeDto.WIN,
        playedOn: String? = "2026-07-12",
    ) = PlayerRecentMatchDto(
        id = id,
        eventName = "EWC 2026",
        eventStage = eventStage,
        teamA = PlayerRecentMatchTeamDto("Nongshim RedForce", "NS"),
        teamB = PlayerRecentMatchTeamDto("BBL Esports", "BBL"),
        teamAScore = teamAScore,
        teamBScore = teamBScore,
        outcome = outcome,
        playedOn = playedOn,
    )
}
