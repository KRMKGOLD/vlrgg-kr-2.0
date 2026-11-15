package kr.co.cotton.vlrgg_mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusBarIconsControllerTest {
    @Test
    fun lightIconsStayWhileAnyDarkBandIsComposedAndRootIconsReturnAfterTheLast() {
        val applied = mutableListOf<Boolean>()
        val controller = StatusBarIconsController { applied += it }

        // Team detail opens, then a player detail is pushed over it.
        controller.acquireLightIcons()
        controller.acquireLightIcons()
        assertEquals(listOf(true), applied)

        // Popping the player keeps the team band light; leaving the team restores dark root icons.
        controller.releaseLightIcons()
        assertEquals(listOf(true), applied)
        controller.releaseLightIcons()
        assertEquals(listOf(true, false), applied)

        // An unmatched release does nothing.
        controller.releaseLightIcons()
        assertEquals(listOf(true, false), applied)
    }
}
