package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.ui.component.StatsColumn
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort
import kr.co.cotton.vlrgg_mobile.ui.component.VlrStatsTable
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

internal const val PLAYER_AGENT_STATS_TABLE_TAG = "player-agent-stats-table"
internal fun playerAgentIdentityTag(rowKey: String) = "player-agent-identity-$rowKey"
internal fun playerAgentMetricHeaderTag(metric: String) = "player-agent-metric-header-$metric"
internal fun playerAgentMetricValueTag(agentName: String, metric: String) = "player-agent-metric-$agentName-$metric"

internal data class PlayerAgentStatsRow(
    val key: String,
    val stat: PlayerAgentStat,
)

@Composable
internal fun PlayerAgentStatsTable(
    stats: List<PlayerAgentStat>,
    horizontalScrollState: ScrollState,
    sort: StatsSort<PlayerAgentStatsSortColumn>?,
    onSortColumn: (PlayerAgentStatsSortColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(stats) { stats.toPlayerAgentStatsRows() }
    VlrStatsTable(
        rows = rows,
        rowKey = PlayerAgentStatsRow::key,
        identityHeader = "Agent",
        identityText = { it.stat.agentName.agentDisplayName() },
        rowSemanticsLabel = "에이전트 통계",
        columns = playerAgentStatsColumns(),
        horizontalScrollState = horizontalScrollState,
        identityWidth = 120.dp,
        headerMinHeight = 56.dp,
        rowMinHeight = 52.dp,
        identityTextStyle = VlrTheme.typography.body,
        headerTextStyle = VlrTheme.typography.labelSmall,
        valueTextStyle = VlrTheme.typography.body,
        modifier = modifier.testTag(PLAYER_AGENT_STATS_TABLE_TAG),
        headerContainer = VlrTheme.colors.surfaceSubtle,
        sort = sort,
        onSortColumn = onSortColumn,
        identityTestTag = { playerAgentIdentityTag(it.key) },
        metricHeaderTestTag = { playerAgentMetricHeaderTag(it.label) },
        metricValueTestTag = { row, column ->
            playerAgentMetricValueTag(row.stat.agentName, column.label)
        },
    )
}

internal fun playerAgentStatsColumns(): List<StatsColumn<PlayerAgentStatsRow, PlayerAgentStatsSortColumn>> =
    listOf(
        playerAgentColumn(PlayerAgentStatsSortColumn.MAPS, { it.mapsPlayed.toString() }) { it.mapsPlayed.toDouble() },
        playerAgentColumn(
            PlayerAgentStatsSortColumn.PICK_RATE,
            { it.pickRatePercent?.let { value -> "$value%" } ?: "—" },
        ) { it.pickRatePercent?.toDouble() },
        playerAgentColumn(PlayerAgentStatsSortColumn.RATING, { it.rating?.toString() ?: "—" }) { it.rating },
        playerAgentColumn(
            PlayerAgentStatsSortColumn.ACS,
            { it.averageCombatScore?.toString() ?: "—" },
        ) { it.averageCombatScore },
        playerAgentColumn(PlayerAgentStatsSortColumn.K_D, { it.killDeathRatio?.toString() ?: "—" }) {
            it.killDeathRatio
        },
        playerAgentColumn(
            PlayerAgentStatsSortColumn.KAST,
            { it.kastPercent?.let { value -> "$value%" } ?: "—" },
        ) { it.kastPercent?.toDouble() },
        playerAgentColumn(
            PlayerAgentStatsSortColumn.ADR,
            { it.averageDamagePerRound?.toString() ?: "—" },
        ) { it.averageDamagePerRound },
    )

private fun playerAgentColumn(
    key: PlayerAgentStatsSortColumn,
    displayValue: (PlayerAgentStat) -> String,
    numericValue: (PlayerAgentStat) -> Double?,
) = StatsColumn<PlayerAgentStatsRow, PlayerAgentStatsSortColumn>(
    key = key,
    label = key.label,
    width = 84.dp,
    textAlign = TextAlign.End,
    displayValue = { displayValue(it.stat) },
    numericValue = { numericValue(it.stat) },
)

internal fun List<PlayerAgentStat>.toPlayerAgentStatsRows(): List<PlayerAgentStatsRow> {
    val counts = groupingBy(PlayerAgentStat::agentName).eachCount()
    val occurrences = mutableMapOf<String, Int>()
    return map { stat ->
        val occurrence = occurrences.getOrElse(stat.agentName) { 0 } + 1
        occurrences[stat.agentName] = occurrence
        PlayerAgentStatsRow(
            key = if (counts.getValue(stat.agentName) == 1) stat.agentName else "${stat.agentName}#$occurrence",
            stat = stat,
        )
    }
}

private fun String.agentDisplayName(): String = replaceFirstChar { it.uppercase() }
