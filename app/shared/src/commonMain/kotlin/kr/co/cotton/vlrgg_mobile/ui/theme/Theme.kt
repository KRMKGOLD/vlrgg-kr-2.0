package kr.co.cotton.vlrgg_mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val VlrLightMaterialColorScheme: ColorScheme = lightColorScheme(
    primary = VlrLightColors.actionPrimary,
    onPrimary = VlrLightColors.onActionPrimary,
    primaryContainer = VlrLightColors.accentLiveContainer,
    onPrimaryContainer = VlrLightColors.accentLiveOn,
    secondary = VlrLightColors.accentLive,
    onSecondary = VlrLightColors.accentLiveOn,
    secondaryContainer = VlrLightColors.surfaceSelected,
    onSecondaryContainer = VlrLightColors.actionPrimary,
    background = VlrLightColors.surface,
    onBackground = VlrLightColors.textPrimary,
    surface = VlrLightColors.surface,
    onSurface = VlrLightColors.textPrimary,
    surfaceVariant = VlrLightColors.surfaceSubtle,
    onSurfaceVariant = VlrLightColors.textSecondary,
    outline = VlrLightColors.outline,
    error = VlrLightColors.actionPrimary,
    onError = VlrLightColors.onActionPrimary,
    scrim = VlrLightColors.scrim,
)

val LocalVlrColors = staticCompositionLocalOf { VlrLightColors }
val LocalVlrTypography = staticCompositionLocalOf { VlrTypography }

object VlrTheme {
    val colors: VlrColors
        @Composable
        @ReadOnlyComposable
        get() = LocalVlrColors.current

    val typography: VlrTypeScale
        @Composable
        @ReadOnlyComposable
        get() = LocalVlrTypography.current
}

@Composable
fun VlrTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalVlrColors provides VlrLightColors,
        LocalVlrTypography provides VlrTypography,
    ) {
        MaterialTheme(
            colorScheme = VlrLightMaterialColorScheme,
            typography = VlrMaterialTypography,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
fun initializeVlrMaterial3() {
    ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
}
