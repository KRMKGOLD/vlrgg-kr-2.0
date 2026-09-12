package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.theme.initializeVlrMaterial3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class VlrStatsTableUiTest {
    @Test
    fun sortableAndDisplayTablesRemainIndependentAndDisplayModeHasNoSortAffordance() = runComposeUiTest {
        initializeVlrMaterial3()
        var sortableClicks = 0
        var identityClicks = 0
        var sortableScroll = 0
        var displayScroll = 0
        setContent {
            VlrTheme {
                Column {
                    val firstScroll = rememberScrollState(20)
                    val secondScroll = rememberScrollState()
                    var sort by remember { mutableStateOf<StatsSort<Metric>?>(null) }
                    VlrStatsTable(
                        rows = rows,
                        rowKey = Row::id,
                        identityHeader = "Player",
                        identityText = Row::name,
                        rowSemanticsLabel = "player",
                        columns = columns,
                        horizontalScrollState = firstScroll,
                        identityWidth = 132.dp,
                        headerMinHeight = 56.dp,
                        rowMinHeight = 56.dp,
                        identityTextStyle = VlrTheme.typography.bodyStrong,
                        headerTextStyle = VlrTheme.typography.label,
                        valueTextStyle = VlrTheme.typography.label,
                        modifier = Modifier.width(360.dp).testTag("sortable-table"),
                        sort = sort,
                        onSortColumn = {
                            sortableClicks += 1
                            sort = nextStatsSort(sort, it)
                        },
                        onIdentityClick = { identityClicks += 1 },
                        identityTestTag = { "sortable-${it.id}" },
                        metricHeaderTestTag = { "sortable-header-${it.name}" },
                        metricValueTestTag = { row, metric -> "sortable-${row.id}-${metric.name}" },
                    )
                    VlrStatsTable(
                        rows = rows,
                        rowKey = Row::id,
                        identityHeader = "Agent",
                        identityText = Row::name,
                        rowSemanticsLabel = "agent",
                        columns = columns,
                        horizontalScrollState = secondScroll,
                        identityWidth = 120.dp,
                        headerMinHeight = 56.dp,
                        rowMinHeight = 52.dp,
                        identityTextStyle = VlrTheme.typography.bodyStrong,
                        headerTextStyle = VlrTheme.typography.label,
                        valueTextStyle = VlrTheme.typography.label,
                        modifier = Modifier.width(360.dp).testTag("display-table"),
                        sort = StatsSort(Metric.VALUE, StatsSortDirection.DESCENDING),
                        onSortColumn = null,
                        identityTestTag = { "display-${it.id}" },
                        metricHeaderTestTag = { "display-header-${it.name}" },
                    )
                    sortableScroll = firstScroll.value
                    displayScroll = secondScroll.value
                }
            }
        }

        val displayHeader = onNodeWithTag("display-header-VALUE").fetchSemanticsNode().config
        assertFalse(displayHeader.contains(SemanticsActions.OnClick))
        assertFalse(displayHeader.contains(SemanticsProperties.StateDescription))
        onNodeWithText("Value ↓").assertDoesNotExist()
        assertEquals(0, sortableClicks)
        assertEquals(20, sortableScroll)
        assertEquals(0, displayScroll)
        assertTrue(
            onNodeWithTag("display-two").fetchSemanticsNode().boundsInRoot.top <
                onNodeWithTag("display-ten").fetchSemanticsNode().boundsInRoot.top,
        )

        onNodeWithTag("sortable-header-VALUE").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, sortableClicks)
        assertEquals("내림차순", onNodeWithTag("sortable-header-VALUE").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertTrue(
            onNodeWithTag("sortable-ten").fetchSemanticsNode().boundsInRoot.top <
                onNodeWithTag("sortable-two").fetchSemanticsNode().boundsInRoot.top,
        )
        val headerLeft = onNodeWithTag("sortable-header-VALUE").fetchSemanticsNode().boundsInRoot.left
        assertEquals(headerLeft, onNodeWithTag("sortable-ten-VALUE").fetchSemanticsNode().boundsInRoot.left)
        assertEquals(headerLeft, onNodeWithTag("sortable-two-VALUE").fetchSemanticsNode().boundsInRoot.left)
        onNodeWithTag("sortable-ten").assertWidthIsEqualTo(132.dp).performClick()
        assertEquals(1, identityClicks)
        assertFalse(onNodeWithTag("display-two").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun metricSemanticsNameTheIdentityLabelAndValueAndRowsShareDynamicHeight() = runComposeUiTest {
        initializeVlrMaterial3()
        val largeValue = 1234567890.123
        setContent {
            VlrTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    VlrStatsTable(
                        rows = listOf(Row("long", "A very long player identity that wraps", largeValue)),
                        rowKey = Row::id,
                        identityHeader = "A very long identity header that wraps",
                        identityText = Row::name,
                        identitySupportingText = { "A very long supporting team name" },
                        rowSemanticsLabel = "player",
                        columns = columns,
                        horizontalScrollState = rememberScrollState(),
                        identityWidth = 132.dp,
                        headerMinHeight = 56.dp,
                        rowMinHeight = 56.dp,
                        identityTextStyle = VlrTheme.typography.bodyStrong,
                        headerTextStyle = VlrTheme.typography.label,
                        valueTextStyle = VlrTheme.typography.label,
                        modifier = Modifier.width(360.dp).testTag("common-360-long-table"),
                        metricHeaderTestTag = { "header-${it.name}" },
                        metricValueTestTag = { row, metric -> "value-${row.id}-${metric.name}" },
                        identityTestTag = { "identity-${it.id}" },
                    )
                }
            }
        }

        onNodeWithTag("common-360-long-table").assertWidthIsEqualTo(360.dp)
        onNodeWithContentDescription("A very long player identity that wraps, Value, $largeValue").assertExists()
        val identityBounds = onNodeWithTag("identity-long").fetchSemanticsNode().boundsInRoot
        val valueBounds = onNodeWithTag("value-long-VALUE").fetchSemanticsNode().boundsInRoot
        assertEquals(identityBounds.top, valueBounds.top)
        assertEquals(identityBounds.bottom, valueBounds.bottom)
        assertTrue(identityBounds.height > 56f)
        onNodeWithTag("common-360-long-table").captureStatsEvidence("common-360-long")
    }

    @Test
    fun lazyModeKeepsStableRowsReachableAtTheEnd() = runComposeUiTest {
        initializeVlrMaterial3()
        setContent {
            VlrTheme {
                Box(Modifier.fillMaxSize()) {
                    VlrStatsTable(
                        rows = (1..30).map { Row("row-$it", "Player $it", it.toDouble()) },
                        rowKey = Row::id,
                        identityHeader = "Player",
                        identityText = Row::name,
                        rowSemanticsLabel = "player",
                        columns = columns,
                        horizontalScrollState = rememberScrollState(),
                        identityWidth = 132.dp,
                        headerMinHeight = 56.dp,
                        rowMinHeight = 56.dp,
                        identityTextStyle = VlrTheme.typography.bodyStrong,
                        headerTextStyle = VlrTheme.typography.label,
                        valueTextStyle = VlrTheme.typography.label,
                        modifier = Modifier.width(360.dp).height(260.dp),
                        lazyListState = rememberLazyListState(),
                        identityTestTag = { "identity-${it.id}" },
                    )
                }
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("identity-row-30"))
        onNodeWithTag("identity-row-30").assertExists()
    }

    private data class Row(val id: String, val name: String, val value: Double?)
    private enum class Metric { VALUE, MISSING, EXTRA }

    private companion object {
        val rows = listOf(Row("two", "Two", 2.0), Row("ten", "Ten", 10.0))
        val columns = listOf(
            StatsColumn<Row, Metric>(Metric.VALUE, "Value", 84.dp, TextAlign.Center, { it.value?.toString() ?: "—" }, Row::value),
            StatsColumn<Row, Metric>(Metric.MISSING, "A very long metric header", 84.dp, TextAlign.Center, { "—" }, { null }),
            StatsColumn<Row, Metric>(Metric.EXTRA, "Extra", 84.dp, TextAlign.Center, { "999999999999999999" }, { 999999999999999999.0 }),
        )
    }
}
