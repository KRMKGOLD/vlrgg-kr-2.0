package kr.co.cotton.vlrgg_mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kr.co.cotton.vlrgg_mobile.di.AppGraph
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutPlatform
import kr.co.cotton.vlrgg_mobile.ui.navigation.AppNavigation
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import androidx.compose.runtime.remember
import kr.co.cotton.vlrgg_mobile.ui.component.LocalStatusBarIconsController
import kr.co.cotton.vlrgg_mobile.ui.component.StatusBarIconsApplier
import kr.co.cotton.vlrgg_mobile.ui.component.StatusBarIconsController

@Composable
fun App(
    graph: AppGraph,
    aboutPlatform: AboutPlatform,
    readLicenseDefinitions: suspend () -> String,
    statusBarIconsApplier: StatusBarIconsApplier = StatusBarIconsApplier.None,
) {
    val statusBarIconsController = remember(statusBarIconsApplier) {
        StatusBarIconsController(statusBarIconsApplier)
    }
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
        LocalStatusBarIconsController provides statusBarIconsController,
    ) {
        VlrTheme {
            AppNavigation(
                aboutPlatform = aboutPlatform,
                readLicenseDefinitions = readLicenseDefinitions,
            )
        }
    }
}
