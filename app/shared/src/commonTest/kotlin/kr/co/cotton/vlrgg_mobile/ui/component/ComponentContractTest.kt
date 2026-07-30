package kr.co.cotton.vlrgg_mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrLightColors

class ComponentContractTest {

    @Test
    fun buttonSizeContractKeepsVisualsInsideAccessibleTarget() {
        assertEquals(40.dp, VlrButtonSize.Standard.visualHeight)
        assertEquals(16.dp, VlrButtonSize.Standard.horizontalPadding)
        assertEquals(32.dp, VlrButtonSize.Compact.visualHeight)
        assertEquals(12.dp, VlrButtonSize.Compact.horizontalPadding)
    }

    @Test
    fun searchFieldVariantsPreserveSpecifiedVisualHeights() {
        assertEquals(56.dp, VlrSearchFieldVariant.Standard.visualHeight)
        assertEquals(40.dp, VlrSearchFieldVariant.Compact.visualHeight)
    }

    @Test
    fun statusColorsMapEverySupportedStatusToSemanticTokens() {
        val live = statusChipColors(StatusChipStatus.Live, VlrLightColors)
        val upcoming = statusChipColors(StatusChipStatus.Upcoming, VlrLightColors)
        val completed = statusChipColors(StatusChipStatus.Completed, VlrLightColors)
        val postponed = statusChipColors(StatusChipStatus.Postponed, VlrLightColors)
        val cancelled = statusChipColors(StatusChipStatus.Cancelled, VlrLightColors)
        val partial = statusChipColors(StatusChipStatus.Partial, VlrLightColors)
        val stale = statusChipColors(StatusChipStatus.Stale, VlrLightColors)
        val unavailable = statusChipColors(StatusChipStatus.Unavailable, VlrLightColors)

        assertEquals(VlrLightColors.surfaceSelected, live.container)
        assertEquals(VlrLightColors.actionPrimary, live.content)
        assertEquals(VlrLightColors.statusUpcomingContainer, upcoming.container)
        assertEquals(VlrLightColors.statusUpcoming, upcoming.content)
        assertEquals(VlrLightColors.statusCompletedContainer, completed.container)
        assertEquals(VlrLightColors.statusCompleted, completed.content)
        assertEquals(VlrLightColors.statusPostponedContainer, postponed.container)
        assertEquals(VlrLightColors.statusPostponed, postponed.content)
        assertEquals(VlrLightColors.statusCancelled, cancelled.content)
        assertEquals(VlrLightColors.textSecondary, partial.content)
        assertEquals(partial, stale)
        assertEquals(VlrLightColors.surface, unavailable.container)
        assertEquals(VlrLightColors.outline, unavailable.border)
        assertTrue(unavailable.usesDashedBorder)
        assertFalse(live.usesDashedBorder)
    }
}
