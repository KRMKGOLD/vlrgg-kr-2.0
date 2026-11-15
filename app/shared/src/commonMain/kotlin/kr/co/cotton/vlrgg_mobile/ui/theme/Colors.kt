package kr.co.cotton.vlrgg_mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Light-only semantic colors from the v3 design contract (`DESIGN.md` section 4).
 *
 * Feature and component code should consume these semantic roles rather than literal colors.
 */
data class VlrColors(
    val canvas: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val skeleton: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val live: Color,
    val panel: Color,
    val panel2: Color,
    val onPanel: Color,
    val onPanelMuted: Color,
    val onPanelAccent: Color,
    val warn: Color,
    val warnSoft: Color,
    val focus: Color,
    val disabled: Color,
    val scrim: Color,
    // v3에서는 상태를 텍스트로 표시하므로 해당 화면 적용 단계에서 제거한다.
    val statusUpcomingContainer: Color,
    val statusUpcoming: Color,
    val statusCompletedContainer: Color,
    val statusCompleted: Color,
    val statusCancelled: Color,
)

val VlrLightColors = VlrColors(
    canvas = Color(0xFFF1F2F4),
    surface = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFF5F6F8),
    skeleton = Color(0xFFE8EAED),
    line = Color(0xFFE4E6EA),
    lineStrong = Color(0xFFC9CED6),
    ink = Color(0xFF111418),
    ink2 = Color(0xFF3D434C),
    muted = Color(0xFF646B78),
    accent = Color(0xFFCF1F33),
    accentPressed = Color(0xFFA8172A),
    accentSoft = Color(0xFFFDECEE),
    live = Color(0xFFFF3B4B),
    panel = Color(0xFF15191E),
    panel2 = Color(0xFF232930),
    onPanel = Color(0xFFFFFFFF),
    onPanelMuted = Color(0xFFA4ABB6),
    onPanelAccent = Color(0xFFFF7A86),
    warn = Color(0xFF9A5800),
    warnSoft = Color(0xFFFFF4E0),
    focus = Color(0xFF2F6FED),
    disabled = Color(0xFF9AA1AC),
    scrim = Color(0xFF0B0D10).copy(alpha = 0.40f),
    statusUpcomingContainer = Color(0xFFE3F2FD),
    statusUpcoming = Color(0xFF1976D2),
    statusCompletedContainer = Color(0xFFE8F5E9),
    statusCompleted = Color(0xFF388E3C),
    statusCancelled = Color(0xFF757575),
)
