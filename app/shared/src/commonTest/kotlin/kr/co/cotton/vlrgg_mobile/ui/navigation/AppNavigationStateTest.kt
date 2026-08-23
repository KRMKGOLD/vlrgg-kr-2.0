package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
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

    @Test
    fun switchingRootsPreservesEachRootStackAndRestoresItsOverlayPath() {
        val rootBackStacks = createRootBackStacks()
        val state = AppNavigationState(
            rootBackStacks = rootBackStacks,
            initialSelectedRoot = MyPageRoot,
        )

        state.push(Search)
        state.push(TeamDetail(teamId = "1001"))
        state.selectRoot(NewsRoot)
        state.push(NewsDetail(articleId = "12345", slug = "grand-final"))

        assertEquals(
            listOf<NavKey>(NewsRoot, NewsDetail(articleId = "12345", slug = "grand-final")),
            state.currentBackStack,
        )
        assertEquals(
            listOf<NavKey>(MyPageRoot, Search, TeamDetail(teamId = "1001")),
            state.backStackFor(MyPageRoot),
        )

        state.selectRoot(MyPageRoot)

        assertEquals(MyPageRoot, state.selectedRoot)
        assertEquals(
            listOf<NavKey>(MyPageRoot, Search, TeamDetail(teamId = "1001")),
            state.currentBackStack,
        )
    }

    @Test
    fun rootSwitchKeepsTheSameBackStackInstancesForEntryScopedState() {
        val rootBackStacks = createRootBackStacks()
        val myPageBackStack = rootBackStacks.getValue(MyPageRoot)
        val newsBackStack = rootBackStacks.getValue(NewsRoot)
        val state = AppNavigationState(
            rootBackStacks = rootBackStacks,
            initialSelectedRoot = MyPageRoot,
        )

        assertSame(myPageBackStack, state.currentBackStack)

        state.selectRoot(NewsRoot)
        assertSame(newsBackStack, state.currentBackStack)

        state.selectRoot(MyPageRoot)
        assertSame(myPageBackStack, state.currentBackStack)
    }

    @Test
    fun poppingAnOverlayOnlyChangesTheSelectedRootStack() {
        val rootBackStacks = createRootBackStacks().apply {
            getValue(NewsRoot).addAll(
                listOf(Search, TeamDetail(teamId = "1001")),
            )
            getValue(MatchesRoot).addAll(
                listOf(Search, MatchDetail(matchId = "2002")),
            )
        }
        val state = AppNavigationState(
            rootBackStacks = rootBackStacks,
            initialSelectedRoot = MatchesRoot,
        )

        assertTrue(state.popOverlay())

        assertEquals(
            listOf<NavKey>(MatchesRoot, Search),
            state.backStackFor(MatchesRoot),
        )
        assertEquals(
            listOf<NavKey>(NewsRoot, Search, TeamDetail(teamId = "1001")),
            state.backStackFor(NewsRoot),
        )
    }

    @Test
    fun restoredStateRejectsAStackWhoseRootDoesNotMatchItsOwner() {
        val rootBackStacks = createRootBackStacks().apply {
            this[NewsRoot] = mutableListOf(MatchesRoot)
        }

        assertFailsWith<IllegalArgumentException> {
            AppNavigationState(
                rootBackStacks = rootBackStacks,
                initialSelectedRoot = NewsRoot,
            )
        }
    }

    private fun createRootBackStacks(): MutableMap<RootNavKey, MutableList<NavKey>> =
        rootNavKeys.associateWithTo(mutableMapOf()) { root -> mutableListOf(root) }
}
