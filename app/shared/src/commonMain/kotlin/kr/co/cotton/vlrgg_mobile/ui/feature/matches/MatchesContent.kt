package kr.co.cotton.vlrgg_mobile.ui.feature.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.MatchCard
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.MatchesSkeleton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_search

internal const val MATCHES_LOADING_TAG = "matches-loading"
internal const val MATCHES_REFRESHING_TAG = "matches-refreshing"
internal const val MATCHES_INITIAL_RETRY_TAG = "matches-initial-retry"
internal const val MATCHES_PAGINATION_LOADING_TAG = "matches-pagination-loading"
internal const val MATCHES_PAGINATION_RETRY_TAG = "matches-pagination-retry"

internal fun matchCardTag(matchId: String): String = "match-card-$matchId"

@Composable
fun MatchesContent(
    uiState: MatchesUiState,
    upcomingLiveListState: LazyListState,
    resultsListState: LazyListState,
    onSearch: () -> Unit,
    onSelectTab: (MatchesTab) -> Unit,
    onMatchClick: (matchId: String) -> Unit,
    onRefresh: () -> Unit,
    onRetryInitial: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedState = when (uiState.selectedTab) {
        MatchesTab.UPCOMING_LIVE -> uiState.upcomingLive
        MatchesTab.RESULTS -> uiState.results
    }
    val listState = when (uiState.selectedTab) {
        MatchesTab.UPCOMING_LIVE -> upcomingLiveListState
        MatchesTab.RESULTS -> resultsListState
    }

    val content = feedState.contentState
    if (content is MatchesFeedContentState.Content) {
        MatchesLoadMoreEffect(
            listState = listState,
            enabled = !feedState.isRefreshing &&
                !feedState.isLoadingMore &&
                !feedState.hasPaginationError,
            onLoadMore = onLoadMore,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = {
            Column {
                MatchesTopBar(onSearch = onSearch)
                MatchesTabs(
                    selectedTab = uiState.selectedTab,
                    onSelectTab = onSelectTab,
                )
            }
        },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = feedState.isRefreshing,
            onRefresh = onRefresh,
            enabled = content is MatchesFeedContentState.Content ||
                content is MatchesFeedContentState.Empty,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .then(
                    if (feedState.isRefreshing) {
                        Modifier
                            .testTag(MATCHES_REFRESHING_TAG)
                            .semantics { stateDescription = "새로고침 중" }
                    } else {
                        Modifier
                    },
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (content) {
                    MatchesFeedContentState.Loading -> item(key = "loading") {
                        MatchesSkeleton(Modifier.testTag(MATCHES_LOADING_TAG))
                    }

                    MatchesFeedContentState.Empty -> item(key = "empty") {
                        MatchesStateMessage(
                            message = uiState.selectedTab.emptyMessage(),
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    MatchesFeedContentState.Error -> item(key = "error") {
                        MatchesStateMessage(
                            message = "경기 목록을 불러오지 못했습니다.\n네트워크 상태를 확인하고 다시 시도해 주세요.",
                            actionText = "재시도",
                            actionTag = MATCHES_INITIAL_RETRY_TAG,
                            onAction = onRetryInitial,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    is MatchesFeedContentState.Content -> {
                        content.groups.forEach { group ->
                            matchGroup(
                                group = group,
                                onMatchClick = onMatchClick,
                            )
                        }
                        if (feedState.isLoadingMore || feedState.hasPaginationError) {
                            item(key = "pagination-footer") {
                                MatchesPaginationFooter(
                                    isLoading = feedState.isLoadingMore,
                                    hasError = feedState.hasPaginationError,
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

private fun androidx.compose.foundation.lazy.LazyListScope.matchGroup(
    group: MatchDateGroup,
    onMatchClick: (String) -> Unit,
) {
    item(key = "date-${group.dateLabel}") {
        Text(
            text = group.dateLabel,
            modifier = Modifier.padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = VlrDimensions.Space4,
                bottom = VlrDimensions.Space2,
            ),
            style = VlrTheme.typography.label,
            color = VlrTheme.colors.textSecondary,
        )
    }
    items(
        items = group.matches,
        key = { match -> match.id },
    ) { match ->
        MatchCard(
            match = match,
            onClick = { onMatchClick(match.id) },
            modifier = Modifier.padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space1,
            ),
        )
    }
}

@Composable
private fun MatchesTopBar(onSearch: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "Matches",
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
private fun MatchesTabs(
    selectedTab: MatchesTab,
    onSelectTab: (MatchesTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VlrDimensions.MinimumTouchTarget),
    ) {
        MatchesTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) VlrTheme.colors.surfaceSelected else Color.Transparent)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelectTab(tab) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label(),
                    style = VlrTheme.typography.label,
                    color = if (selected) {
                        VlrTheme.colors.actionPrimary
                    } else {
                        VlrTheme.colors.textSecondary
                    },
                )
            }
        }
    }
    HorizontalDivider(
        thickness = VlrDimensions.OutlineWidth,
        color = VlrTheme.colors.outline,
    )
}

@Composable
private fun MatchesStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    actionTag: String? = null,
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
                modifier = Modifier
                    .padding(top = VlrDimensions.Space4)
                    .then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier),
            )
        }
    }
}

@Composable
private fun MatchesPaginationFooter(
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
                modifier = Modifier
                    .size(24.dp)
                    .testTag(MATCHES_PAGINATION_LOADING_TAG),
                color = VlrTheme.colors.actionPrimary,
                strokeWidth = 2.dp,
            )

            hasError -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            ) {
                Text(
                    text = "경기를 더 불러오지 못했습니다.",
                    style = VlrTheme.typography.body,
                    color = VlrTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                VlrButton(
                    text = "재시도",
                    onClick = onRetry,
                    variant = VlrButtonVariant.Text,
                    modifier = Modifier.testTag(MATCHES_PAGINATION_RETRY_TAG),
                )
            }
        }
    }
}

@Composable
private fun MatchesLoadMoreEffect(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            layoutInfo.totalItemsCount to layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .distinctUntilChanged()
            .collect { (totalItemsCount, lastVisibleIndex) ->
                if (
                    totalItemsCount > 0 &&
                    lastVisibleIndex != null &&
                    lastVisibleIndex >= totalItemsCount - 3
                ) {
                    onLoadMore()
                }
            }
    }
}

private fun MatchesTab.label(): String = when (this) {
    MatchesTab.UPCOMING_LIVE -> "예정 · 라이브"
    MatchesTab.RESULTS -> "결과"
}

private fun MatchesTab.emptyMessage(): String = when (this) {
    MatchesTab.UPCOMING_LIVE -> "표시할 예정 또는 라이브 경기가 없어요."
    MatchesTab.RESULTS -> "표시할 경기 결과가 없어요."
}
