package kr.co.cotton.vlrgg_mobile.ui.feature.series.detail

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_error
import vlrggmobile.app.shared.generated.resources.ic_event

internal const val SERIES_DETAIL_LOADING_TAG = "series-detail-loading"
internal const val SERIES_DETAIL_IDENTITY_TAG = "series-detail-identity"
internal const val SERIES_DETAIL_UPCOMING_SECTION_TAG = "series-detail-upcoming-section"
internal const val SERIES_DETAIL_COMPLETED_SECTION_TAG = "series-detail-completed-section"
internal const val SERIES_DETAIL_EMPTY_TAG = "series-detail-empty"
internal const val SERIES_DETAIL_ERROR_TAG = "series-detail-error"

internal fun seriesEventRowTag(eventId: String): String = "series-event-$eventId"
internal fun seriesEventImageTag(eventId: String): String = "series-event-image-$eventId"

@Composable
fun SeriesDetailContent(
    uiState: SeriesDetailUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onEventClick: (eventId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { SeriesDetailTopBar(onBack) },
    ) { contentPadding ->
        when (val contentState = uiState.contentState) {
            SeriesDetailContentState.Loading -> SeriesDetailLoading(
                listState = listState,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )

            is SeriesDetailContentState.Content -> SeriesDetailBody(
                series = contentState.series,
                listState = listState,
                onEventClick = onEventClick,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )

            SeriesDetailContentState.Error -> SeriesDetailError(
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
        }
    }
}

@Composable
private fun SeriesDetailTopBar(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().height(56.dp).background(VlrTheme.colors.surface),
    ) {
        VlrIconButton(
            contentDescription = "뒤로 가기",
            onClick = onBack,
            modifier = Modifier.padding(start = VlrDimensions.Space1).weight(1f),
            icon = { Icon(vectorResource(Res.drawable.ic_arrow_back), contentDescription = null) },
        )
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.outline)
    }
}

@Composable
private fun SeriesDetailLoading(listState: LazyListState, modifier: Modifier) {
    LazyColumn(state = listState, modifier = modifier.testTag(SERIES_DETAIL_LOADING_TAG)) {
        item("identity") { SeriesIdentitySkeleton() }
        skeletonSection("upcoming", "Upcoming Events")
        skeletonSection("completed", "Completed Events")
    }
}

private fun LazyListScope.skeletonSection(key: String, title: String) {
    item("$key-heading") { SeriesSectionHeading(title) }
    item("$key-row") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(VlrDimensions.Space4),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        ) {
            SkeletonBlock(Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
                SkeletonBlock(Modifier.fillMaxWidth().height(16.dp))
                SkeletonBlock(Modifier.width(96.dp).height(12.dp))
            }
        }
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.outline)
    }
}

@Composable
private fun SeriesIdentitySkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space6),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        SkeletonBlock(Modifier.width(180.dp).height(34.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(20.dp))
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(VlrDimensions.DefaultCornerRadius)).background(VlrTheme.colors.skeleton))
}

@Composable
private fun SeriesDetailBody(
    series: SeriesDetail,
    listState: LazyListState,
    onEventClick: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(state = listState, modifier = modifier) {
        item("identity") { SeriesIdentity(series) }
        if (series.upcomingEvents.isEmpty() && series.completedEvents.isEmpty()) {
            item("overall-empty") { OverallEmpty() }
        } else {
            eventSection(
                key = "upcoming",
                title = "Upcoming Events",
                events = series.upcomingEvents,
                emptyMessage = "예정된 대회가 없습니다.",
                sectionTag = SERIES_DETAIL_UPCOMING_SECTION_TAG,
                onEventClick = onEventClick,
            )
            eventSection(
                key = "completed",
                title = "Completed Events",
                events = series.completedEvents,
                emptyMessage = "종료된 대회가 없습니다.",
                sectionTag = SERIES_DETAIL_COMPLETED_SECTION_TAG,
                onEventClick = onEventClick,
            )
        }
        item("bottom-space") { Spacer(Modifier.height(VlrDimensions.Space8)) }
    }
}

private fun LazyListScope.eventSection(
    key: String,
    title: String,
    events: List<EventSummary>,
    emptyMessage: String,
    sectionTag: String,
    onEventClick: (String) -> Unit,
) {
    item("$key-heading") { SeriesSectionHeading(title, Modifier.testTag(sectionTag)) }
    if (events.isEmpty()) {
        item("$key-empty") { SectionEmpty(emptyMessage) }
    } else {
        items(events, key = EventSummary::id) { event ->
            SeriesEventRow(event = event, onClick = { onEventClick(event.id) })
        }
    }
}

@Composable
private fun SeriesIdentity(series: SeriesDetail) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(SERIES_DETAIL_IDENTITY_TAG)
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space6),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Text(series.name, style = VlrTheme.typography.display, color = VlrTheme.colors.textPrimary)
        series.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(description, style = VlrTheme.typography.body, color = VlrTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun SeriesSectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth().background(VlrTheme.colors.surfaceSubtle)
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
        style = VlrTheme.typography.sectionTitle,
        color = VlrTheme.colors.textPrimary,
    )
}

@Composable
private fun SeriesEventRow(event: EventSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = VlrDimensions.MinimumTouchTarget)
            .testTag(seriesEventRowTag(event.id))
            .semantics { contentDescription = "이벤트 상세: ${event.name}" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        event.imageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).testTag(seriesEventImageTag(event.id))
                    .clip(RoundedCornerShape(VlrDimensions.DefaultCornerRadius)),
            )
            Spacer(Modifier.width(VlrDimensions.Space3))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1)) {
            Text(event.name, style = VlrTheme.typography.bodyStrong, color = VlrTheme.colors.textPrimary)
            EventMetadata(event)
        }
        Spacer(Modifier.width(VlrDimensions.Space2))
        StatusChip(event.status.toChipStatus(), event.status.label(), Modifier.align(Alignment.Top))
    }
    HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.outline)
}

@Composable
private fun EventMetadata(event: EventSummary) {
    val labels = listOfNotNull(event.dateLabel?.takeIf(String::isNotBlank), event.regionCode?.takeIf(String::isNotBlank))
    Text(
        text = labels.joinToString(" · ").ifEmpty { "정보 없음" },
        style = VlrTheme.typography.labelSmall,
        color = VlrTheme.colors.textSecondary,
    )
}

@Composable
private fun SectionEmpty(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = VlrDimensions.MinimumTouchTarget)
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(vectorResource(Res.drawable.ic_event), contentDescription = null, tint = VlrTheme.colors.textSecondary)
        Text(message, style = VlrTheme.typography.body, color = VlrTheme.colors.textSecondary)
    }
    HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.outline)
}

@Composable
private fun OverallEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(SERIES_DETAIL_EMPTY_TAG)
            .padding(VlrDimensions.Space4)
            .clip(RoundedCornerShape(VlrDimensions.DefaultCornerRadius))
            .background(VlrTheme.colors.surfaceSubtle)
            .padding(VlrDimensions.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Icon(vectorResource(Res.drawable.ic_event), contentDescription = null, tint = VlrTheme.colors.textSecondary)
        Text("표시할 대회가 없습니다.", style = VlrTheme.typography.body, color = VlrTheme.colors.textSecondary)
    }
}

@Composable
private fun SeriesDetailError(onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.testTag(SERIES_DETAIL_ERROR_TAG).padding(VlrDimensions.Space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(vectorResource(Res.drawable.ic_error), contentDescription = null, modifier = Modifier.size(48.dp), tint = VlrTheme.colors.textSecondary)
        Spacer(Modifier.height(VlrDimensions.Space4))
        Text("시리즈 정보를 불러오지 못했습니다", style = VlrTheme.typography.pageTitle, color = VlrTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(VlrDimensions.Space2))
        Text("잠시 후 다시 시도해 주세요.", style = VlrTheme.typography.body, color = VlrTheme.colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(VlrDimensions.Space6))
        VlrButton(text = "재시도", onClick = onRetry)
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
    EventStatus.COMPLETED -> "종료됨"
    EventStatus.PAUSED -> "일시 중지"
}
