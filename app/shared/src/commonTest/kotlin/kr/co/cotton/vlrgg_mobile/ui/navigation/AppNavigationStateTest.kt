package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationStateTest {
    @Test
    fun freshStateStartsAtMyPageWithoutAnOverlay() {
        val backStack = mutableListOf<NavKey>(MyPageRoot)
        val state = AppNavigationState(backStack)

        assertEquals(MyPageRoot, state.selectedRoot)
        assertTrue(state.overlay.isEmpty())
        assertEquals(listOf<NavKey>(MyPageRoot), backStack)
    }

    @Test
    fun searchIsPushedAboveEveryRootWithoutChangingSelection() {
        rootNavKeys.forEach { root ->
            val backStack = mutableListOf<NavKey>(root)
            val state = AppNavigationState(backStack)

            state.push(Search)

            assertEquals(root, state.selectedRoot)
            assertEquals(listOf<NavKey>(root, Search), backStack)
        }
    }

    @Test
    fun backPopsDetailThenSearch() {
        val backStack = mutableListOf<NavKey>(MyPageRoot)
        val state = AppNavigationState(backStack)
        state.push(Search)
        state.push(TeamDetail(teamId = "1"))

        assertTrue(state.popOverlay())
        assertEquals(listOf<NavKey>(MyPageRoot, Search), backStack)
        assertTrue(state.popOverlay())
        assertEquals(listOf<NavKey>(MyPageRoot), backStack)
    }

    @Test
    fun selectingAnotherRootClearsTheWholeOverlay() {
        val backStack = mutableListOf<NavKey>(MyPageRoot)
        val state = AppNavigationState(backStack)
        state.push(Search)
        state.push(TeamDetail(teamId = "1"))

        state.selectRoot(NewsRoot)

        assertEquals(listOf<NavKey>(NewsRoot), backStack)
        state.selectRoot(MyPageRoot)
        assertEquals(listOf<NavKey>(MyPageRoot), backStack)
    }

    @Test
    fun selectingTheCurrentRootStillClearsTheWholeOverlay() {
        val backStack = mutableListOf<NavKey>(MyPageRoot)
        val state = AppNavigationState(backStack)
        state.push(Search)
        state.push(TeamDetail(teamId = "1"))

        state.selectRoot(MyPageRoot)

        assertEquals(listOf<NavKey>(MyPageRoot), backStack)
        assertTrue(state.overlay.isEmpty())
    }

    @Test
    fun backAtRootIsNotConsumedAndDoesNotCreateADestination() {
        val backStack = mutableListOf<NavKey>(MyPageRoot)
        val state = AppNavigationState(backStack)

        assertFalse(state.popOverlay())
        assertEquals(listOf<NavKey>(MyPageRoot), backStack)
    }

    @Test
    fun rootsCannotEnterTheOverlay() {
        rootNavKeys.forEach { root ->
            val backStack = mutableListOf<NavKey>(MyPageRoot)
            val state = AppNavigationState(backStack)

            assertFailsWith<IllegalArgumentException> {
                state.push(root)
            }
            assertEquals(listOf<NavKey>(MyPageRoot), backStack)
        }

        assertFailsWith<IllegalArgumentException> {
            AppNavigationState(mutableListOf<NavKey>(MyPageRoot, Search, NewsRoot))
        }
    }

    @Test
    fun navigationStateOnlyMutatesTheNavigation3BackStackItReceives() {
        val backStack = mutableListOf<NavKey>(EventsRoot, Search)
        val state = AppNavigationState(backStack)

        state.push(TeamDetail(teamId = "1001"))
        state.popOverlay()
        state.selectRoot(AboutRoot)

        assertEquals(listOf<NavKey>(AboutRoot), backStack)
    }
}
