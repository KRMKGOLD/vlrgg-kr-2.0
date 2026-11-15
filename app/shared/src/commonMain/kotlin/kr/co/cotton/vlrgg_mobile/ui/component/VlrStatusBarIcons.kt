package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf

/** Platform hook that switches the status bar icons between dark (light screens) and light (dark band). */
fun interface StatusBarIconsApplier {
    fun apply(lightIcons: Boolean)

    companion object {
        /** Previews, tests and hosts that keep the platform default. */
        val None = StatusBarIconsApplier { }
    }
}

/**
 * Counts the dark top bands in composition. Light icons stay on while at least one is present,
 * so pushing one dark detail over another keeps them, and leaving the last one restores the
 * dark root icons.
 */
class StatusBarIconsController(private val applier: StatusBarIconsApplier) {
    private var lightRequests = 0

    fun acquireLightIcons() {
        lightRequests += 1
        if (lightRequests == 1) applier.apply(lightIcons = true)
    }

    fun releaseLightIcons() {
        if (lightRequests == 0) return
        lightRequests -= 1
        if (lightRequests == 0) applier.apply(lightIcons = false)
    }
}

val LocalStatusBarIconsController = staticCompositionLocalOf { StatusBarIconsController(StatusBarIconsApplier.None) }

/** Keeps light status bar icons while the caller (a dark top band) is in composition. */
@Composable
fun LightStatusBarIconsEffect() {
    val controller = LocalStatusBarIconsController.current
    DisposableEffect(controller) {
        controller.acquireLightIcons()
        onDispose { controller.releaseLightIcons() }
    }
}
