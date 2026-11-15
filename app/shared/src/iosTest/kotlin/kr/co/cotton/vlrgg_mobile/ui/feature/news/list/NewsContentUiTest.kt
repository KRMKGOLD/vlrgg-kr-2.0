package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    @Test
    fun firstArticleIsHeadlineAndBylinesUseKoreanDateWithSourceFallback() = runComposeUiTest {
        setNews(NewsListUiState(contentState = NewsListContentState.Content(listOf(lead, dated, undated))))

        assertEquals(20.sp, onNodeWithText(lead.title, useUnmergedTree = true).textStyle().fontSize)
        assertEquals(15.sp, onNodeWithText(dated.title, useUnmergedTree = true).textStyle().fontSize)
        onNodeWithText("ChickenJoe · 2026년 9월 19일", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("jenopelle · 2026년 9월 18일", useUnmergedTree = true).assertIsDisplayed()
        // 서버가 publishedDate를 주지 않으면 원문 그대로다.
        onNodeWithText("VLR.gg · September 17, 2026", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun loadingShowsRowSkeletonsWithoutSpinner() = runComposeUiTest {
        setNews(NewsListUiState())

        onNodeWithText(lead.title).assertDoesNotExist()
        onAllNodes(hasProgressBarRangeInfo()).assertCountEquals(0)
    }

    @Test
    fun loadingMoreShowsSkeletonRowWithBusySemanticsAtTheEnd() = runComposeUiTest {
        setNews(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(lead, dated)),
                isLoadingMore = true,
            ),
        )

        val footer = onNodeWithTag(NEWS_PAGINATION_LOADING_TAG).assertIsDisplayed()
        footer.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        assertTrue(footer.fetchSemanticsNode().children.isEmpty())
        // 스켈레톤 행은 최종 기사 행처럼 전폭이다.
        assertEquals(
            onNodeWithText(dated.title).parentWidth(),
            footer.fetchSemanticsNode().boundsInRoot.width,
        )
        assertTrue(onNodeWithText(dated.title).fetchSemanticsNode().boundsInRoot.bottom <= footer.fetchSemanticsNode().boundsInRoot.top)
        onAllNodes(hasProgressBarRangeInfo()).assertCountEquals(1)
        onNodeWithTag(NEWS_PAGINATION_RETRY_TAG).assertDoesNotExist()
    }

    @Test
    fun paginationErrorKeepsItemsAndShowsInlineRetry() = runComposeUiTest {
        var retries = 0
        setNews(
            NewsListUiState(
                contentState = NewsListContentState.Content(listOf(lead, dated)),
                hasPaginationError = true,
            ),
            onRetryLoadMore = { retries += 1 },
        )

        onNodeWithText(dated.title).assertIsDisplayed()
        onNodeWithText("뉴스 소식을 더 불러오지 못했습니다.").assertIsDisplayed()
        onNodeWithTag(NEWS_PAGINATION_LOADING_TAG).assertDoesNotExist()
        onNodeWithTag(NEWS_PAGINATION_RETRY_TAG).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun emptyListShowsTitleOnlyStatePanel() = runComposeUiTest {
        setNews(NewsListUiState(contentState = NewsListContentState.Empty))

        onNodeWithText("표시할 뉴스가 없어요.").assertIsDisplayed()
        onNodeWithText("재시도").assertDoesNotExist()
    }

    @Test
    fun initialErrorShowsStatePanelRetry() = runComposeUiTest {
        var retries = 0
        setNews(NewsListUiState(contentState = NewsListContentState.Error), onRetryInitial = { retries += 1 })

        onNodeWithText("뉴스를 불러오지 못했습니다.").assertIsDisplayed()
        onNodeWithText("네트워크 상태를 확인하고 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithTag(NEWS_INITIAL_RETRY_TAG).assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }

    private fun ComposeUiTest.setNews(
        uiState: NewsListUiState,
        onRetryInitial: () -> Unit = {},
        onRetryLoadMore: () -> Unit = {},
    ) {
        setContent {
            VlrTheme {
                NewsContent(
                    uiState = uiState,
                    listState = rememberLazyListState(),
                    onSearch = {},
                    onNewsClick = { _, _ -> },
                    onRefresh = {},
                    onRetryInitial = onRetryInitial,
                    onLoadMore = {},
                    onRetryLoadMore = onRetryLoadMore,
                    modifier = Modifier.size(360.dp, 780.dp),
                )
            }
        }
    }

    private fun SemanticsNodeInteraction.parentWidth(): Float =
        fetchSemanticsNode().parent!!.boundsInRoot.width

    private fun hasProgressBarRangeInfo() = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style
    }

    private companion object {
        val lead = NewsSummary(
            articleId = "1",
            slug = "coach-joe",
            title = "Coach Joe: \"[Shopify] has been the team to beat in [Game Changers].\"",
            author = "ChickenJoe",
            publishedAt = "September 19, 2026",
            publishedDate = LocalDate(2026, 9, 19),
        )
        val dated = NewsSummary(
            articleId = "2",
            slug = "purp0",
            title = "purp0 searching for new team following Team Liquid benching",
            author = "jenopelle",
            publishedAt = "September 18, 2026",
            publishedDate = LocalDate(2026, 9, 18),
        )
        val undated = NewsSummary(
            articleId = "3",
            slug = "envy-nightz",
            title = "ENVY allows nightz to explore options",
            author = "VLR.gg",
            publishedAt = "September 17, 2026",
        )

        val news = NewsSummary(
            articleId = "news-1",
            slug = "news-1",
            title = "News",
            author = null,
            publishedAt = "Today",
        )
    }
}
