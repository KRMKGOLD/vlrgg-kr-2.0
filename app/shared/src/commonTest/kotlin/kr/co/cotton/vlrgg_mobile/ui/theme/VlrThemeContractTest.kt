package kr.co.cotton.vlrgg_mobile.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.unit.sp

class VlrThemeContractTest {

    @Test
    fun materialSchemeMapsPrimarySemanticRoles() {
        assertEquals(VlrLightColors.actionPrimary, VlrLightMaterialColorScheme.primary)
        assertEquals(VlrLightColors.onActionPrimary, VlrLightMaterialColorScheme.onPrimary)
        assertEquals(VlrLightColors.surface, VlrLightMaterialColorScheme.surface)
        assertEquals(VlrLightColors.textPrimary, VlrLightMaterialColorScheme.onSurface)
        assertEquals(VlrLightColors.focusOutline, VlrLightColors.accentLive)
    }

    @Test
    fun typeScalePreservesStepOneRoleMetrics() {
        assertEquals(28.sp, VlrTypography.display.fontSize)
        assertEquals(34.sp, VlrTypography.display.lineHeight)
        assertEquals(22.sp, VlrTypography.pageTitle.fontSize)
        assertEquals(20.sp, VlrTypography.body.lineHeight)
        assertEquals(10.sp, VlrTypography.navLabel.fontSize)
        assertEquals(12.sp, VlrTypography.navLabel.lineHeight)
    }
}
