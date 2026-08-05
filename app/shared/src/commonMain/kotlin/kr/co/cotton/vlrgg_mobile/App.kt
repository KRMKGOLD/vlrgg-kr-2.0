package kr.co.cotton.vlrgg_mobile

import androidx.compose.runtime.Composable
import kr.co.cotton.vlrgg_mobile.di.AppGraph
import kr.co.cotton.vlrgg_mobile.ui.navigation.AppNavigation
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

@Composable
fun App(graph: AppGraph) {
    VlrTheme {
        AppNavigation(graph = graph)
    }
}
