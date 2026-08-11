package kr.co.cotton.vlrgg_mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Light-only semantic colors from the Step 1 design contract.
 *
 * Feature and component code should consume these semantic roles rather than literal colors.
 */
data class VlrColors(
    val accentLive: Color,
    val accentLiveContainer: Color,
    val actionPrimary: Color,
    val onActionPrimary: Color,
    val actionPrimaryPressed: Color,
    val accentLiveOn: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfaceSelected: Color,
    val textPrimary: Color,
    val textBrand: Color,
    val textSecondary: Color,
    val outline: Color,
    val focusOutline: Color,
    val scrim: Color,
    val statusUpcomingContainer: Color,
    val statusUpcoming: Color,
    val statusCompletedContainer: Color,
    val statusCompleted: Color,
    val statusPostponedContainer: Color,
    val statusPostponed: Color,
    val statusCancelled: Color,
    val skeleton: Color,
)

val VlrLightColors = VlrColors(
    accentLive = Color(0xFFFF4654),
    accentLiveContainer = Color(0xFFFF4654).copy(alpha = 0.10f),
    actionPrimary = Color(0xFFD32F2F),
    onActionPrimary = Color(0xFFFFFFFF),
    actionPrimaryPressed = Color(0xFFB71C1C),
    accentLiveOn = Color(0xFF111823),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFF5F5F5),
    surfaceSelected = Color(0xFFFFEBEE),
    textPrimary = Color(0xFF18181B),
    textBrand = Color(0xFF111823),
    textSecondary = Color(0xFF71717A),
    outline = Color(0xFFE4E4E7),
    focusOutline = Color(0xFFFF4654),
    scrim = Color(0xFF000000).copy(alpha = 0.32f),
    statusUpcomingContainer = Color(0xFFE3F2FD),
    statusUpcoming = Color(0xFF1976D2),
    statusCompletedContainer = Color(0xFFE8F5E9),
    statusCompleted = Color(0xFF388E3C),
    statusPostponedContainer = Color(0xFFFFF3E0),
    statusPostponed = Color(0xFFF57C00),
    statusCancelled = Color(0xFF757575),
    skeleton = Color(0xFFE4E1E6),
)
