package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components.NewsListItem
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components.NewsSkeleton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_search

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

    if (contentState is NewsListContentState.Content) {
        LoadMoreEffect(
            listState = listState,
            itemCount = contentState.items.size,
            onLoadMore = onLoadMore,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = {
            NewsTopBar(onSearch = onSearch)
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
                        NewsStateMessage(
                            message = "표시할 뉴스가 없어요.",
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    NewsListContentState.Error -> item(key = "error") {
                        NewsStateMessage(
                            message = "뉴스를 불러오지 못했습니다.\n네트워크 상태를 확인하고 다시 시도해 주세요.",
                            actionText = "재시도",
                            onAction = onRetryInitial,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    is NewsListContentState.Content -> {
                        items(
                            items = contentState.items,
                            key = { news -> "${news.articleId}/${news.slug}" },
                        ) { news ->
                            NewsListItem(
                                news = news,
                                onClick = {
                                    onNewsClick(news.articleId, news.slug)
                                },
                            )
                            HorizontalDivider(
                                thickness = VlrDimensions.OutlineWidth,
                                color = VlrTheme.colors.outline,
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
private fun NewsTopBar(
    onSearch: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "News",
                style = VlrTheme.typography.pageTitle,
                color = VlrTheme.colors.textPrimary,
            )
        },
        actions = {
            VlrIconButton(
                contentDescription = "검색",
                onClick = onSearch,
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_search),
                        contentDescription = null,
                    )
                },
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = VlrTheme.colors.surface,
        ),
    )
}

@Composable
private fun NewsStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(VlrDimensions.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        actionText?.let { text ->
            VlrButton(
                text = text,
                onClick = onAction,
                modifier = Modifier.padding(top = VlrDimensions.Space4),
            )
        }
    }
}

@Composable
private fun NewsPaginationFooter(
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(VlrDimensions.Space4),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = VlrTheme.colors.actionPrimary,
                strokeWidth = 2.dp,
            )

            hasError -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            ) {
                Text(
                    text = "뉴스 소식을 더 불러오지 못했습니다.",
                    style = VlrTheme.typography.body,
                    color = VlrTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                VlrButton(
                    text = "재시도",
                    onClick = onRetry,
                    variant = VlrButtonVariant.Text,
                )
            }
        }
    }
}

@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(listState, itemCount) {
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
    NewsSummary(
        articleId = "101",
        slug = "champions-run",
        title = "Champions 서울, 새로운 왕좌를 향한 여정",
        author = "글로벌 뉴스",
        publishedAt = "2시간 전",
    ),
    NewsSummary(
        articleId = "102",
        slug = "masters-preview",
        title = "Masters 플레이오프 대진과 주목할 경기",
        author = "VLR 편집팀",
        publishedAt = "5시간 전",
    ),
    NewsSummary(
        articleId = "103",
        slug = "roster-update",
        title = "주요 팀 로스터 업데이트 정리",
        author = "경기 분석팀",
        publishedAt = "어제",
    ),
    NewsSummary(
        articleId = "104",
        slug = "regional-standings",
        title = "지역별 리그 순위 경쟁이 본격화된다",
        author = "글로벌 뉴스",
        publishedAt = "2일 전",
    ),
    NewsSummary(
        articleId = "105",
        slug = "patch-analysis",
        title = "새 패치가 프로 경기 메타에 미칠 영향",
        author = "전략 분석팀",
        publishedAt = "3일 전",
    ),
)
