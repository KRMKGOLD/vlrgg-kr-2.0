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

        assertEquals(EventsRoot, state.selectedRoot)
        assertEquals(listOf(EventDetail(eventId = "event-39")), state.overlay)
        assertTrue(state.popOverlay())
        assertTrue(state.overlay.isEmpty())
    }

    @Test
    fun eventDetailRemainsAttachedToEventsRootAcrossRootSwitches() {
        val state = AppNavigationState(createRootBackStacks(), initialSelectedRoot = EventsRoot)
        state.push(EventDetail(eventId = "event-39"))

        state.selectRoot(NewsRoot)
        assertTrue(state.overlay.isEmpty())
        state.selectRoot(EventsRoot)

        assertEquals(EventsRoot, state.selectedRoot)
        assertEquals(listOf(EventDetail(eventId = "event-39")), state.overlay)
    }

    private fun createRootBackStacks(): MutableMap<RootNavKey, MutableList<NavKey>> =
        rootNavKeys.associateWithTo(mutableMapOf()) { root -> mutableListOf(root) }
}
