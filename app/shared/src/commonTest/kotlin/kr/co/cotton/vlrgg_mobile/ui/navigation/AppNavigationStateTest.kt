package kr.co.cotton.vlrgg_mobile.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationStateTest {
    @Test
    fun freshStateStartsAtMyPageWithoutAnOverlay() {
        val state = AppNavigationState()

        assertEquals(MyPageRoot, state.selectedRoot)
        assertTrue(state.overlay.isEmpty())
        assertEquals(listOf(MyPageRoot), state.backStack)
    }

    @Test
    fun searchIsPushedAboveEveryRootWithoutChangingSelection() {
        rootNavKeys.forEach { root ->
            val state = AppNavigationState(selectedRoot = root)

            state.push(Search)

            assertEquals(root, state.selectedRoot)
            assertEquals(listOf(root, Search), state.backStack)
        }
    }

    @Test
    fun backPopsDetailThenSearch() {
        val state = AppNavigationState()
        state.push(Search)
        state.push(TeamDetail(teamId = "1"))

        assertTrue(state.popOverlay())
        assertEquals(listOf(MyPageRoot, Search), state.backStack)
        assertTrue(state.popOverlay())
        assertEquals(listOf(MyPageRoot), state.backStack)
    }

    @Test
    fun selectingAnotherRootClearsTheWholeOverlay() {
        val state = AppNavigationState()
        state.push(Search)
        state.push(TeamDetail(teamId = "1"))

        state.selectRoot(NewsRoot)

        assertEquals(listOf(NewsRoot), state.backStack)
        state.selectRoot(MyPageRoot)
        assertEquals(listOf(MyPageRoot), state.backStack)
    }

    @Test
    fun selectingTheCurrentRootStillClearsTheWholeOverlay() {
        val state = AppNavigationState()
        state.push(Search)
        state.push(TeamDetail(teamId = "1"))

        state.selectRoot(MyPageRoot)

        assertEquals(listOf(MyPageRoot), state.backStack)
        assertTrue(state.overlay.isEmpty())
    }

    @Test
    fun backAtRootIsNotConsumedAndDoesNotCreateADestination() {
        val state = AppNavigationState()

        assertFalse(state.popOverlay())
        assertEquals(listOf(MyPageRoot), state.backStack)
    }

    @Test
    fun rootsCannotEnterTheOverlay() {
        rootNavKeys.forEach { root ->
            val state = AppNavigationState()

            assertFailsWith<IllegalArgumentException> {
                state.push(root)
            }
            assertEquals(listOf(MyPageRoot), state.backStack)
        }

        assertFailsWith<IllegalArgumentException> {
            AppNavigationState(overlay = listOf(Search, NewsRoot))
        }
    }
}
