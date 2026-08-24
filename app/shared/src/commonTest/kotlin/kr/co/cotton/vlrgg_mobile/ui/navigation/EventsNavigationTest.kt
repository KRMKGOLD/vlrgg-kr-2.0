package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventsNavigationTest {

    @Test
    fun eventSelectionPushesDetailAndBackRestoresEventsRootStack() {
        val state = AppNavigationState(createRootBackStacks(), initialSelectedRoot = EventsRoot)

        state.push(EventDetail(eventId = "event-39"))

        assertEquals(
            listOf<NavKey>(EventsRoot, EventDetail(eventId = "event-39")),
            state.currentBackStack,
        )
        assertTrue(state.popOverlay())
        assertEquals(listOf<NavKey>(EventsRoot), state.currentBackStack)
    }

    @Test
    fun eventDetailRemainsAttachedToEventsRootAcrossRootSwitches() {
        val state = AppNavigationState(createRootBackStacks(), initialSelectedRoot = EventsRoot)
        state.push(EventDetail(eventId = "event-39"))

        state.selectRoot(NewsRoot)
        state.selectRoot(EventsRoot)

        assertEquals(
            listOf<NavKey>(EventsRoot, EventDetail(eventId = "event-39")),
            state.currentBackStack,
        )
    }

    private fun createRootBackStacks(): MutableMap<RootNavKey, MutableList<NavKey>> =
        rootNavKeys.associateWithTo(mutableMapOf()) { root -> mutableListOf(root) }
}
