package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RootTopBarUiTest {

    @Test
    fun sharedContentStartsAtTheHostInsetBoundaryAndKeepsAFixedHeight() = runComposeUiTest {
        var expectedContentHeight = 0f
        setContent {
            val density = LocalDensity.current
            SideEffect {
                expectedContentHeight = with(density) { RootTopBarContentHeight.toPx() }
            }
            VlrTheme {
                Box(Modifier.testTag("root-top-bar-host")) {
                    RootTopBar(title = "Events", onSearch = {})
                }
            }
        }

        val barBounds = onNodeWithTag(ROOT_TOP_BAR_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val contentBounds = onNodeWithTag(ROOT_TOP_BAR_CONTENT_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(barBounds.top, contentBounds.top, absoluteTolerance = 1f)
        assertEquals(expectedContentHeight, contentBounds.height, absoluteTolerance = 1f)
    }

    @Test
    fun titleAndSearchActionAreVerticallyCenteredInTheSharedContentRow() = runComposeUiTest {
        setContent {
            VlrTheme {
                RootTopBar(title = "Events", onSearch = {})
            }
        }

        val contentBounds = onNodeWithTag(ROOT_TOP_BAR_CONTENT_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = onNodeWithTag(ROOT_TOP_BAR_TITLE_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val searchBounds = onNodeWithTag(ROOT_TOP_BAR_SEARCH_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertVerticallyCentered(contentBounds.center.y, titleBounds.center.y)
        assertVerticallyCentered(contentBounds.center.y, searchBounds.center.y)
    }

    private fun assertVerticallyCentered(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) <= 1f, "Expected $actual to be vertically centered at $expected")
    }
}
