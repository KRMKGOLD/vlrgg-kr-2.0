package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StatsTableSortTest {
    @Test
    fun numericSortUsesNumbersAndCyclesDescendingAscendingThenSourceOrder() {
        val rows = listOf(Row("two", 2.0), Row("ten", 10.0))

        val descending = nextStatsSort<Metric>(null, Metric.VALUE)
        assertEquals(listOf("ten", "two"), stableSortedRows(rows, columns, descending).map { it.id })

        val ascending = nextStatsSort(descending, Metric.VALUE)
        assertEquals(listOf("two", "ten"), stableSortedRows(rows, columns, ascending).map { it.id })

        val off = nextStatsSort(ascending, Metric.VALUE)
        assertEquals(null, off)
        assertSame(rows, stableSortedRows(rows, columns, off))
    }

    @Test
    fun changingColumnStartsDescendingAndNullsStayLastInBothDirections() {
        val rows = listOf(Row("missing", null, 20.0), Row("zero", 0.0, 10.0), Row("one", 1.0, 30.0))

        val valueDescending = StatsSort(Metric.VALUE, StatsSortDirection.DESCENDING)
        assertEquals(listOf("one", "zero", "missing"), stableSortedRows(rows, columns, valueDescending).map { it.id })
        assertEquals(
            listOf("zero", "one", "missing"),
            stableSortedRows(rows, columns, valueDescending.copy(direction = StatsSortDirection.ASCENDING)).map { it.id },
        )

        assertEquals(StatsSort(Metric.OTHER, StatsSortDirection.DESCENDING), nextStatsSort(valueDescending, Metric.OTHER))
    }

    @Test
    fun tiesAllNullEmptyAndSingleRowsKeepSourceOrderWithoutMutatingInput() {
        val tied = listOf(Row("first", 1.0), Row("second", 1.0), Row("third", null), Row("fourth", null))
        val snapshot = tied.toList()
        val sort = StatsSort(Metric.VALUE, StatsSortDirection.DESCENDING)

        assertEquals(listOf("first", "second", "third", "fourth"), stableSortedRows(tied, columns, sort).map { it.id })
        assertEquals(
            listOf("first", "second", "third", "fourth"),
            stableSortedRows(tied, columns, sort.copy(direction = StatsSortDirection.ASCENDING)).map { it.id },
        )
        val allNull = listOf(Row("a", null), Row("b", null), Row("c", null))
        assertEquals(allNull, stableSortedRows(allNull, columns, sort))
        assertEquals(allNull, stableSortedRows(allNull, columns, sort.copy(direction = StatsSortDirection.ASCENDING)))
        assertEquals(snapshot, tied)
        assertEquals(emptyList(), stableSortedRows(emptyList(), columns, sort))
        val single = listOf(Row("only", null))
        assertEquals(single, stableSortedRows(single, columns, sort))
    }

    @Test
    fun currentSortAppliesToANewSnapshotWithoutRetainingOldRows() {
        val sort = StatsSort(Metric.VALUE, StatsSortDirection.DESCENDING)
        val first = listOf(Row("old-low", 1.0), Row("old-high", 2.0))
        val replacement = listOf(Row("new-low", 3.0), Row("new-high", 4.0))

        assertEquals(listOf("old-high", "old-low"), stableSortedRows(first, columns, sort).map { it.id })
        assertEquals(listOf("new-high", "new-low"), stableSortedRows(replacement, columns, sort).map { it.id })
        assertSame(replacement, stableSortedRows(replacement, columns, null))
    }

    private data class Row(val id: String, val value: Double?, val other: Double? = null)
    private enum class Metric { VALUE, OTHER }

    private companion object {
        val columns = listOf(
            StatsColumn<Row, Metric>(Metric.VALUE, "Value", 80.dp, TextAlign.Center, { it.value?.toString() ?: "—" }, Row::value),
            StatsColumn<Row, Metric>(Metric.OTHER, "Other", 80.dp, TextAlign.Center, { it.other?.toString() ?: "—" }, Row::other),
        )
    }
}
