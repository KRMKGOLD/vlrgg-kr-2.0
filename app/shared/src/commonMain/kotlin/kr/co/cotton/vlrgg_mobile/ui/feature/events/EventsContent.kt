package kr.co.cotton.vlrgg_mobile.ui.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_search

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
                .padding(contentPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (contentState) {
                    EventsContentState.Loading -> item(key = "loading") { EventsSkeleton() }
                    EventsContentState.Empty -> item(key = "empty") {
                        EventsStateMessage(
                            message = "표시할 이벤트가 없어요.",
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    EventsContentState.Error -> item(key = "error") {
                        EventsStateMessage(
                            message = "이벤트를 불러오지 못했습니다.\n네트워크 상태를 확인하고 다시 시도해 주세요.",
                            actionText = "재시도",
                            onAction = onRetry,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    is EventsContentState.Content -> {
                        eventSection(
                            key = "ongoing",
                            title = "Ongoing",
                            events = contentState.events.ongoing,
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
    onEventClick: (String) -> Unit,
) {
    item(key = "section-$key") {
        Text(
            text = title,
            modifier = Modifier.padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = VlrDimensions.Space6,
                bottom = VlrDimensions.Space2,
            ),
            style = VlrTheme.typography.sectionTitle,
            color = VlrTheme.colors.textPrimary,
        )
    }
    if (events.isEmpty()) {
        item(key = "section-$key-empty") {
            Text(
                text = "현재 이벤트가 없어요.",
                modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
            )
        }
    } else {
        items(items = events, key = EventSummary::id) { event ->
            EventListItem(
                event = event,
                onClick = { onEventClick(event.id) },
                modifier = Modifier.padding(
                    horizontal = VlrDimensions.Space4,
                    vertical = VlrDimensions.Space1,
                ),
            )
        }
    }
}

@Composable
private fun EventsTopBar(onSearch: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "Events",
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = VlrTheme.colors.surface),
    )
}

@Composable
private fun EventListItem(
    event: EventSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(VlrTheme.colors.surface)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, cardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        event.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(cardShape),
            )
            Spacer(Modifier.width(VlrDimensions.Space3))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.name,
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
            )
            event.dateLabel?.let { dateLabel ->
                Text(
                    text = dateLabel,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                )
            }
            event.regionCode?.let { regionCode ->
                Text(
                    text = regionCode,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(VlrDimensions.Space2))
        StatusChip(
            status = event.status.toChipStatus(),
            label = event.status.label(),
        )
    }
}

@Composable
private fun EventsSkeleton() {
    val cardShape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    Column(
        modifier = Modifier.padding(VlrDimensions.Space4),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(cardShape)
                    .background(VlrTheme.colors.skeleton),
            )
        }
    }
}

@Composable
private fun EventsStateMessage(
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
