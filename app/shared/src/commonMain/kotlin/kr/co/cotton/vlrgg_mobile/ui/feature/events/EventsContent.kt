package kr.co.cotton.vlrgg_mobile.ui.feature.events

import androidx.compose.foundation.layout.Box
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
                modifier = Modifier.fillMaxSize(),
            ) {
                when (contentState) {
                    EventsContentState.Loading -> eventsSkeleton()
                    EventsContentState.Empty -> item(key = "empty") {
                        EventsEmptyState(modifier = Modifier.fillParentMaxSize())
                    }

                    EventsContentState.Error -> item(key = "error") {
                        if (uiState.busyRetry?.isDialogVisible == true || isBusyCooldownActive) {
                            Box(Modifier.fillParentMaxSize())
                        } else {
                            EventsErrorState(
                                onRetry = onRetry,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }

                    is EventsContentState.Content -> {
                        eventSection(
                            key = "ongoing",
                            title = "Ongoing",
                            events = contentState.events.ongoing,
                            isFirst = true,
                            onEventClick = onEventClick,
                        )
                        eventSection(
                            key = "upcoming",
                            title = "Upcoming",
                            events = contentState.events.upcoming,
                            onEventClick = onEventClick,
                        )
                        eventSection(
                            key = "completed-paused",
                            title = "Completed / Paused",
                            events = contentState.events.completedOrPaused,
                            onEventClick = onEventClick,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.eventSection(
    key: String,
    title: String,
    events: List<EventSummary>,
    isFirst: Boolean = false,
    onEventClick: (String) -> Unit,
) {
    sectionHeading(key = key, title = title, isFirst = isFirst)
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

private fun LazyListScope.sectionHeading(
    key: String,
    title: String,
    isFirst: Boolean,
) {
    item(key = "section-$key") {
        SectionHeader(
            title = title,
            modifier = if (isFirst) Modifier else Modifier.padding(top = VlrDimensions.Space2),
        )
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
        meta = event.metadataLabel(),
        leading = { LogoSlot(imageUrl = event.imageUrl, kind = LogoSlotKind.Event) },
        onClick = onClick,
        modifier = modifier.testTag(eventRowTag(event.id)),
    )
}

@Composable
private fun EmptySectionRow() {
    SectionMessage(message = "현재 이벤트가 없어요.")
}

private fun LazyListScope.eventsSkeleton() {
    skeletonSection(
        key = "ongoing",
        title = "Ongoing",
        isFirst = true,
        loadingTag = EVENTS_LOADING_TAG,
    )
    skeletonSection(key = "upcoming", title = "Upcoming")
    skeletonSection(key = "completed-paused", title = "Completed / Paused")
}

private fun LazyListScope.skeletonSection(
    key: String,
    title: String,
    isFirst: Boolean = false,
    loadingTag: String? = null,
) {
    sectionHeading(key = "skeleton-$key", title = title, isFirst = isFirst)
    item(key = "skeleton-row-$key") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (loadingTag != null) {
                        Modifier
                            .testTag(loadingTag)
                            .semantics { stateDescription = "이벤트를 불러오는 중" }
                    } else {
                        Modifier
                    },
                ),
        ) {
            EventsSkeletonRow()
        }
    }
}

@Composable
private fun EventsSkeletonRow() {
    SkeletonRow()
}

@Composable
private fun EventsEmptyState(modifier: Modifier = Modifier) {
    StatePanel(
        title = "표시할 이벤트가 없어요.",
        icon = Res.drawable.ic_trophy,
        modifier = modifier,
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
        modifier = modifier,
    )
}

/**
 * Group headers carry the status. Only `중단` is spelled out, because it shares the
 * `Completed / Paused` group with finished events.
 */
private fun EventSummary.metadataLabel(): String? =
    listOfNotNull(PausedLabel.takeIf { status == EventStatus.PAUSED }, dateLabel, regionCode)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

private const val PausedLabel = "중단"
