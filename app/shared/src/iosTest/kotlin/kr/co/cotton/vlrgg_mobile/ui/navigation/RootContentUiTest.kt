package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.Modifier
import kr.co.cotton.vlrgg_mobile.ui.component.ROOT_TOP_BAR_TAG
import kr.co.cotton.vlrgg_mobile.ui.component.ROOT_TOP_BAR_TITLE_TAG
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RootContentUiTest {

    @Test
    fun myPageAndAboutRootsUseTheSharedBarAndKeepSearchCallbacks() = runComposeUiTest {
        var destination: AppNavKey by mutableStateOf(MyPageRoot)
        var searchClicks = 0
        setContent {
            VlrTheme {
                RootContent(
                    destination = destination,
                    onSearch = { searchClicks += 1 },
                    modifier = Modifier,
                ) {
                    Text(destination.destinationDescriptor.marker)
                }
            }
        }

        assertSharedBar(title = "My Page")
        onNodeWithContentDescription("검색").performClick()
        assertEquals(1, searchClicks)

        runOnIdle { destination = AboutRoot }

        assertSharedBar(title = "About")
        onNodeWithContentDescription("검색").performClick()
        assertEquals(2, searchClicks)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertSharedBar(title: String) {
        onNodeWithTag(ROOT_TOP_BAR_TAG).assertIsDisplayed()
        onNodeWithTag(ROOT_TOP_BAR_TITLE_TAG).assertIsDisplayed()
        onNodeWithText(title).assertIsDisplayed()
    }
}
