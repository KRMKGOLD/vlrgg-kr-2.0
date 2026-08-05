package kr.co.cotton.vlrgg_mobile

import androidx.compose.ui.window.ComposeUIViewController
import kr.co.cotton.vlrgg_mobile.di.AppGraph

fun MainViewController(graph: AppGraph) = ComposeUIViewController { App(graph) }
