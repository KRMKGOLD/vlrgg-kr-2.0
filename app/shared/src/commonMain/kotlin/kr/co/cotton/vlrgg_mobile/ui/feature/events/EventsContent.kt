package kr.co.cotton.vlrgg_mobile.ui.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.ui.component.RootHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonRow
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_trophy
import kr.co.cotton.vlrgg_mobile.ui.component.IdentityRow
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind

internal const val EVENTS_LOADING_TAG = "events-loading"
internal const val EVENTS_REFRESHING_TAG = "events-refreshing"
internal const val EVENTS_INITIAL_RETRY_TAG = "events-initial-retry"

internal fun eventRowTag(eventId: String): String = "event-row-$eventId"

@Composable
fun EventsContent(
    uiState: EventsUiState,
    listState: LazyListState,
    onSearch: () -> Unit,
    onEventClick: (eventId: String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusyCooldownActive = rememberBusyRetryCooldown(uiState.busyRetry)
    val contentState = uiState.contentState
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { RootHeader(title = "대회", onSearch = onSearch) },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            enabled = contentState is EventsContentState.Content || contentState is EventsContentState.Empty,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .then(
                    if (uiState.isRefreshing) {
                        Modifier
                            .testTag(EVENTS_REFRESHING_TAG)
                            .semantics { stateDescription = "새로고침 중" }
                    } else {
                        Modifier
                    },
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(VlrTheme.colors.canvas),
            ) {
                when (contentState) {
                    EventsContentState.Loading -> item(key = "skeleton") { EventsSkeleton() }
                    EventsContentState.Empty -> item(key = "empty") {
                        EventsEmptyState(modifier = Modifier.fillParentMaxSize())
                    }

                    EventsContentState.Error -> item(key = "error") {
                        if (uiState.busyRetry?.isDialogVisible == true || isBusyCooldownActive) {
                            Box(Modifier.fillParentMaxSize().background(VlrTheme.colors.surface))
                        } else {
                            EventsErrorState(
                                onRetry = onRetry,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }

                    is EventsContentState.Content -> {
                        eventGroup(
                            key = "ongoing",
                            title = "진행 중",
                            events = contentState.events.ongoing,
                            isFirst = true,
                            onEventClick = onEventClick,
                        )
                        eventGroup(
                            key = "upcoming",
                            title = "예정",
                            events = contentState.events.upcoming,
                            onEventClick = onEventClick,
                        )
                        eventGroup(
                            key = "completed-paused",
                            title = "종료 · 중단",
                            events = contentState.events.completedOrPaused,
                            onEventClick = onEventClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Status group (`.pen` 40-1): a white section whose header shows the count of visible events,
 * separated from the previous group by 8dp `canvas`. An empty group keeps its title without a
 * count and shows a Section message (`.pen` 40-5).
 */
private fun LazyListScope.eventGroup(
    key: String,
    title: String,
    events: List<EventSummary>,
    isFirst: Boolean = false,
    onEventClick: (String) -> Unit,
) {
    if (!isFirst) {
        item(key = "gap-$key") {
            Spacer(Modifier.fillMaxWidth().height(VlrDimensions.Space2).background(VlrTheme.colors.canvas))
        }
    }
    item(key = "section-$key") {
        SectionHeader(
            title = title,
            count = events.size.takeIf { it > 0 }?.toString(),
            modifier = Modifier.background(VlrTheme.colors.surface),
        )
    }
    if (events.isEmpty()) {
        item(key = "section-$key-empty") { EmptySectionRow() }
    } else {
        items(items = events, key = EventSummary::id) { event ->
            EventListItem(
                event = event,
                onClick = { onEventClick(event.id) },
            )
        }
    }
}

@Composable
private fun EventListItem(
    event: EventSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IdentityRow(
        title = event.name,
        meta = event.metadataLabel(spoken = false),
        metaContentDescription = event.metadataLabel(spoken = true),
        leading = { LogoSlot(imageUrl = event.imageUrl, kind = LogoSlotKind.Event) },
        onClick = onClick,
        modifier = modifier.testTag(eventRowTag(event.id)),
    )
}

@Composable
private fun EmptySectionRow() {
    SectionMessage(
        message = "현재 이벤트가 없어요.",
        modifier = Modifier.background(VlrTheme.colors.surface),
    )
}

/** First-load placeholder (`.pen` 40-2): six list-row skeletons under an 8dp top inset, without group headers. */
@Composable
private fun EventsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .padding(top = VlrDimensions.Space2)
            .testTag(EVENTS_LOADING_TAG)
            .clearAndSetSemantics {
                stateDescription = "이벤트를 불러오는 중"
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        repeat(6) { SkeletonRow() }
    }
}

@Composable
private fun EventsEmptyState(modifier: Modifier = Modifier) {
    StatePanel(
        title = "표시할 이벤트가 없어요.",
        icon = Res.drawable.ic_trophy,
        modifier = modifier.background(VlrTheme.colors.surface),
    )
}

@Composable
private fun EventsErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatePanel(
        title = "이벤트를 불러오지 못했습니다.",
        body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
        actionLabel = "재시도",
        onAction = onRetry,
        actionTestTag = EVENTS_INITIAL_RETRY_TAG,
        modifier = modifier.background(VlrTheme.colors.surface),
    )
}

/**
 * Group headers carry the status. Only `중단` is spelled out, because it shares the
 * `종료 · 중단` group with finished events. The period is Korean when the server sends
 * `startDate`, otherwise the source `dateLabel`; [spoken] reads `~` as `부터`·`까지`.
 */
private fun EventSummary.metadataLabel(spoken: Boolean): String? {
    val period = if (spoken) {
        KoreanDateTimeFormat.periodSpoken(startDate, endDate, dateLabel)
    } else {
        KoreanDateTimeFormat.period(startDate, endDate, dateLabel)
    }
    return listOfNotNull(PausedLabel.takeIf { status == EventStatus.PAUSED }, period, regionCode)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

private const val PausedLabel = "중단"
