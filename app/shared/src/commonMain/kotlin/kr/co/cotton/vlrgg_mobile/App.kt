package kr.co.cotton.vlrgg_mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kr.co.cotton.vlrgg_mobile.di.AppGraph
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutPlatform
import kr.co.cotton.vlrgg_mobile.ui.navigation.AppNavigation
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

@Composable
fun App(
    graph: AppGraph,
    aboutPlatform: AboutPlatform,
) {
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
    ) {
        VlrTheme {
            AppNavigation(aboutPlatform = aboutPlatform)
        }
    }
}
