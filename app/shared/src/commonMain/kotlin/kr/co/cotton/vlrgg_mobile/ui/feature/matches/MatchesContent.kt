package kr.co.cotton.vlrgg_mobile.ui.feature.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.distinctUntilChanged
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup
import kr.co.cotton.vlrgg_mobile.ui.component.InlineRetry
import kr.co.cotton.vlrgg_mobile.ui.component.RootHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonMatchRow
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.VlrTab
import kr.co.cotton.vlrgg_mobile.ui.component.VlrTabs
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.MatchCard
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.MatchesSkeleton
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_scoreboard

internal const val MATCHES_LOADING_TAG = "matches-loading"
internal const val MATCHES_REFRESHING_TAG = "matches-refreshing"
internal const val MATCHES_INITIAL_RETRY_TAG = "matches-initial-retry"
internal const val MATCHES_PAGINATION_LOADING_TAG = "matches-pagination-loading"
internal const val MATCHES_PAGINATION_RETRY_TAG = "matches-pagination-retry"
internal const val MATCHES_TABS_TAG = "matches-tabs"

internal fun matchCardTag(matchId: String): String = "match-card-$matchId"
internal fun matchTabTag(tab: MatchesTab): String = "matches-tab-${tab.name.lowercase()}"

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
    val isBusyCooldownActive = rememberBusyRetryCooldown(uiState.busyRetry)
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
                !feedState.hasPaginationError &&
                uiState.busyRetry?.isDialogVisible != true,
            onLoadMore = onLoadMore,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = {
            Column {
                RootHeader(title = "경기", onSearch = onSearch)
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
                        StatePanel(
                            title = uiState.selectedTab.emptyMessage(),
                            icon = Res.drawable.ic_scoreboard,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    MatchesFeedContentState.Error -> item(key = "error") {
                        if (uiState.busyRetry?.isDialogVisible == true || isBusyCooldownActive) {
                            Box(Modifier.fillParentMaxSize())
                        } else {
                            StatePanel(
                                title = "경기 목록을 불러오지 못했습니다.",
                                body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                                actionLabel = "재시도",
                                actionTestTag = MATCHES_INITIAL_RETRY_TAG,
                                onAction = onRetryInitial,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }

                    is MatchesFeedContentState.Content -> {
                        content.groups.forEachIndexed { index, group ->
                            matchGroup(
                                group = group,
                                isFirst = index == 0,
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

/** 구조화한 날짜가 있으면 한국어로 표시하고, 없으면 source label을 그대로 쓴다. */
internal fun MatchDateGroup.displayDateLabel(): String = date?.let(KoreanDateTimeFormat::monthDay) ?: dateLabel

/** 목록의 시각은 vlr.gg가 서울 요청에 렌더한 한국 시간이다(기능 문서 날짜·시간 계약). */
internal const val MATCHES_TIME_ZONE_LABEL = "한국 시간"

/** 날짜 그룹은 흰 섹션이며, 그룹 사이는 8dp `canvas`로 띄운다(`.pen` 20-1). */
private fun androidx.compose.foundation.lazy.LazyListScope.matchGroup(
    group: MatchDateGroup,
    isFirst: Boolean,
    onMatchClick: (String) -> Unit,
) {
    if (!isFirst) {
        item(key = "gap-${group.dateLabel}") {
            Spacer(Modifier.fillMaxWidth().height(VlrDimensions.Space2).background(VlrTheme.colors.canvas))
        }
    }
    item(key = "date-${group.dateLabel}") {
        SectionHeader(title = group.displayDateLabel(), meta = MATCHES_TIME_ZONE_LABEL)
    }
    items(
        items = group.matches,
        key = { match -> match.id },
    ) { match ->
        MatchCard(
            match = match,
            onClick = { onMatchClick(match.id) },
        )
    }
}

@Composable
private fun MatchesTabs(
    selectedTab: MatchesTab,
    onSelectTab: (MatchesTab) -> Unit,
) {
    VlrTabs(modifier = Modifier.testTag(MATCHES_TABS_TAG)) {
        MatchesTab.entries.forEach { tab ->
            VlrTab(
                label = tab.label(),
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                testTag = matchTabTag(tab),
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
    if (!isLoading && hasError) {
        InlineRetry(
            message = "경기를 더 불러오지 못했습니다.",
            onRetry = onRetry,
            actionTestTag = MATCHES_PAGINATION_RETRY_TAG,
        )
        return
    }
    if (isLoading) {
        // 다음 경기 행 자리를 스켈레톤으로 채운다(`DESIGN.md` 6·9절). 진행 상태는 보조 기술에 알린다.
        SkeletonMatchRow(
            modifier = Modifier
                .testTag(MATCHES_PAGINATION_LOADING_TAG)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        )
    }
}

@Composable
private fun MatchesLoadMoreEffect(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val latestOnLoadMore = rememberUpdatedState(onLoadMore)

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
                    latestOnLoadMore.value()
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
