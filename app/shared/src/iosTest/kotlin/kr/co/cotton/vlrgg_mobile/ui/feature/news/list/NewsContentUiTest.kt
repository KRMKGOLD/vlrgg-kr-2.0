package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.ZERO

@OptIn(ExperimentalTestApi::class)
class NewsContentUiTest {
    @Test
    fun visibleBusyDialogStopsAutomaticPagination() = runComposeUiTest {
        var loadMoreRequests = 0
        val state = mutableStateOf(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(news)),
                busyRetry = BusyRetryState.create("news:page:2", ZERO),
            ),
        )
        setContent {
            VlrTheme {
                NewsContent(
                    uiState = state.value,
                    listState = rememberLazyListState(),
                    onSearch = {},
                    onNewsClick = { _, _ -> },
                    onRefresh = {},
                    onRetryInitial = {},
                    onLoadMore = { loadMoreRequests += 1 },
                    onRetryLoadMore = {},
                )
            }
        }

        waitForIdle()
        assertEquals(0, loadMoreRequests)

        state.value = state.value.copy(busyRetry = state.value.busyRetry?.dismiss())
        waitUntil(timeoutMillis = 5_000) { loadMoreRequests > 0 }
        assertEquals(1, loadMoreRequests)
    }

    private companion object {
        val news = NewsSummary(
            articleId = "news-1",
            slug = "news-1",
            title = "News",
            author = null,
            publishedAt = "Today",
        )
    }
}
