package kr.co.cotton.vlrgg_mobile.ui.feature.series.detail

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonBlock
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_trophy
import kr.co.cotton.vlrgg_mobile.ui.component.DetailTopBand
import kr.co.cotton.vlrgg_mobile.ui.component.EntityHero
import kr.co.cotton.vlrgg_mobile.ui.component.IdentityRow
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotDefaults
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotSurface
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonHero

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
    val isBusyCooldownActive = rememberBusyRetryCooldown(uiState.busyRetry)
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { DetailTopBand(onBack = onBack) },
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

            SeriesDetailContentState.Error -> if (uiState.busyRetry?.isDialogVisible == true || isBusyCooldownActive) {
                Box(Modifier.fillMaxSize().padding(contentPadding))
            } else {
                SeriesDetailError(
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            }
        }
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
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.line)
    }
}

@Composable
private fun SeriesIdentitySkeleton() {
    SkeletonHero()
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
    EntityHero(
        name = series.name,
        meta = listOfNotNull(series.description),
        modifier = Modifier.testTag(SERIES_DETAIL_IDENTITY_TAG),
        logo = {
            LogoSlot(
                imageUrl = null,
                kind = LogoSlotKind.Event,
                size = LogoSlotDefaults.HeroSize,
                surface = LogoSlotSurface.Dark,
            )
        },
    )
}

@Composable
private fun SeriesSectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth().background(VlrTheme.colors.canvas)
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
        style = VlrTheme.typography.section,
        color = VlrTheme.colors.ink,
    )
}

@Composable
private fun SeriesEventRow(event: EventSummary, onClick: () -> Unit) {
    IdentityRow(
        title = event.name,
        meta = event.metadataLabel(),
        leading = {
            LogoSlot(
                imageUrl = event.imageUrl,
                kind = LogoSlotKind.Event,
                imageTestTag = seriesEventImageTag(event.id),
            )
        },
        onClick = onClick,
        contentDescription = "이벤트 상세: ${event.name}",
        modifier = Modifier.testTag(seriesEventRowTag(event.id)),
    )
}

/**
 * Section titles carry the status. Only `일시 중지` is spelled out, because it shares the
 * completed section with finished events.
 */
private fun EventSummary.metadataLabel(): String {
    val labels = listOfNotNull(
        PausedLabel.takeIf { status == EventStatus.PAUSED },
        dateLabel?.takeIf(String::isNotBlank),
        regionCode?.takeIf(String::isNotBlank),
    )
    return labels.joinToString(" · ").ifEmpty { "정보 없음" }
}

private const val PausedLabel = "일시 중지"

@Composable
private fun SectionEmpty(message: String) {
    SectionMessage(message = message)
}

@Composable
private fun OverallEmpty() {
    StatePanel(
        title = "표시할 대회가 없습니다.",
        icon = Res.drawable.ic_trophy,
        modifier = Modifier.fillMaxWidth().testTag(SERIES_DETAIL_EMPTY_TAG),
    )
}

@Composable
private fun SeriesDetailError(onRetry: () -> Unit, modifier: Modifier) {
    StatePanel(
        title = "시리즈 정보를 불러오지 못했습니다",
        body = "잠시 후 다시 시도해 주세요.",
        actionLabel = "재시도",
        onAction = onRetry,
        modifier = modifier.testTag(SERIES_DETAIL_ERROR_TAG),
    )
}
