package kr.co.cotton.vlrgg_mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.cotton.vlrgg_mobile.ui.theme.vlrTypeScale
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrLightColors

class ComponentContractTest {

    @Test
    fun buttonVariantsKeepV3HeightAndPadding() {
        VlrButtonVariant.entries.forEach { variant ->
            assertEquals(48.dp, variant.visualHeight, "$variant height")
        }
        assertEquals(24.dp, VlrButtonVariant.Primary.horizontalPadding)
        assertEquals(24.dp, VlrButtonVariant.Secondary.horizontalPadding)
        assertEquals(12.dp, VlrButtonVariant.Text.horizontalPadding)
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
        assertEquals(0.5f, disabled.containerAlpha)
        assertEquals(1f, loading.containerAlpha)
        assertTrue(enabled.isInteractive)
        assertEquals(VlrButtonContentPresentation.Visible, enabled.contentPresentation)
    }

    @Test
    fun searchFieldKeepsV3VisualHeight() {
        assertEquals(48.dp, VlrSearchFieldHeight)
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
    fun searchOutlineFollowsFocusSubmittedValueAndError() {
        val focused = searchFieldOutline(isFocused = true, hasValue = false, errorMessage = null, colors = VlrLightColors)
        val submitted = searchFieldOutline(isFocused = false, hasValue = true, errorMessage = null, colors = VlrLightColors)
        val empty = searchFieldOutline(isFocused = false, hasValue = false, errorMessage = null, colors = VlrLightColors)
        val error = searchFieldOutline(
            isFocused = true,
            hasValue = true,
            errorMessage = "검색어를 확인하세요",
            colors = VlrLightColors,
        )

        assertEquals(VlrSearchFieldOutline(VlrLightColors.ink, 2.dp), focused)
        assertEquals(VlrSearchFieldOutline(VlrLightColors.lineStrong, 1.dp), submitted)
        assertNull(empty)
        assertEquals(VlrSearchFieldOutline(VlrLightColors.accent, 2.dp), error)
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
    fun statusColorsFollowV3ChipVariants() {
        fun colors(status: StatusChipStatus) = statusChipColors(status, VlrLightColors)

        assertEquals(StatusChipColors(VlrLightColors.accent, VlrLightColors.onPanel), colors(StatusChipStatus.Live))
        assertEquals(StatusChipColors(VlrLightColors.accent, VlrLightColors.onPanel), colors(StatusChipStatus.InProgress))
        assertEquals(StatusChipColors(VlrLightColors.panel2, VlrLightColors.onPanel), colors(StatusChipStatus.Upcoming))
        listOf(StatusChipStatus.Completed, StatusChipStatus.Cancelled, StatusChipStatus.Unavailable).forEach {
            assertEquals(StatusChipColors(VlrLightColors.panel2, VlrLightColors.onPanelMuted), colors(it), "$it")
        }
        assertEquals(StatusChipColors(VlrLightColors.warnSoft, VlrLightColors.warn), colors(StatusChipStatus.Postponed))
        assertEquals(StatusChipColors(VlrLightColors.surfaceSunken, VlrLightColors.ink2), colors(StatusChipStatus.Version))
    }

    @Test
    fun liveChipUsesSairaTagWhileInProgressUsesSystemLabel() {
        val typography = vlrTypeScale(FontFamily.Monospace)

        val live = statusChipTextStyle(StatusChipStatus.Live, typography)
        val inProgress = statusChipTextStyle(StatusChipStatus.InProgress, typography)

        assertEquals(FontFamily.Monospace, live.fontFamily)
        assertEquals(13.sp, live.fontSize)
        assertEquals(FontWeight.Bold, live.fontWeight)
        assertEquals(typography.label.fontFamily, inProgress.fontFamily)
        assertEquals(12.sp, inProgress.fontSize)
    }
}
