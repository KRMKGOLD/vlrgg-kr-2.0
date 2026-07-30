package kr.co.cotton.vlrgg_mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.semantics.Role
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
    fun buttonLoadingStateDisablesActivationAndKeepsContentMeasured() {
        val loading = VlrButtonState(enabled = true, isLoading = true)
        val disabled = VlrButtonState(enabled = false, isLoading = false)
        val enabled = VlrButtonState(enabled = true, isLoading = false)

        assertFalse(loading.isInteractive)
        assertTrue(loading.showsProgress)
        assertEquals(VlrButtonContentPresentation.InvisibleButMeasured, loading.contentPresentation)
        assertEquals(Role.Button, loading.role)
        assertEquals("로딩 중", loading.stateDescription)
        assertEquals(48.dp, loading.minimumTouchTarget)
        assertFalse(disabled.isInteractive)
        assertTrue(enabled.isInteractive)
        assertEquals(VlrButtonContentPresentation.Visible, enabled.contentPresentation)
    }

    @Test
    fun searchFieldVariantsPreserveSpecifiedVisualHeights() {
        assertEquals(56.dp, VlrSearchFieldVariant.Standard.visualHeight)
        assertEquals(40.dp, VlrSearchFieldVariant.Compact.visualHeight)
    }

    @Test
    fun searchFieldStateAndCallbacksCoverSubmitClearAndSemantics() {
        val editableValue = VlrSearchFieldState(
            value = "DRX",
            enabled = true,
            isLoading = false,
            errorMessage = null,
        )
        val loadingValue = editableValue.copy(isLoading = true)
        var submitted = ""
        var cleared = "not cleared"

        submitSearch(editableValue.value) { submitted = it }
        clearSearch { cleared = it }

        assertEquals("DRX", submitted)
        assertEquals("", cleared)
        assertTrue(editableValue.hasClearAction)
        assertFalse(loadingValue.hasClearAction)
        assertEquals("로딩 중", loadingValue.stateDescription)
        assertEquals(48.dp, editableValue.minimumTouchTarget)
    }

    @Test
    fun searchFocusAndErrorOutlinesDrawOutsideVisualBounds() {
        val focused = searchFieldOutline(isFocused = true, errorMessage = null, colors = VlrLightColors)
        val error = searchFieldOutline(
            isFocused = false,
            errorMessage = "검색어를 확인하세요",
            colors = VlrLightColors,
        )
        val default = searchFieldOutline(isFocused = false, errorMessage = null, colors = VlrLightColors)

        assertTrue(focused.isOutsideVisualBounds)
        assertEquals(2.dp, focused.width)
        assertEquals(VlrLightColors.focusOutline, focused.color)
        assertTrue(error.isOutsideVisualBounds)
        assertEquals(2.dp, error.width)
        assertEquals(VlrLightColors.actionPrimary, error.color)
        assertFalse(default.isOutsideVisualBounds)
        assertEquals(1.dp, default.width)
    }

    @Test
    fun statusChipIsInformationalAndHasNoButtonRole() {
        assertFalse(StatusChipSemantics.isInteractive)
        assertEquals(null, StatusChipSemantics.role)
    }

    @Test
    fun statusColorsMeetNormalTextContrastForEveryStatus() {
        StatusChipStatus.entries.forEach { status ->
            val colors = statusChipColors(status, VlrLightColors)
            assertTrue(
                contrastRatio(colors.content, colors.container) >= MinimumStatusTextContrast,
                "$status label contrast must meet 4.5:1",
            )
        }
    }

    @Test
    fun statusColorsKeepStatusIdentityInContainersAndBorders() {
        val live = statusChipColors(StatusChipStatus.Live, VlrLightColors)
        val upcoming = statusChipColors(StatusChipStatus.Upcoming, VlrLightColors)
        val completed = statusChipColors(StatusChipStatus.Completed, VlrLightColors)
        val postponed = statusChipColors(StatusChipStatus.Postponed, VlrLightColors)
        val cancelled = statusChipColors(StatusChipStatus.Cancelled, VlrLightColors)
        val unavailable = statusChipColors(StatusChipStatus.Unavailable, VlrLightColors)

        assertEquals(VlrLightColors.surfaceSelected, live.container)
        assertEquals(VlrLightColors.actionPrimary, live.border)
        assertEquals(VlrLightColors.statusUpcomingContainer, upcoming.container)
        assertEquals(VlrLightColors.statusUpcoming, upcoming.border)
        assertEquals(VlrLightColors.statusCompletedContainer, completed.container)
        assertEquals(VlrLightColors.statusCompleted, completed.border)
        assertEquals(VlrLightColors.statusPostponedContainer, postponed.container)
        assertEquals(VlrLightColors.statusPostponed, postponed.border)
        assertEquals(VlrLightColors.statusCancelled, cancelled.border)
        assertEquals(VlrLightColors.surface, unavailable.container)
        assertEquals(VlrLightColors.outline, unavailable.border)
        assertTrue(unavailable.usesDashedBorder)
        assertFalse(live.usesDashedBorder)
    }
}
