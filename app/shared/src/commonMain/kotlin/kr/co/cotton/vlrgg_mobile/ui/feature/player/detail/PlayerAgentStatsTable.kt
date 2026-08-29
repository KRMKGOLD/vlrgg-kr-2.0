package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

internal const val PLAYER_AGENT_STATS_TABLE_TAG = "player-agent-stats-table"
internal fun playerAgentMetricHeaderTag(metric: String) = "player-agent-metric-header-$metric"
internal fun playerAgentMetricValueTag(agentName: String, metric: String) = "player-agent-metric-$agentName-$metric"

private const val AgentColumnWidth = 120
private const val MetricColumnWidth = 84
private val AgentTableHeaderHeight = 56.dp
private val AgentTableRowHeight = 52.dp

@Composable
internal fun PlayerAgentStatsTable(
    stats: List<PlayerAgentStat>,
    modifier: Modifier = Modifier,
) {
    val metrics = agentMetrics()
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PLAYER_AGENT_STATS_TABLE_TAG)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline)
            .semantics { isTraversalGroup = true },
    ) {
        AgentIdentityColumn(stats, metrics.size)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scroll),
        ) {
            metrics.forEachIndexed { index, metric -> MetricColumn(metric, index, metrics.size, stats) }
        }
    }
}

@Composable
private fun AgentIdentityColumn(stats: List<PlayerAgentStat>, metricCount: Int) = Column(Modifier.width(AgentColumnWidth.dp)) {
    AgentTableCell(text = "Agent", header = true)
    TableDivider()
    stats.forEachIndexed { index, stat ->
        AgentTableCell(
            text = stat.agentName.agentDisplayName(),
            header = false,
            modifier = Modifier.semantics {
                contentDescription = stat.accessibilitySummary()
                isTraversalGroup = true
                traversalIndex = rowTraversalIndex(index, metricCount, columnIndex = 0)
            },
        )
        if (index != stats.lastIndex) TableDivider()
    }
}

@Composable
private fun MetricColumn(
    metric: AgentMetric,
    metricIndex: Int,
    metricCount: Int,
    stats: List<PlayerAgentStat>,
) = Column(Modifier.width(MetricColumnWidth.dp)) {
    AgentTableCell(
        text = metric.title,
        header = true,
        textAlign = TextAlign.End,
        modifier = Modifier.testTag(playerAgentMetricHeaderTag(metric.title)),
    )
    TableDivider()
    stats.forEachIndexed { index, stat ->
        AgentTableCell(
            text = metric.value(stat),
            header = false,
            textAlign = TextAlign.End,
            modifier = Modifier
                .testTag(playerAgentMetricValueTag(stat.agentName, metric.title))
                .semantics {
                    traversalIndex = rowTraversalIndex(index, metricCount, columnIndex = metricIndex + 1)
                },
        )
        if (index != stats.lastIndex) TableDivider()
    }
}

@Composable
private fun AgentTableCell(
    text: String,
    header: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) = Box(
    modifier = modifier
        .fillMaxWidth()
        .height(if (header) AgentTableHeaderHeight else AgentTableRowHeight)
        .background(if (header) VlrTheme.colors.surfaceSubtle else VlrTheme.colors.surface)
        .padding(horizontal = VlrDimensions.Space4),
    contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart,
) {
    Text(
        text = text,
        style = if (header) VlrTheme.typography.labelSmall else VlrTheme.typography.body,
        color = if (header) VlrTheme.colors.textSecondary else VlrTheme.colors.textPrimary,
        textAlign = textAlign,
    )
}

@Composable
private fun TableDivider() = HorizontalDivider(
    thickness = VlrDimensions.OutlineWidth,
    color = VlrTheme.colors.outline,
)

private data class AgentMetric(
    val title: String,
    val value: (PlayerAgentStat) -> String,
)

private fun agentMetrics(): List<AgentMetric> = listOf(
    AgentMetric("Maps") { it.mapsPlayed.toString() },
    AgentMetric("Pick Rate") { it.pickRatePercent?.let { value -> "$value%" } ?: "—" },
    AgentMetric("Rating") { it.rating?.toString() ?: "—" },
    AgentMetric("ACS") { it.averageCombatScore?.toString() ?: "—" },
    AgentMetric("K/D") { it.killDeathRatio?.toString() ?: "—" },
    AgentMetric("KAST") { it.kastPercent?.let { value -> "$value%" } ?: "—" },
    AgentMetric("ADR") { it.averageDamagePerRound?.toString() ?: "—" },
)

private fun String.agentDisplayName(): String = replaceFirstChar { it.uppercase() }

private fun PlayerAgentStat.accessibilitySummary(): String = agentMetrics().joinToString(
    prefix = "에이전트 통계: ${agentName.agentDisplayName()}, ",
) { metric -> "${metric.title}: ${metric.value(this)}" }

private fun rowTraversalIndex(rowIndex: Int, metricCount: Int, columnIndex: Int): Float =
    ((rowIndex + 1) * (metricCount + 1) + columnIndex).toFloat()
