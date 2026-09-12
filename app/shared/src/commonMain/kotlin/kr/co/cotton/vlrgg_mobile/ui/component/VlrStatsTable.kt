package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

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

internal data class StatsColumn<T, K : Any>(
    val key: K,
    val label: String,
    val width: Dp,
    val textAlign: TextAlign,
    val displayValue: (T) -> String,
    val numericValue: (T) -> Double?,
)

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

@Composable
internal fun <T, K : Any> VlrStatsTable(
    rows: List<T>,
    rowKey: (T) -> Any,
    identityHeader: String,
    identityText: (T) -> String,
    identitySupportingText: (T) -> String? = { null },
    rowSemanticsLabel: String,
    columns: List<StatsColumn<T, K>>,
    horizontalScrollState: ScrollState,
    identityWidth: Dp,
    headerMinHeight: Dp,
    rowMinHeight: Dp,
    identityTextStyle: TextStyle,
    headerTextStyle: TextStyle,
    valueTextStyle: TextStyle,
    modifier: Modifier = Modifier,
    headerContainer: Color = VlrTheme.colors.surface,
    lazyListState: LazyListState? = null,
    sort: StatsSort<K>? = null,
    onSortColumn: ((K) -> Unit)? = null,
    onIdentityClick: ((T) -> Unit)? = null,
    identityTestTag: ((T) -> String)? = null,
    metricHeaderTestTag: ((K) -> String)? = null,
    metricValueTestTag: ((T, K) -> String)? = null,
) {
    val displayedRows = if (onSortColumn == null) rows else stableSortedRows(rows, columns, sort)
    val shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    val tableModifier = modifier
        .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
        .clip(shape)
        .background(VlrTheme.colors.surface)

    if (lazyListState != null) {
        LazyColumn(state = lazyListState, modifier = tableModifier) {
            item(key = "vlr-stats-header") {
                StatsHeaderRow(
                    identityHeader,
                    columns,
                    horizontalScrollState,
                    identityWidth,
                    headerMinHeight,
                    headerTextStyle,
                    sort,
                    onSortColumn,
                    metricHeaderTestTag,
                    headerContainer,
                )
            }
            items(displayedRows, key = rowKey) { row ->
                HorizontalDivider(color = VlrTheme.colors.outline)
                StatsValueRow(
                    row,
                    identityText,
                    identitySupportingText,
                    rowSemanticsLabel,
                    columns,
                    horizontalScrollState,
                    identityWidth,
                    rowMinHeight,
                    identityTextStyle,
                    valueTextStyle,
                    onIdentityClick,
                    identityTestTag,
                    metricValueTestTag,
                )
            }
        }
    } else {
        Column(modifier = tableModifier) {
            StatsHeaderRow(
                identityHeader,
                columns,
                horizontalScrollState,
                identityWidth,
                headerMinHeight,
                headerTextStyle,
                sort,
                onSortColumn,
                metricHeaderTestTag,
                headerContainer,
            )
            displayedRows.forEach { row ->
                key(rowKey(row)) {
                    HorizontalDivider(color = VlrTheme.colors.outline)
                    StatsValueRow(
                        row,
                        identityText,
                        identitySupportingText,
                        rowSemanticsLabel,
                        columns,
                        horizontalScrollState,
                        identityWidth,
                        rowMinHeight,
                        identityTextStyle,
                        valueTextStyle,
                        onIdentityClick,
                        identityTestTag,
                        metricValueTestTag,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T, K : Any> StatsHeaderRow(
    identityHeader: String,
    columns: List<StatsColumn<T, K>>,
    horizontalScrollState: ScrollState,
    identityWidth: Dp,
    minHeight: Dp,
    textStyle: TextStyle,
    sort: StatsSort<K>?,
    onSortColumn: ((K) -> Unit)?,
    metricHeaderTestTag: ((K) -> String)?,
    headerContainer: Color,
) = StatsTableRow(identityWidth, minHeight, horizontalScrollState, modifier = Modifier.background(headerContainer), identity = {
    Box(
        modifier = Modifier.fillMaxHeight().fillMaxWidth().padding(horizontal = VlrDimensions.Space4),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(identityHeader, style = textStyle, color = VlrTheme.colors.textSecondary)
    }
}) {
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
            Box(
                modifier = Modifier
                    .width(column.width)
                    .fillMaxHeight()
                    .heightIn(min = VlrDimensions.MinimumTouchTarget)
                    .then(if (tag == null) Modifier else Modifier.testTag(tag))
                    .then(interaction)
                    .padding(horizontal = VlrDimensions.Space2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = column.label + when (activeDirection) {
                        StatsSortDirection.DESCENDING -> " ↓"
                        StatsSortDirection.ASCENDING -> " ↑"
                        null -> ""
                    },
                    style = textStyle,
                    color = VlrTheme.colors.textSecondary,
                    textAlign = column.textAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
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
    identityWidth: Dp,
    minHeight: Dp,
    identityTextStyle: TextStyle,
    valueTextStyle: TextStyle,
    onIdentityClick: ((T) -> Unit)?,
    identityTestTag: ((T) -> String)?,
    metricValueTestTag: ((T, K) -> String)?,
) {
    val identity = identityText(row)
    StatsTableRow(identityWidth, minHeight, horizontalScrollState, identity = {
        val tag = identityTestTag?.invoke(row)
        val interaction = if (onIdentityClick == null) Modifier else Modifier.clickable(role = Role.Button) { onIdentityClick(row) }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .then(if (tag == null) Modifier else Modifier.testTag(tag))
                .semantics { contentDescription = "$rowSemanticsLabel: $identity" }
                .then(interaction)
                .padding(horizontal = VlrDimensions.Space4),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(identity, style = identityTextStyle, color = VlrTheme.colors.textPrimary)
            identitySupportingText(row)?.let {
                Text(it, style = VlrTheme.typography.labelSmall, color = VlrTheme.colors.textSecondary)
            }
        }
    }) {
        columns.forEach { column ->
            key(column.key) {
                val value = column.displayValue(row)
                val tag = metricValueTestTag?.invoke(row, column.key)
                Box(
                    modifier = Modifier
                        .width(column.width)
                        .fillMaxHeight()
                        .then(if (tag == null) Modifier else Modifier.testTag(tag))
                        .semantics { contentDescription = "$identity, ${column.label}, $value" }
                        .padding(horizontal = VlrDimensions.Space2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value,
                        style = valueTextStyle,
                        color = VlrTheme.colors.textPrimary,
                        textAlign = column.textAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsTableRow(
    identityWidth: Dp,
    minHeight: Dp,
    horizontalScrollState: ScrollState,
    modifier: Modifier = Modifier,
    identity: @Composable () -> Unit,
    metrics: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(identityWidth).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            identity()
        }
        Row(
            modifier = Modifier.fillMaxHeight().horizontalScroll(horizontalScrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metrics()
        }
    }
}
