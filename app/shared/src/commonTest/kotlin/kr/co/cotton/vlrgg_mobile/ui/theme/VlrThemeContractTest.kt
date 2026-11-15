package kr.co.cotton.vlrgg_mobile.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class VlrThemeContractTest {

    @Test
    fun materialSchemeMapsPrimarySemanticRoles() {
        assertEquals(VlrLightColors.accent, VlrLightMaterialColorScheme.primary)
        assertEquals(VlrLightColors.onPanel, VlrLightMaterialColorScheme.onPrimary)
        assertEquals(VlrLightColors.canvas, VlrLightMaterialColorScheme.background)
        assertEquals(VlrLightColors.surface, VlrLightMaterialColorScheme.surface)
        assertEquals(VlrLightColors.ink, VlrLightMaterialColorScheme.onSurface)
        assertEquals(VlrLightColors.line, VlrLightMaterialColorScheme.outline)
        assertEquals(VlrLightColors.panel, VlrLightMaterialColorScheme.inverseSurface)
        assertEquals(VlrLightColors.onPanel, VlrLightMaterialColorScheme.inverseOnSurface)
        assertEquals(VlrLightColors.onPanelAccent, VlrLightMaterialColorScheme.inversePrimary)
    }

    @Test
    fun colorTokensMatchV3Contract() {
        assertEquals(Color(0xFFF1F2F4), VlrLightColors.canvas)
        assertEquals(Color(0xFF111418), VlrLightColors.ink)
        assertEquals(Color(0xFF646B78), VlrLightColors.muted)
        assertEquals(Color(0xFFCF1F33), VlrLightColors.accent)
        assertEquals(Color(0xFF15191E), VlrLightColors.panel)
        assertEquals(Color(0xFF2F6FED), VlrLightColors.focus)
        assertEquals(Color(0xFF0B0D10).copy(alpha = 0.40f), VlrLightColors.scrim)
    }

    @Test
    fun typeScalePreservesV3RoleMetrics() {
        val numeric = FontFamily.Monospace
        val scale = vlrTypeScale(numeric)

        assertEquals(28.sp, scale.display.fontSize)
        assertEquals(34.sp, scale.display.lineHeight)
        assertEquals(FontWeight.Black, scale.display.fontWeight)
        assertEquals(24.sp, scale.title.fontSize)
        assertEquals(31.sp, scale.title.lineHeight)
        assertEquals(22.sp, scale.body.lineHeight)
        assertEquals(20.sp, scale.bodySm.lineHeight)
        assertEquals(12.sp, scale.label.fontSize)
        assertEquals(16.sp, scale.label.lineHeight)
        assertEquals(FontFamily.SansSerif, scale.body.fontFamily)
        assertEquals(52.sp, scale.scoreXl.fontSize)
        assertEquals(22.sp, scale.scoreM.lineHeight)
        assertEquals(FontWeight.SemiBold, scale.stat.fontWeight)
        assertEquals(numeric, scale.scoreXl.fontFamily)
        assertEquals(numeric, scale.tag.fontFamily)

        val material = vlrMaterialTypography(scale)
        assertEquals(scale.bodySm, material.bodyLarge)
        assertEquals(scale.label, material.labelSmall)
    }

    @Test
    fun shapeAndSpacingFollowV3Contract() {
        assertEquals(20.dp, VlrDimensions.Space5)
        assertEquals(2.dp, VlrDimensions.CornerRadius)
        assertEquals(10.dp, VlrDimensions.ButtonCornerRadius)
        assertEquals(4.dp, VlrDimensions.OverlayCornerRadius)
        assertEquals(56.dp, VlrDimensions.HeaderHeight)
        assertEquals(48.dp, VlrDimensions.MinimumTouchTarget)
    }
}
