package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

/** Matches Navigation 3's Android default push/pop fade, which the app keeps for both. */
private const val ButtonBackFadeDurationMillis = 700

private const val GestureBackSlideDurationMillis = 300

private const val GestureBackPreviousInitialAlpha = 0.6f

/**
 * Direction the current screen leaves in during a predictive back, or `null` for a fade.
 *
 * Android 16 also delivers a three-button back tap as a predictive back with [NavigationEvent.EDGE_NONE];
 * only an edge swipe follows the finger.
 */
internal fun predictiveBackSlideDirection(@NavigationEvent.SwipeEdge swipeEdge: Int): SlideDirection? =
    when (swipeEdge) {
        NavigationEvent.EDGE_LEFT -> SlideDirection.Right
        NavigationEvent.EDGE_RIGHT -> SlideDirection.Left
        else -> null
    }

// The gesture progress already supplies the motion curve while seeking, so the specs stay linear.
internal actual fun <T : Any> appPredictivePopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
    { swipeEdge ->
        val direction = predictiveBackSlideDirection(swipeEdge)
        if (direction == null) {
            ContentTransform(
                fadeIn(tween(ButtonBackFadeDurationMillis, easing = LinearEasing)),
                fadeOut(tween(ButtonBackFadeDurationMillis, easing = LinearEasing)),
            )
        } else {
            ContentTransform(
                slideIntoContainer(
                    towards = direction,
                    animationSpec = tween(GestureBackSlideDurationMillis, easing = LinearEasing),
                    initialOffset = { it / 4 },
                ) + fadeIn(
                    animationSpec = tween(GestureBackSlideDurationMillis, easing = LinearEasing),
                    initialAlpha = GestureBackPreviousInitialAlpha,
                ),
                slideOutOfContainer(
                    towards = direction,
                    animationSpec = tween(GestureBackSlideDurationMillis, easing = LinearEasing),
                ),
            )
        }
    }
