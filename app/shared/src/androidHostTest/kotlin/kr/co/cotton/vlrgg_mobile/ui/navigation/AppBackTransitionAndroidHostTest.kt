package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.navigationevent.NavigationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppBackTransitionAndroidHostTest {
    @Test
    fun backWithoutSwipeEdgeFadesInsteadOfSliding() {
        assertNull(predictiveBackSlideDirection(NavigationEvent.EDGE_NONE))
    }

    @Test
    fun leftEdgeSwipeSlidesCurrentScreenToTheRight() {
        assertEquals(SlideDirection.Right, predictiveBackSlideDirection(NavigationEvent.EDGE_LEFT))
    }

    @Test
    fun rightEdgeSwipeSlidesCurrentScreenToTheLeft() {
        assertEquals(SlideDirection.Left, predictiveBackSlideDirection(NavigationEvent.EDGE_RIGHT))
    }
}
