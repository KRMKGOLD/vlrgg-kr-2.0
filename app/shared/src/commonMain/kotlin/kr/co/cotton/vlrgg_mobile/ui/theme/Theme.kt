package kr.co.cotton.vlrgg_mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

val VlrLightMaterialColorScheme: ColorScheme = lightColorScheme(
    primary = VlrLightColors.accent,
    onPrimary = VlrLightColors.onPanel,
    primaryContainer = VlrLightColors.accentSoft,
    onPrimaryContainer = VlrLightColors.accent,
    secondaryContainer = VlrLightColors.accentSoft,
    onSecondaryContainer = VlrLightColors.ink,
    background = VlrLightColors.canvas,
    onBackground = VlrLightColors.ink,
    surface = VlrLightColors.surface,
    onSurface = VlrLightColors.ink,
    surfaceVariant = VlrLightColors.surfaceSunken,
    onSurfaceVariant = VlrLightColors.muted,
    outline = VlrLightColors.line,
    outlineVariant = VlrLightColors.lineStrong,
    error = VlrLightColors.accent,
    onError = VlrLightColors.onPanel,
    scrim = VlrLightColors.scrim,
    inverseSurface = VlrLightColors.panel,
    inverseOnSurface = VlrLightColors.onPanel,
    inversePrimary = VlrLightColors.onPanelAccent,
)

val LocalVlrColors = staticCompositionLocalOf { VlrLightColors }

// Outside VlrTheme the numeric roles fall back to the system sans-serif.
val LocalVlrTypography = staticCompositionLocalOf { vlrTypeScale(FontFamily.SansSerif) }

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
    val typography = rememberVlrTypography()
    val materialTypography = remember(typography) { vlrMaterialTypography(typography) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalVlrColors provides VlrLightColors,
        LocalVlrTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = VlrLightMaterialColorScheme,
            typography = materialTypography,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
fun initializeVlrMaterial3() {
    ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
}
