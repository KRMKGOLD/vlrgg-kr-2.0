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
    fun freshStateStartsAtMyPageAndCreatesOneEmptyStackForEveryRoot() {
        val state = AppNavigationState(createRootBackStacks())

        assertEquals(MyPageRoot, state.selectedRoot)
        assertTrue(state.overlay.isEmpty())
        rootNavKeys.forEach { root ->
            assertEquals(listOf<NavKey>(root), state.backStackFor(root))
        }
    }

    @Test
    fun searchIsPushedAboveTheSelectedRootWithoutChangingSelection() {
        rootNavKeys.forEach { root ->
            val state = AppNavigationState(createRootBackStacks(), initialSelectedRoot = root)

            state.push(Search)

            assertEquals(root, state.selectedRoot)
            assertEquals(listOf<NavKey>(root, Search), state.currentBackStack)
        }
    }

    @Test
    fun backPopsDetailThenSearchFromTheInitiatingRoot() {
        val state = AppNavigationState(createRootBackStacks())
        state.push(Search)
        state.push(TeamDetail(teamId = "1"))

        assertTrue(state.popOverlay())
        assertEquals(listOf<NavKey>(MyPageRoot, Search), state.currentBackStack)
        assertTrue(state.popOverlay())
        assertEquals(listOf<NavKey>(MyPageRoot), state.currentBackStack)
        assertFalse(state.popOverlay())
    }

    @Test
    fun switchingRootsPreservesEachRootStackAndRestoresItsOverlayPath() {
        val state = AppNavigationState(createRootBackStacks())
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
        assertTrue(state.popOverlay())
        assertEquals(listOf<NavKey>(MyPageRoot, Search), state.currentBackStack)
    }

    @Test
    fun reselectingTheCurrentRootClearsOnlyItsOverlay() {
        val state = AppNavigationState(createRootBackStacks())
        state.push(Search)
        state.selectRoot(NewsRoot)
        state.push(NewsDetail(articleId = "12345", slug = "grand-final"))

        state.selectRoot(NewsRoot)

        assertEquals(listOf<NavKey>(NewsRoot), state.currentBackStack)
        assertEquals(listOf<NavKey>(MyPageRoot, Search), state.backStackFor(MyPageRoot))
    }

    @Test
    fun rootSwitchKeepsTheSameBackStackInstancesForEntryScopedState() {
        val rootBackStacks = createRootBackStacks()
        val myPageBackStack = rootBackStacks.getValue(MyPageRoot)
        val newsBackStack = rootBackStacks.getValue(NewsRoot)
        val state = AppNavigationState(rootBackStacks)

        assertSame(myPageBackStack, state.currentBackStack)

        state.selectRoot(NewsRoot)
        assertSame(newsBackStack, state.currentBackStack)

        state.selectRoot(MyPageRoot)
        assertSame(myPageBackStack, state.currentBackStack)
    }

    @Test
    fun poppingAnOverlayOnlyChangesTheSelectedRootStack() {
        val rootBackStacks = createRootBackStacks().apply {
            getValue(NewsRoot).addAll(listOf(Search, TeamDetail(teamId = "1001")))
            getValue(MatchesRoot).addAll(listOf(Search, MatchDetail(matchId = "2002")))
        }
        val state = AppNavigationState(rootBackStacks, initialSelectedRoot = MatchesRoot)

        assertTrue(state.popOverlay())

        assertEquals(listOf<NavKey>(MatchesRoot, Search), state.backStackFor(MatchesRoot))
        assertEquals(
            listOf<NavKey>(NewsRoot, Search, TeamDetail(teamId = "1001")),
            state.backStackFor(NewsRoot),
        )
    }

    @Test
    fun rootsCannotEnterAnOverlay() {
        val state = AppNavigationState(createRootBackStacks())

        rootNavKeys.forEach { root ->
            assertFailsWith<IllegalArgumentException> { state.push(root) }
        }
    }

    @Test
    fun restoredStateRejectsMissingExtraAndMismatchedRootStacks() {
        assertFailsWith<IllegalArgumentException> {
            AppNavigationState(createRootBackStacks().minus(NewsRoot))
        }
        assertFailsWith<IllegalArgumentException> {
            AppNavigationState(createRootBackStacks().apply {
                this[NewsRoot] = mutableListOf(MatchesRoot)
            })
        }
        assertFailsWith<IllegalArgumentException> {
            AppNavigationState(createRootBackStacks().apply {
                getValue(NewsRoot) += MatchesRoot
            })
        }
    }

    @Test
    fun rootSavedStateIdsAreStableAndRestoreEachRoot() {
        val expectedIds: Map<RootNavKey, String> = mapOf(
            NewsRoot to "news",
            MatchesRoot to "matches",
            MyPageRoot to "my-page",
            EventsRoot to "events",
            AboutRoot to "about",
        )

        expectedIds.forEach { (root, expectedId) ->
            assertEquals(expectedId, root.savedStateId())
            assertEquals(root, rootNavKeyFromSavedStateId(root.savedStateId()))
        }
    }

    @Test
    fun rootSavedStateIdsDoNotDependOnRootNavigationOrder() {
        val expectedIds: Map<RootNavKey, String> = mapOf(
            NewsRoot to "news",
            MatchesRoot to "matches",
            MyPageRoot to "my-page",
            EventsRoot to "events",
            AboutRoot to "about",
        )

        assertEquals(expectedIds, rootNavKeys.reversed().associateWith(RootNavKey::savedStateId))
    }

    @Test
    fun unknownRootSavedStateIdRestoresAsNull() {
        assertEquals(null, rootNavKeyFromSavedStateId("unknown-root"))
    }

    private fun createRootBackStacks(): MutableMap<RootNavKey, MutableList<NavKey>> =
        rootNavKeys.associateWithTo(mutableMapOf()) { root -> mutableListOf(root) }
}
