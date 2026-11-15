package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_info
import vlrggmobile.app.shared.generated.resources.ic_newspaper
import vlrggmobile.app.shared.generated.resources.ic_person
import vlrggmobile.app.shared.generated.resources.ic_scoreboard
import vlrggmobile.app.shared.generated.resources.ic_star
import vlrggmobile.app.shared.generated.resources.ic_trophy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class VlrChromeUiTest {
    @Test
    fun rootHeaderShowsHeadingTitleAndAccessibleSearchAction() = runComposeUiTest {
        var searches = 0
        setContent {
            VlrTheme { RootHeader(title = "경기", onSearch = { searches += 1 }) }
        }

        onNodeWithText("경기").assertIsDisplayed().assert(isHeading())
        onNodeWithContentDescription("검색")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasRole(Role.Button))
            .performClick()
        assertEquals(1, searches)
    }

    @Test
    fun lightDetailHeaderShowsTitleAndInvokesBackAndAction() = runComposeUiTest {
        var backs = 0
        var favorites = 0
        setContent {
            VlrTheme {
                DetailHeader(
                    onBack = { backs += 1 },
                    title = "뉴스",
                    actions = { iconColor ->
                        VlrIconButton(
                            contentDescription = "즐겨찾기 추가",
                            onClick = { favorites += 1 },
                            tint = iconColor,
                            icon = { Icon(vectorResource(Res.drawable.ic_star), contentDescription = null) },
                        )
                    },
                )
            }
        }

        onNodeWithText("뉴스").assertIsDisplayed().assert(isHeading())
        onNodeWithContentDescription("뒤로 가기").assertWidthIsAtLeast(48.dp).performClick()
        onNodeWithContentDescription("즐겨찾기 추가").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, backs)
        assertEquals(1, favorites)
    }

    @Test
    fun darkDetailHeaderNeverShowsTitleButKeepsBack() = runComposeUiTest {
        var backs = 0
        setContent {
            VlrTheme {
                DetailHeader(
                    onBack = { backs += 1 },
                    title = "팀",
                    variant = DetailHeaderVariant.Dark,
                )
            }
        }

        onNodeWithText("팀").assertDoesNotExist()
        onNodeWithContentDescription("뒤로 가기").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun tabsExposeSelectionAndReportClicks() = runComposeUiTest {
        var selected by mutableStateOf("matches")
        setContent {
            VlrTheme {
                VlrTabs {
                    listOf("matches" to "경기", "news" to "뉴스", "stats" to "통계").forEach { (key, label) ->
                        VlrTab(
                            label = label,
                            selected = key == selected,
                            onClick = { selected = key },
                            testTag = "tab-$key",
                        )
                    }
                }
            }
        }

        onNodeWithTag("tab-matches")
            .assertIsSelected()
            .assert(hasRole(Role.Tab))
            .assertHeightIsAtLeast(48.dp)
        onNodeWithTag("tab-news").assertIsNotSelected().performClick()
        onNodeWithTag("tab-news").assertIsSelected()
        onNodeWithTag("tab-matches").assertIsNotSelected()
        onNodeWithText("통계").assertIsDisplayed()
        assertEquals("news", selected)
    }

    @Test
    fun bottomNavigationItemsHaveNamesSelectionAndCallbacks() = runComposeUiTest {
        var selected by mutableStateOf("matches")
        setContent { VlrTheme { BottomNavigationFixture(selected) { selected = it } } }

        bottomNavigationItems.forEach { (key, label) ->
            onNodeWithTag("nav-$key")
                .assertIsDisplayed()
                .assert(hasRole(Role.Tab))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(label)))
                .assertHeightIsAtLeast(48.dp)
        }
        onNodeWithTag("nav-matches").assertIsSelected()
        onNodeWithTag("nav-my-page").assertIsNotSelected().performClick()
        assertEquals("my-page", selected)
        onNodeWithTag("nav-my-page").assertIsSelected()
        onNodeWithTag("nav-matches").assertIsNotSelected()
    }

    @Test
    fun bottomNavigationKeepsOneLineLabelsAtDefaultSizeAndGrowsForLargeFont() = runComposeUiTest {
        var fontScale by mutableStateOf(1f)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VlrTheme {
                    Column(Modifier.width(360.dp)) { BottomNavigationFixture("matches") {} }
                }
            }
        }

        val defaultNews = itemHeight("nav-news")
        assertEquals(defaultNews, itemHeight("nav-my-page"), "All labels fit on one line at 360dp")

        fontScale = 2f
        waitForIdle()
        val largeNews = itemHeight("nav-news")
        val largeMyPage = itemHeight("nav-my-page")
        assertTrue(largeNews > defaultNews, "Large font grows the bar")
        assertTrue(largeMyPage > largeNews, "마이페이지 wraps to a second line instead of being ellipsized")
    }

    @Test
    fun bottomNavigationKeepsOuterItemsInsideSideInsetsInShortLandscape() = runComposeUiTest {
        var selected by mutableStateOf("matches")
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                VlrTheme {
                    Box(
                        modifier = Modifier
                            .size(width = 640.dp, height = 200.dp)
                            .testTag(LANDSCAPE_ROOT_TAG),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        BottomNavigationFixture(
                            selected = selected,
                            onSelect = { selected = it },
                            windowInsets = WindowInsets(left = 48.dp, right = 48.dp, bottom = 16.dp),
                        )
                    }
                }
            }
        }

        val root = onNodeWithTag(LANDSCAPE_ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val first = onNodeWithTag("nav-news").fetchSemanticsNode().boundsInRoot
        val last = onNodeWithTag("nav-about").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left >= root.left + 48f * density - 1f, "First item must clear the start inset")
        assertTrue(last.right <= root.right - 48f * density + 1f, "Last item must clear the end inset")
        assertTrue(last.bottom <= root.bottom - 16f * density + 1f, "Items must clear the bottom inset")
        bottomNavigationItems.forEach { (key, _) ->
            onNodeWithTag("nav-$key").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        onNodeWithTag("nav-about").performClick()
        onNodeWithTag("nav-news").performClick()
        assertEquals("news", selected)
        onNodeWithTag("nav-news").assertIsSelected()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.itemHeight(tag: String): Float =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height

    @androidx.compose.runtime.Composable
    private fun BottomNavigationFixture(
        selected: String,
        windowInsets: WindowInsets = WindowInsets(0.dp),
        onSelect: (String) -> Unit,
    ) {
        val icons = remember {
            mapOf(
                "news" to Res.drawable.ic_newspaper,
                "matches" to Res.drawable.ic_scoreboard,
                "my-page" to Res.drawable.ic_person,
                "events" to Res.drawable.ic_trophy,
                "about" to Res.drawable.ic_info,
            )
        }
        VlrBottomNavigation(windowInsets = windowInsets) {
            bottomNavigationItems.forEach { (key, label) ->
                VlrBottomNavigationItem(
                    label = label,
                    icon = icons.getValue(key),
                    selected = key == selected,
                    onClick = { onSelect(key) },
                    testTag = "nav-$key",
                    wrappedLabel = if (key == "my-page") "마이\n페이지" else null,
                )
            }
        }
    }

    private companion object {
        const val LANDSCAPE_ROOT_TAG = "landscape-root"
        val bottomNavigationItems = listOf(
            "news" to "뉴스",
            "matches" to "경기",
            "my-page" to "마이페이지",
            "events" to "대회",
            "about" to "정보",
        )

        fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    }
}
