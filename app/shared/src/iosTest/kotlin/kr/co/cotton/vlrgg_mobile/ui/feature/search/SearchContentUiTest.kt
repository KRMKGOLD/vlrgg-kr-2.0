package kr.co.cotton.vlrgg_mobile.ui.feature.search

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SearchContentUiTest {
    @Test
    fun initialGuidesInputAndDisablesInvalidVisibleSubmit() = runComposeUiTest {
        setContent { SearchContentFixture(SearchUiState()) }

        onNodeWithText("검색어를 입력해 주세요").assertExists()
        onNodeWithContentDescription("검색어").assertIsFocused()
        onNodeWithContentDescription("검색").assertIsNotEnabled()
        onNodeWithContentDescription("검색 화면 닫기").assertExists()
    }

    @Test
    fun loadingKeepsQueryAndShowsProgressCopy() = runComposeUiTest {
        setContent {
            SearchContentFixture(
                SearchUiState(query = "T1", contentState = SearchContentState.Loading),
            )
        }

        onNodeWithText("T1 검색 중…").assertExists()
        onNodeWithTag(SEARCH_LOADING_TAG).assertExists()
    }

    @Test
    fun populatedRendersSeriesRowWithTypeAndMetadata() = runComposeUiTest {
        setContent {
            SearchContentFixture(
                SearchUiState(contentState = SearchContentState.Populated(items)),
            )
        }

        onNodeWithText("Series").assertExists()
        onNodeWithText("Group Stage").assertExists()
    }

    @Test
    fun emptyShowsItsOwnGuidance() = runComposeUiTest {
        setContent {
            SearchContentFixture(
                SearchUiState(query = "unknown", contentState = SearchContentState.Empty),
            )
        }
        onNodeWithText("unknown에 대한 검색 결과가 없어요.").assertExists()
    }

    @Test
    fun errorShowsRetryAction() = runComposeUiTest {
        setContent {
            SearchContentFixture(
                SearchUiState(query = "T1", contentState = SearchContentState.Error),
            )
        }
        onNodeWithText("검색 결과를 불러오지 못했습니다.").assertExists()
        onNodeWithTag(SEARCH_RETRY_TAG).assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun SearchContentFixture(
        uiState: SearchUiState,
        onResultClick: (SearchResult) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        VlrTheme {
            SearchContent(
                uiState = uiState,
                onQueryChange = {},
                onSubmit = {},
                onRetry = onRetry,
                onBack = {},
                onResultClick = onResultClick,
            )
        }
    }

    private companion object {
        val items = listOf(
            SeriesSearchResult("series-1", "T1 vs GEN", "Group Stage"),
            EventSearchResult("event-2", "VCT Pacific", "06.15–07.21"),
            TeamSearchResult("team-3", "T1", "Pacific"),
            PlayerSearchResult("player-4", "T1 Sayaplayer", "Ha Jung-woo"),
        )
    }
}
