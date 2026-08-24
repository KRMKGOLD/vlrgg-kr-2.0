package kr.co.cotton.vlrgg_mobile.ui.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.RootTopBar
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_error

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
    val contentState = uiState.contentState
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { EventsTopBar(onSearch = onSearch) },
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
                        EventsErrorState(
                            onRetry = onRetry,
                            modifier = Modifier.fillParentMaxSize(),
                        )
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
        Text(
            text = title,
            modifier = Modifier.padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = if (isFirst) VlrDimensions.Space4 else VlrDimensions.Space6,
                bottom = VlrDimensions.Space2,
            ),
            style = VlrTheme.typography.sectionTitle,
            color = VlrTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun EventsTopBar(onSearch: () -> Unit) = RootTopBar(
    title = "Events",
    onSearch = onSearch,
)

@Composable
private fun EventListItem(
    event: EventSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageShape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = VlrDimensions.MinimumTouchTarget)
            .testTag(eventRowTag(event.id))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        event.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(imageShape),
            )
            Spacer(Modifier.width(VlrDimensions.Space3))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.name,
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
            )
            event.metadataLabel()?.let { metadata ->
                Spacer(Modifier.height(VlrDimensions.Space1))
                Text(
                    text = metadata,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(VlrDimensions.Space2))
        StatusChip(
            status = event.status.toChipStatus(),
            label = event.status.label(),
            modifier = Modifier.align(Alignment.Top),
        )
    }
    HorizontalDivider(
        thickness = VlrDimensions.OutlineWidth,
        color = VlrTheme.colors.outline,
    )
}

@Composable
private fun EmptySectionRow() {
    Text(
        text = "현재 이벤트가 없어요.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space3,
            ),
        style = VlrTheme.typography.body,
        color = VlrTheme.colors.textSecondary,
    )
    HorizontalDivider(
        thickness = VlrDimensions.OutlineWidth,
        color = VlrTheme.colors.outline,
    )
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
    val placeholderShape = RoundedCornerShape(VlrDimensions.Space1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space3,
            )
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(20.dp)
                    .background(VlrTheme.colors.skeleton, placeholderShape),
            )
            Spacer(Modifier.height(VlrDimensions.Space1))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(VlrDimensions.Space3)
                    .background(VlrTheme.colors.skeleton, placeholderShape),
            )
        }
        Spacer(Modifier.width(VlrDimensions.Space2))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(24.dp)
                .background(VlrTheme.colors.skeleton, CircleShape),
        )
    }
    HorizontalDivider(
        thickness = VlrDimensions.OutlineWidth,
        color = VlrTheme.colors.outline,
    )
}

@Composable
private fun EventsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(VlrDimensions.Space6),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "표시할 이벤트가 없어요.",
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EventsErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(VlrDimensions.Space6),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space6),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(VlrTheme.colors.surfaceSubtle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_error),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = VlrTheme.colors.textSecondary,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            ) {
                Text(
                    text = "이벤트를 불러오지 못했습니다.",
                    style = VlrTheme.typography.sectionTitle,
                    color = VlrTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                    style = VlrTheme.typography.body,
                    color = VlrTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            VlrButton(
                text = "재시도",
                onClick = onRetry,
                modifier = Modifier.testTag(EVENTS_INITIAL_RETRY_TAG),
            )
        }
    }
}

private fun EventSummary.metadataLabel(): String? = listOfNotNull(dateLabel, regionCode)
    .takeIf { it.isNotEmpty() }
    ?.joinToString(" · ")

private fun EventStatus.toChipStatus(): StatusChipStatus = when (this) {
    EventStatus.ONGOING -> StatusChipStatus.Live
    EventStatus.UPCOMING -> StatusChipStatus.Upcoming
    EventStatus.COMPLETED -> StatusChipStatus.Completed
    EventStatus.PAUSED -> StatusChipStatus.Postponed
}

private fun EventStatus.label(): String = when (this) {
    EventStatus.ONGOING -> "진행 중"
    EventStatus.UPCOMING -> "예정"
    EventStatus.COMPLETED -> "종료"
    EventStatus.PAUSED -> "중단"
}
