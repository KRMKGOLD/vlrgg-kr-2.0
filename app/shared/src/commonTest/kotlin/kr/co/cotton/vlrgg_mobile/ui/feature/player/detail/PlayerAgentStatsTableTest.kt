package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSortDirection
import kr.co.cotton.vlrgg_mobile.ui.component.stableSortedRows
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerAgentStatsTableTest {
    @Test
    fun everyDisplayedColumnSelectsItsTypedDomainNumber() {
        val row = PlayerAgentStatsRow(
            key = "jett" to 1,
            stat = agentStat(
                name = "jett",
                maps = 7,
                pickRate = 13,
                rating = 1.07,
                acs = 235.1,
                killDeath = 1.3,
                kast = 72,
                adr = 140.5,
            ),
        )
        val columns = playerAgentStatsColumns()

        assertEquals(PlayerAgentStatsSortColumn.entries, columns.map { it.key })
        assertEquals(
            mapOf(
                PlayerAgentStatsSortColumn.MAPS to 7.0,
                PlayerAgentStatsSortColumn.PICK_RATE to 13.0,
                PlayerAgentStatsSortColumn.RATING to 1.07,
                PlayerAgentStatsSortColumn.ACS to 235.1,
                PlayerAgentStatsSortColumn.K_D to 1.3,
                PlayerAgentStatsSortColumn.KAST to 72.0,
                PlayerAgentStatsSortColumn.ADR to 140.5,
            ),
            columns.associate { it.key to it.numericValue(row) },
        )
    }

    @Test
    fun sortingUsesTheNewSnapshotWithoutMutatingEitherSourceOrder() {
        val firstSource = listOf(agentStat("jett", maps = 2), agentStat("omen", maps = 10))
        val firstRows = firstSource.toPlayerAgentStatsRows()
        val sort = StatsSort(PlayerAgentStatsSortColumn.MAPS, StatsSortDirection.DESCENDING)

        assertEquals(
            listOf("omen", "jett"),
            stableSortedRows(firstRows, playerAgentStatsColumns(), sort).map { it.stat.agentName },
        )
        assertEquals(listOf("jett", "omen"), firstSource.map(PlayerAgentStat::agentName))
        assertEquals(listOf("jett", "omen"), firstRows.map { it.stat.agentName })

        val newSource = listOf(agentStat("sage", maps = 4), agentStat("raze", maps = 12))
        assertEquals(
            listOf("raze", "sage"),
            stableSortedRows(newSource.toPlayerAgentStatsRows(), playerAgentStatsColumns(), sort)
                .map { it.stat.agentName },
        )
        assertEquals(listOf("sage", "raze"), newSource.map(PlayerAgentStat::agentName))
    }

    @Test
    fun duplicateAgentNamesReceiveOccurrenceKeysWithoutChangingDomainData() {
        val source = listOf(agentStat("jett", maps = 1), agentStat("omen", maps = 2), agentStat("jett", maps = 3), agentStat("jett#1", maps = 4))

        val rows = source.toPlayerAgentStatsRows()

        assertEquals(source.size, rows.map(PlayerAgentStatsRow::key).toSet().size)
        assertEquals(listOf("jett" to 1, "omen" to 1, "jett" to 2, "jett#1" to 1), rows.map(PlayerAgentStatsRow::key))
        assertEquals(source, rows.map(PlayerAgentStatsRow::stat))
    }

    private fun agentStat(
        name: String,
        maps: Int,
        pickRate: Int? = null,
        rating: Double? = null,
        acs: Double? = null,
        killDeath: Double? = null,
        kast: Int? = null,
        adr: Double? = null,
    ) = PlayerAgentStat(
        agentName = name,
        mapsPlayed = maps,
        pickRatePercent = pickRate,
        roundsPlayed = null,
        rating = rating,
        averageCombatScore = acs,
        killDeathRatio = killDeath,
        kastPercent = kast,
        averageDamagePerRound = adr,
        killsPerRound = null,
        assistsPerRound = null,
        firstKillDeathRatio = null,
        kills = null,
        deaths = null,
        assists = null,
        firstKills = null,
        firstDeaths = null,
    )
}
