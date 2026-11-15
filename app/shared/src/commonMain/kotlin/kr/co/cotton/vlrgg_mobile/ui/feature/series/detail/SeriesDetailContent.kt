package kr.co.cotton.vlrgg_mobile.ui.feature.series.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonRow
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
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
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus

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

/** `.pen` 42-2: dark hero skeleton and four list-row skeletons on `surface`. */
@Composable
private fun SeriesDetailLoading(listState: LazyListState, modifier: Modifier) {
    LazyColumn(state = listState, modifier = modifier.testTag(SERIES_DETAIL_LOADING_TAG)) {
        item("identity") { SkeletonHero() }
        item("skeleton-rows") {
            Column(
                Modifier.clearAndSetSemantics {
                    stateDescription = "시리즈 정보를 불러오는 중"
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
            ) {
                repeat(4) { SkeletonRow() }
            }
        }
    }
}

/**
 * `.pen` 42-1: hero, then white `예정 대회`·`완료 대회` sections on `canvas`, 8dp apart and 8dp below
 * the hero. With no events at all the State panel fills the rest of the screen on `surface`.
 */
@Composable
private fun SeriesDetailBody(
    series: SeriesDetail,
    listState: LazyListState,
    onEventClick: (String) -> Unit,
    modifier: Modifier,
) {
    val isOverallEmpty = series.upcomingEvents.isEmpty() && series.completedEvents.isEmpty()
    BoxWithConstraints(modifier) {
        var heroHeight by remember { mutableIntStateOf(0) }
        val remainingHeight = (maxHeight - with(LocalDensity.current) { heroHeight.toDp() }).coerceAtLeast(0.dp)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(if (isOverallEmpty) VlrTheme.colors.surface else VlrTheme.colors.canvas),
        ) {
            item("identity") {
                SeriesIdentity(series, Modifier.onSizeChanged { heroHeight = it.height })
            }
            if (isOverallEmpty) {
                // A fixed height bounds the panel, so it centers in the rest of the screen and scrolls
                // its own content when a tall hero (large font) leaves little room.
                item("overall-empty") {
                    OverallEmpty(Modifier.height(remainingHeight.coerceAtLeast(OverallEmptyMinHeight)))
                }
            } else {
                eventSection(
                    key = "upcoming",
                    title = "예정 대회",
                    events = series.upcomingEvents,
                    emptyMessage = "예정된 대회가 없습니다.",
                    sectionTag = SERIES_DETAIL_UPCOMING_SECTION_TAG,
                    onEventClick = onEventClick,
                )
                eventSection(
                    key = "completed",
                    title = "완료 대회",
                    events = series.completedEvents,
                    emptyMessage = "종료된 대회가 없습니다.",
                    sectionTag = SERIES_DETAIL_COMPLETED_SECTION_TAG,
                    onEventClick = onEventClick,
                )
                item("bottom-space") { Spacer(Modifier.height(VlrDimensions.Space8)) }
            }
        }
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
    item("$key-gap") {
        Spacer(Modifier.fillMaxWidth().height(VlrDimensions.Space2).background(VlrTheme.colors.canvas))
    }
    item("$key-heading") {
        SectionHeader(
            title = title,
            count = events.size.takeIf { it > 0 }?.toString(),
            modifier = Modifier.background(VlrTheme.colors.surface).testTag(sectionTag),
        )
    }
    if (events.isEmpty()) {
        item("$key-empty") { SectionEmpty(emptyMessage) }
    } else {
        items(events, key = EventSummary::id) { event ->
            SeriesEventRow(event = event, onClick = { onEventClick(event.id) })
        }
    }
}

@Composable
private fun SeriesIdentity(series: SeriesDetail, modifier: Modifier = Modifier) {
    EntityHero(
        name = series.name,
        meta = listOfNotNull(series.description),
        modifier = modifier.testTag(SERIES_DETAIL_IDENTITY_TAG),
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
private fun SeriesEventRow(event: EventSummary, onClick: () -> Unit) {
    IdentityRow(
        title = event.name,
        meta = event.metadataLabel(spoken = false),
        metaContentDescription = event.metadataLabel(spoken = true),
        leading = {
            LogoSlot(
                imageUrl = event.imageUrl,
                kind = LogoSlotKind.Event,
                imageTestTag = seriesEventImageTag(event.id),
            )
        },
        onClick = onClick,
        contentDescription = "이벤트 상세: ${event.name}",
        badge = if (event.status == EventStatus.ONGOING) {
            { StatusChip(StatusChipStatus.InProgress, OngoingLabel) }
        } else {
            null
        },
        modifier = Modifier.testTag(seriesEventRowTag(event.id)),
    )
}

/**
 * Section titles carry the status. The mixed sections spell out the minority status: ongoing
 * events get the `진행 중` chip among upcoming ones, and `일시 중지` leads the meta among finished ones.
 * The period is Korean when the server sends `startDate`, otherwise the source `dateLabel`;
 * [spoken] reads `~` as `부터`·`까지`.
 */
private fun EventSummary.metadataLabel(spoken: Boolean): String {
    val period = if (spoken) {
        KoreanDateTimeFormat.periodSpoken(startDate, endDate, dateLabel)
    } else {
        KoreanDateTimeFormat.period(startDate, endDate, dateLabel)
    }
    val labels = listOfNotNull(
        PausedLabel.takeIf { status == EventStatus.PAUSED },
        period?.takeIf(String::isNotBlank),
        regionCode?.takeIf(String::isNotBlank),
    )
    return labels.joinToString(" · ").ifEmpty { "정보 없음" }
}

private val OverallEmptyMinHeight = 240.dp

private const val PausedLabel = "일시 중지"
private const val OngoingLabel = "진행 중"

@Composable
private fun SectionEmpty(message: String) {
    SectionMessage(message = message, modifier = Modifier.background(VlrTheme.colors.surface))
}

@Composable
private fun OverallEmpty(modifier: Modifier = Modifier) {
    StatePanel(
        title = "표시할 대회가 없습니다.",
        icon = Res.drawable.ic_trophy,
        modifier = modifier.fillMaxWidth().testTag(SERIES_DETAIL_EMPTY_TAG),
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
