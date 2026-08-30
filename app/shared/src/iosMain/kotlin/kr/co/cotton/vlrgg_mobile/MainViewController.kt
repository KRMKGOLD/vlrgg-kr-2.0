package kr.co.cotton.vlrgg_mobile

import androidx.compose.ui.window.ComposeUIViewController
import kr.co.cotton.vlrgg_mobile.di.AppGraph
import kr.co.cotton.vlrgg_mobile.ui.theme.initializeVlrMaterial3
import platform.UIKit.UIViewController

fun MainViewController(graph: AppGraph): UIViewController {
    initializeVlrMaterial3()
    return ComposeUIViewController { App(graph) }
}
