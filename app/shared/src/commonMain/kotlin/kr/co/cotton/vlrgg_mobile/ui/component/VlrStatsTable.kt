package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_downward
import vlrggmobile.app.shared.generated.resources.ic_arrow_upward
import vlrggmobile.app.shared.generated.resources.ic_swap_horiz

enum class StatsSortDirection(val savedStateId: String) {
    DESCENDING("desc"),
    ASCENDING("asc"),
    ;

    companion object {
        internal fun fromSavedStateId(id: String?): StatsSortDirection? = entries.firstOrNull { it.savedStateId == id }
    }
}

data class StatsSort<K : Any>(
    val column: K,
    val direction: StatsSortDirection,
)

/** One metric column. Values are right-aligned `stat` text; [StatsMissingValue] renders `muted`. */
internal data class StatsColumn<T, K : Any>(
    val key: K,
    val label: String,
    val displayValue: (T) -> String,
    val numericValue: (T) -> Double?,
    val width: Dp = StatsTableDefaults.ColumnWidth,
)

/** Missing metric value (`DESIGN.md` 3·8.3). A real zero stays `0`. */
internal const val StatsMissingValue = "—"

internal const val StatsScrollHint = "옆으로 밀어 지표 더 보기 · — 는 기록 없음"
internal const val StatsSortIconTag = "vlr-stats-sort-icon"

internal object StatsTableDefaults {
    val IdentityWidth: Dp = 120.dp
    val ColumnWidth: Dp = 68.dp
    val HeaderHeight: Dp = 40.dp
    val RowHeight: Dp = 52.dp
    val FadeWidth: Dp = 56.dp
}

internal fun <K : Any> nextStatsSort(current: StatsSort<K>?, column: K): StatsSort<K>? = when {
    current?.column != column -> StatsSort(column, StatsSortDirection.DESCENDING)
    current.direction == StatsSortDirection.DESCENDING -> current.copy(direction = StatsSortDirection.ASCENDING)
    else -> null
}

internal fun <T, K : Any> stableSortedRows(
    rows: List<T>,
    columns: List<StatsColumn<T, K>>,
    sort: StatsSort<K>?,
): List<T> {
    if (sort == null || rows.size < 2) return rows
    val column = columns.firstOrNull { it.key == sort.column } ?: return rows
    return rows.withIndex().sortedWith { left, right ->
        val leftValue = column.numericValue(left.value)
        val rightValue = column.numericValue(right.value)
        val valueOrder = when {
            leftValue == null && rightValue == null -> 0
            leftValue == null -> 1
            rightValue == null -> -1
            sort.direction == StatsSortDirection.DESCENDING -> rightValue.compareTo(leftValue)
            else -> leftValue.compareTo(rightValue)
        }
        if (valueOrder != 0) valueOrder else left.index.compareTo(right.index)
    }.map { it.value }
}

/**
 * v3 stats table (`DESIGN.md` 8.3), shared by player agent stats and event stats.
 *
 * A pinned 120dp identity column with a right 1dp line stays put while the metrics scroll
 * sideways together through [horizontalScrollState]. Sortable headers ([onSortColumn]) show the
 * direction with an `ink` label and arrow; a 40dp header still gets the 48dp touch target. While
 * more metrics sit off-screen, each row fades its right 56dp and a hint line follows the rows.
 * With [lazyListState] the table is its own lazy list (event stats tab).
 */
@Composable
internal fun <T, K : Any> VlrStatsTable(
    rows: List<T>,
    rowKey: (T) -> Any,
    identityHeader: String,
    identityText: (T) -> String,
    rowSemanticsLabel: String,
    columns: List<StatsColumn<T, K>>,
    horizontalScrollState: ScrollState,
    modifier: Modifier = Modifier,
    identitySupportingText: (T) -> String? = { null },
    lazyListState: LazyListState? = null,
    sort: StatsSort<K>? = null,
    onSortColumn: ((K) -> Unit)? = null,
    onIdentityClick: ((T) -> Unit)? = null,
    identityTestTag: ((T) -> String)? = null,
    metricHeaderTestTag: ((K) -> String)? = null,
    metricValueTestTag: ((T, K) -> String)? = null,
) {
    val displayedRows = if (onSortColumn == null) rows else stableSortedRows(rows, columns, sort)
    val showsScrollHint = horizontalScrollState.maxValue > 0
    val header: @Composable () -> Unit = {
        StatsHeaderRow(identityHeader, columns, horizontalScrollState, sort, onSortColumn, metricHeaderTestTag)
    }
    val valueRow: @Composable (T) -> Unit = { row ->
        StatsValueRow(
            row,
            identityText,
            identitySupportingText,
            rowSemanticsLabel,
            columns,
            horizontalScrollState,
            onIdentityClick,
            identityTestTag,
            metricValueTestTag,
        )
    }
    val tableModifier = modifier.background(VlrTheme.colors.surface)

    if (lazyListState != null) {
        LazyColumn(state = lazyListState, modifier = tableModifier) {
            item(key = "vlr-stats-header") { header() }
            items(displayedRows, key = rowKey) { row -> valueRow(row) }
            if (showsScrollHint) {
                item(key = "vlr-stats-hint") { StatsScrollHintRow() }
            }
        }
    } else {
        Column(modifier = tableModifier) {
            header()
            displayedRows.forEach { row ->
                key(rowKey(row)) { valueRow(row) }
            }
            if (showsScrollHint) StatsScrollHintRow()
        }
    }
}

@Composable
private fun <T, K : Any> StatsHeaderRow(
    identityHeader: String,
    columns: List<StatsColumn<T, K>>,
    horizontalScrollState: ScrollState,
    sort: StatsSort<K>?,
    onSortColumn: ((K) -> Unit)?,
    metricHeaderTestTag: ((K) -> String)?,
) = StatsTableRow(
    minHeight = StatsTableDefaults.HeaderHeight,
    container = VlrTheme.colors.surfaceSunken,
    horizontalScrollState = horizontalScrollState,
    identity = {
        Text(
            text = identityHeader,
            style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Bold),
            color = VlrTheme.colors.muted,
        )
    },
) {
    columns.forEach { column ->
        key(column.key) {
            val activeDirection = sort?.takeIf { onSortColumn != null && it.column == column.key }?.direction
            val tag = metricHeaderTestTag?.invoke(column.key)
            val interaction = if (onSortColumn == null) {
                Modifier
            } else {
                Modifier
                    .semantics {
                        stateDescription = when (activeDirection) {
                            StatsSortDirection.DESCENDING -> "내림차순"
                            StatsSortDirection.ASCENDING -> "오름차순"
                            null -> "정렬 안 함"
                        }
                    }
                    .clickable(role = Role.Button) { onSortColumn(column.key) }
            }
            val labelColor = if (activeDirection != null) VlrTheme.colors.ink else VlrTheme.colors.muted
            Row(
                modifier = Modifier
                    .width(column.width)
                    .fillMaxHeight()
                    .then(if (tag == null) Modifier else Modifier.testTag(tag))
                    .then(interaction)
                    .padding(end = VlrDimensions.Space3),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = column.label,
                    modifier = Modifier.weight(1f, fill = false),
                    style = VlrTheme.typography.tag,
                    color = labelColor,
                    textAlign = TextAlign.End,
                )
                if (activeDirection != null) {
                    Icon(
                        imageVector = vectorResource(
                            if (activeDirection == StatsSortDirection.DESCENDING) {
                                Res.drawable.ic_arrow_downward
                            } else {
                                Res.drawable.ic_arrow_upward
                            },
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp).testTag(StatsSortIconTag),
                        tint = VlrTheme.colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T, K : Any> StatsValueRow(
    row: T,
    identityText: (T) -> String,
    identitySupportingText: (T) -> String?,
    rowSemanticsLabel: String,
    columns: List<StatsColumn<T, K>>,
    horizontalScrollState: ScrollState,
    onIdentityClick: ((T) -> Unit)?,
    identityTestTag: ((T) -> String)?,
    metricValueTestTag: ((T, K) -> String)?,
) {
    val identity = identityText(row)
    val tag = identityTestTag?.invoke(row)
    val interaction = if (onIdentityClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button) { onIdentityClick(row) }
    }
    StatsTableRow(
        minHeight = StatsTableDefaults.RowHeight,
        container = VlrTheme.colors.surface,
        horizontalScrollState = horizontalScrollState,
        identityModifier = Modifier
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .semantics { contentDescription = "$rowSemanticsLabel: $identity" }
            .then(interaction),
        identity = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = identity,
                    style = VlrTheme.typography.bodySm.copy(fontWeight = FontWeight.Bold),
                    color = VlrTheme.colors.ink,
                )
                identitySupportingText(row)?.let {
                    Text(
                        text = it,
                        style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                        color = VlrTheme.colors.muted,
                    )
                }
            }
        },
    ) {
        columns.forEach { column ->
            key(column.key) {
                val value = column.displayValue(row)
                val valueTag = metricValueTestTag?.invoke(row, column.key)
                Box(
                    modifier = Modifier
                        .width(column.width)
                        .fillMaxHeight()
                        .then(if (valueTag == null) Modifier else Modifier.testTag(valueTag))
                        .semantics { contentDescription = "$identity, ${column.label}, $value" }
                        .padding(end = VlrDimensions.Space3),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = value,
                        style = VlrTheme.typography.stat,
                        color = if (value == StatsMissingValue) VlrTheme.colors.muted else VlrTheme.colors.ink,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsTableRow(
    minHeight: Dp,
    container: Color,
    horizontalScrollState: ScrollState,
    identity: @Composable () -> Unit,
    identityModifier: Modifier = Modifier,
    metrics: @Composable () -> Unit,
) {
    val line = VlrTheme.colors.line
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container)
            .rowDivider(line)
            .height(IntrinsicSize.Min)
            .heightIn(min = minHeight),
    ) {
        Box(
            modifier = Modifier
                .width(StatsTableDefaults.IdentityWidth)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width - VlrDimensions.OutlineWidth.toPx() / 2
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), VlrDimensions.OutlineWidth.toPx())
                }
                .then(identityModifier)
                .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space2),
            contentAlignment = Alignment.CenterStart,
        ) {
            identity()
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxHeight().horizontalScroll(horizontalScrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                metrics()
            }
            if (horizontalScrollState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(StatsTableDefaults.FadeWidth)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(container.copy(alpha = 0f), container))),
                )
            }
        }
    }
}

@Composable
private fun StatsScrollHintRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.ic_swap_horiz),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = VlrTheme.colors.muted,
        )
        Text(
            text = StatsScrollHint,
            style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
            color = VlrTheme.colors.muted,
        )
    }
}
