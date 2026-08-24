package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kr.co.cotton.vlrgg_mobile.ui.component.ROOT_TOP_BAR_TAG
import kr.co.cotton.vlrgg_mobile.ui.component.ROOT_TOP_BAR_TITLE_TAG
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NewsContentUiTest {

    @Test
    fun loadingUsesTheSharedRootBarAndPreservesSearchCallback() = runComposeUiTest {
        var searchClicks = 0
        setContent {
            VlrTheme {
                NewsContent(
                    uiState = NewsListUiState(),
                    listState = rememberLazyListState(),
                    onSearch = { searchClicks += 1 },
                    onNewsClick = { _, _ -> },
                    onRefresh = {},
                    onRetryInitial = {},
                    onLoadMore = {},
                    onRetryLoadMore = {},
                )
            }
        }

        onNodeWithTag(ROOT_TOP_BAR_TAG).assertIsDisplayed()
        onNodeWithTag(ROOT_TOP_BAR_TITLE_TAG).assertIsDisplayed()
        onNodeWithText("News").assertIsDisplayed()
        onNodeWithContentDescription("검색").performClick()
        assertEquals(1, searchClicks)
    }
}
