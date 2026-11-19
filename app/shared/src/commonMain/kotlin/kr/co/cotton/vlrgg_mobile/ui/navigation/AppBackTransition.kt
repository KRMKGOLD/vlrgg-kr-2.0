package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

/**
 * Predictive back transition passed to `NavDisplay`.
 *
 * Android replaces the library's shrinking default: a back press without a dragging finger fades,
 * an edge swipe slides the current screen away. iOS keeps the library's iOS-style slide.
 */
internal expect fun <T : Any> appPredictivePopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform
