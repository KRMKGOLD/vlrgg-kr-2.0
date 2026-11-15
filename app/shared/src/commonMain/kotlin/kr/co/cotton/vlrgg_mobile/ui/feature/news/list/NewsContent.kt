package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.component.InlineRetry
import kr.co.cotton.vlrgg_mobile.ui.component.RootHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonRow
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components.NewsListItem
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components.NewsSkeleton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_newspaper
import androidx.compose.foundation.lazy.itemsIndexed

internal const val NEWS_INITIAL_RETRY_TAG = "news-initial-retry"
internal const val NEWS_PAGINATION_RETRY_TAG = "news-pagination-retry"
internal const val NEWS_PAGINATION_LOADING_TAG = "news-pagination-loading"

@Composable
fun NewsContent(
    uiState: NewsListUiState,
    listState: LazyListState,
    onSearch: () -> Unit,
    onNewsClick: (articleId: String, slug: String) -> Unit,
    onRefresh: () -> Unit,
    onRetryInitial: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentState = uiState.contentState
    val isBusyCooldownActive = rememberBusyRetryCooldown(uiState.busyRetry)

    if (contentState is NewsListContentState.Content) {
        LoadMoreEffect(
            listState = listState,
            itemCount = contentState.items.size,
            enabled = !uiState.isRefreshing && !uiState.isLoadingMore && !uiState.hasPaginationError &&
                uiState.busyRetry?.isDialogVisible != true,
            onLoadMore = onLoadMore,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = {
            RootHeader(title = "뉴스", onSearch = onSearch)
        },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            enabled = contentState is NewsListContentState.Content ||
                contentState is NewsListContentState.Empty,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (contentState) {
                    NewsListContentState.Loading -> item(key = "loading") {
                        NewsSkeleton()
                    }

                    NewsListContentState.Empty -> item(key = "empty") {
                        StatePanel(
                            title = "표시할 뉴스가 없어요.",
                            icon = Res.drawable.ic_newspaper,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    NewsListContentState.Error -> item(key = "error") {
                        if (uiState.busyRetry?.isDialogVisible == true || isBusyCooldownActive) {
                            Box(Modifier.fillParentMaxSize())
                        } else {
                            StatePanel(
                                title = "뉴스를 불러오지 못했습니다.",
                                body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                                actionLabel = "재시도",
                                onAction = onRetryInitial,
                                actionTestTag = NEWS_INITIAL_RETRY_TAG,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }

                    is NewsListContentState.Content -> {
                        itemsIndexed(
                            items = contentState.items,
                            key = { _, news -> "${news.articleId}/${news.slug}" },
                        ) { index, news ->
                            NewsListItem(
                                news = news,
                                onClick = {
                                    onNewsClick(news.articleId, news.slug)
                                },
                                headline = index == 0,
                            )
                        }

                        if (uiState.isLoadingMore || uiState.hasPaginationError) {
                            item(key = "pagination-footer") {
                                NewsPaginationFooter(
                                    isLoading = uiState.isLoadingMore,
                                    hasError = uiState.hasPaginationError,
                                    onRetry = onRetryLoadMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsPaginationFooter(
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
) {
    if (!isLoading && hasError) {
        InlineRetry(
            message = "뉴스 소식을 더 불러오지 못했습니다.",
            onRetry = onRetry,
            actionTestTag = NEWS_PAGINATION_RETRY_TAG,
        )
        return
    }
    if (isLoading) {
        // 다음 기사 행 자리를 스켈레톤으로 채운다(`DESIGN.md` 6·9절). 진행 상태는 보조 기술에 알린다.
        SkeletonRow(
            modifier = Modifier
                .testTag(NEWS_PAGINATION_LOADING_TAG)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        )
    }
}

@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    itemCount: Int,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(listState, itemCount, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (
                    lastVisibleIndex != null &&
                    lastVisibleIndex >= itemCount - 3
                ) {
                    onLoadMore()
                }
            }
    }
}

@Preview(
    name = "Loading",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsContentLoadingPreview() {
    NewsContentPreview(uiState = NewsListUiState())
}

@Preview(
    name = "Empty",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsContentEmptyPreview() {
    NewsContentPreview(
        uiState = NewsListUiState(
            contentState = NewsListContentState.Empty,
        ),
    )
}

@Preview(
    name = "Initial error",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsContentErrorPreview() {
    NewsContentPreview(
        uiState = NewsListUiState(
            contentState = NewsListContentState.Error,
        ),
    )
}

@Preview(
    name = "Content",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsContentPopulatedPreview() {
    NewsContentPreview(
        uiState = NewsListUiState(
            contentState = NewsListContentState.Content(previewNewsItems),
        ),
    )
}

@Preview(
    name = "Loading more",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsContentLoadingMorePreview() {
    NewsContentPreview(
        uiState = NewsListUiState(
            contentState = NewsListContentState.Content(previewNewsItems),
            isLoadingMore = true,
        ),
    )
}

@Preview(
    name = "Pagination error",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsContentPaginationErrorPreview() {
    NewsContentPreview(
        uiState = NewsListUiState(
            contentState = NewsListContentState.Content(previewNewsItems),
            hasPaginationError = true,
        ),
    )
}

@Composable
private fun NewsContentPreview(
    uiState: NewsListUiState,
) {
    VlrTheme {
        NewsContent(
            uiState = uiState,
            listState = rememberLazyListState(),
            onSearch = {},
            onNewsClick = { _, _ -> },
            onRefresh = {},
            onRetryInitial = {},
            onLoadMore = {},
            onRetryLoadMore = {},
        )
    }
}

private val previewNewsItems = listOf(
    previewNews("101", "coach-joe", "Coach Joe: \"[Shopify] has been the team to beat in [Game Changers].\"", "ChickenJoe", 19),
    previewNews("102", "purp0-free-agent", "purp0 searching for new team following Team Liquid benching", "jenopelle", 19),
    previewNews("103", "cyvoph-free-agent", "Natus Vincere's CyvOph set to enter free agency", "jenopelle", 19),
    previewNews("104", "warbirds-rex-regum", "Warbirds set to leave the Rex Regum Qeon flock", "VLR.gg", 18),
    previewNews("105", "envy-nightz", "ENVY allows nightz to explore options", "VLR.gg", 17),
)

private fun previewNews(articleId: String, slug: String, title: String, author: String, day: Int) = NewsSummary(
    articleId = articleId,
    slug = slug,
    title = title,
    author = author,
    publishedAt = "September $day, 2026",
    publishedDate = LocalDate(2026, 9, day),
)
