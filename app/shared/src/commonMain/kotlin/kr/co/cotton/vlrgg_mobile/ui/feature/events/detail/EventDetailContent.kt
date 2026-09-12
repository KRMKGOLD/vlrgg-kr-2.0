package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventPlayerStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.component.StatsColumn
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrStatsTable
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.MatchCard
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components.NewsListItem
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back

internal const val EVENT_DETAIL_IDENTITY_RETRY_TAG = "event-detail-identity-retry"
internal const val EVENT_DETAIL_TAB_RETRY_TAG = "event-detail-tab-retry"
internal const val EVENT_DETAIL_LOADING_TAG = "event-detail-loading"

internal fun eventStatsPlayerTag(playerId: String): String = "event-stats-player-$playerId"
internal fun eventStatsMetricHeaderTag(column: EventStatsSortColumn): String = "event-stats-header-${column.savedStateId}"
internal fun eventStatsMetricValueTag(playerId: String, column: EventStatsSortColumn): String = "event-stats-$playerId-${column.savedStateId}"
internal fun eventDetailTabTag(tab: EventDetailTab): String = "event-detail-tab-${tab.savedStateId}"

@Composable
fun EventDetailContent(
    uiState: EventDetailUiState,
    matchesListState: LazyListState,
    newsListState: LazyListState,
    statsListState: LazyListState,
    statsHorizontalScrollState: ScrollState,
    onBack: () -> Unit,
    onSelectTab: (EventDetailTab) -> Unit,
    onMatchClick: (String) -> Unit,
    onNewsClick: (String, String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onSortStats: (EventStatsSortColumn) -> Unit,
    onRetryIdentity: () -> Unit,
    onRetrySelectedTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryEnabled = !rememberBusyRetryCooldown(uiState.busyRetry)
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { EventDetailTopBar(onBack) },
    ) { contentPadding ->
        when (val identity = uiState.identity) {
            EventIdentityContentState.Loading -> EventStateMessage(
                message = "이벤트 정보를 불러오는 중",
                loading = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .testTag(EVENT_DETAIL_LOADING_TAG),
            )

            EventIdentityContentState.Error -> if (uiState.busyRetry?.isDialogVisible == true) {
                Box(Modifier.fillMaxSize().padding(contentPadding))
            } else {
                EventStateMessage(
                    message = "이벤트 정보를 불러오지 못했습니다.\n네트워크 상태를 확인하고 다시 시도해 주세요.",
                    actionText = "재시도",
                    actionTag = EVENT_DETAIL_IDENTITY_RETRY_TAG,
                    actionEnabled = retryEnabled,
                    onAction = onRetryIdentity,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }

            is EventIdentityContentState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                EventIdentityHeader(identity.event)
                EventDetailTabs(uiState.selectedTab, onSelectTab)
                Box(modifier = Modifier.weight(1f)) {
                    when (uiState.selectedTab) {
                        EventDetailTab.MATCHES -> MatchesTabContent(
                            state = uiState.matches,
                            listState = matchesListState,
                            onMatchClick = onMatchClick,
                            onRetry = onRetrySelectedTab,
                            suppressError = uiState.busyRetry?.isDialogVisible == true,
                            retryEnabled = retryEnabled,
                        )

                        EventDetailTab.NEWS -> NewsTabContent(
                            state = uiState.news,
                            listState = newsListState,
                            onNewsClick = onNewsClick,
                            onRetry = onRetrySelectedTab,
                            suppressError = uiState.busyRetry?.isDialogVisible == true,
                            retryEnabled = retryEnabled,
                        )

                        EventDetailTab.STATS -> StatsTabContent(
                            state = uiState.stats,
                            listState = statsListState,
                            horizontalScrollState = statsHorizontalScrollState,
                            sort = uiState.statsSort,
                            onSortColumn = onSortStats,
                            onPlayerClick = onPlayerClick,
                            onRetry = onRetrySelectedTab,
                            suppressError = uiState.busyRetry?.isDialogVisible == true,
                            retryEnabled = retryEnabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDetailTopBar(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(VlrTheme.colors.surface),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = VlrDimensions.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VlrIconButton(
                contentDescription = "뒤로 가기",
                onClick = onBack,
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_arrow_back),
                        contentDescription = null,
                    )
                },
            )
        }
        HorizontalDivider(color = VlrTheme.colors.outline)
    }
}

@Composable
private fun EventIdentityHeader(event: EventDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event.name,
                modifier = Modifier.weight(1f),
                style = VlrTheme.typography.pageTitle,
                color = VlrTheme.colors.textPrimary,
            )
            event.status?.let { status ->
                StatusChip(status.toChipStatus(), status.displayLabel())
            }
        }
        listOfNotNull(event.series, event.dateLabel, event.location)
            .takeIf { it.isNotEmpty() }
            ?.let { metadata ->
                Text(
                    text = metadata.joinToString(" · "),
                    style = VlrTheme.typography.label,
                    color = VlrTheme.colors.textSecondary,
                )
            }
        event.description?.let { description ->
            Text(
                text = description,
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EventDetailTabs(
    selectedTab: EventDetailTab,
    onSelectTab: (EventDetailTab) -> Unit,
) {
    val shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space2)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
            .clip(shape),
    ) {
        EventDetailTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = VlrDimensions.MinimumTouchTarget)
                    .testTag(eventDetailTabTag(tab))
                    .background(if (selected) VlrTheme.colors.surfaceSubtle else VlrTheme.colors.surface)
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
                    color = if (selected) VlrTheme.colors.actionPrimary else VlrTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun MatchesTabContent(
    state: EventMatchesContentState,
    listState: LazyListState,
    onMatchClick: (String) -> Unit,
    onRetry: () -> Unit,
    suppressError: Boolean,
    retryEnabled: Boolean,
) {
    when (state) {
        EventMatchesContentState.Loading -> EventStateMessage("경기를 불러오는 중", loading = true)
        EventMatchesContentState.Empty -> EventStateMessage("표시할 경기가 없어요.")
        EventMatchesContentState.Error -> if (suppressError) Box(Modifier.fillMaxSize()) else EventTabError("경기를 불러오지 못했습니다.", onRetry, retryEnabled)
        is EventMatchesContentState.Content -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(VlrDimensions.Space4),
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        ) {
            items(state.matches, key = { it.id }) { match ->
                MatchCard(
                    match = match,
                    onClick = { onMatchClick(match.id) },
                    showEventName = false,
                )
            }
        }
    }
}

@Composable
private fun NewsTabContent(
    state: EventNewsContentState,
    listState: LazyListState,
    onNewsClick: (String, String) -> Unit,
    onRetry: () -> Unit,
    suppressError: Boolean,
    retryEnabled: Boolean,
) {
    when (state) {
        EventNewsContentState.Loading -> EventStateMessage("뉴스를 불러오는 중", loading = true)
        EventNewsContentState.Empty -> EventStateMessage("표시할 뉴스가 없어요.")
        EventNewsContentState.Error -> if (suppressError) Box(Modifier.fillMaxSize()) else EventTabError("뉴스를 불러오지 못했습니다.", onRetry, retryEnabled)
        is EventNewsContentState.Content -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.news, key = { "${it.articleId}/${it.slug}" }) { news ->
                NewsListItem(
                    news = news,
                    onClick = { onNewsClick(news.articleId, news.slug) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
                    color = VlrTheme.colors.outline,
                )
            }
        }
    }
}

@Composable
private fun StatsTabContent(
    state: EventStatsContentState,
    listState: LazyListState,
    horizontalScrollState: ScrollState,
    sort: StatsSort<EventStatsSortColumn>?,
    onSortColumn: (EventStatsSortColumn) -> Unit,
    onPlayerClick: (String) -> Unit,
    onRetry: () -> Unit,
    suppressError: Boolean,
    retryEnabled: Boolean,
) {
    when (state) {
        EventStatsContentState.Loading -> EventStateMessage("통계를 불러오는 중", loading = true)
        EventStatsContentState.Empty -> EventStateMessage("아직 제공되는 통계가 없어요.")
        EventStatsContentState.Error -> if (suppressError) Box(Modifier.fillMaxSize()) else EventTabError("통계를 불러오지 못했습니다.", onRetry, retryEnabled)
        is EventStatsContentState.Content -> VlrStatsTable(
            rows = state.stats.players,
            rowKey = EventPlayerStats::playerId,
            identityHeader = "Player",
            identityText = EventPlayerStats::playerName,
            identitySupportingText = EventPlayerStats::teamAbbreviation,
            rowSemanticsLabel = "player",
            columns = eventStatsColumns,
            horizontalScrollState = horizontalScrollState,
            identityWidth = 132.dp,
            headerMinHeight = 56.dp,
            rowMinHeight = 56.dp,
            identityTextStyle = VlrTheme.typography.bodyStrong,
            headerTextStyle = VlrTheme.typography.label,
            valueTextStyle = VlrTheme.typography.label,
            modifier = Modifier.fillMaxSize(),
            lazyListState = listState,
            sort = sort,
            onSortColumn = onSortColumn,
            onIdentityClick = { onPlayerClick(it.playerId) },
            identityTestTag = { eventStatsPlayerTag(it.playerId) },
            metricHeaderTestTag = ::eventStatsMetricHeaderTag,
            metricValueTestTag = { player, column -> eventStatsMetricValueTag(player.playerId, column) },
        )
    }
}

@Composable
private fun EventTabError(message: String, onRetry: () -> Unit, enabled: Boolean) {
    EventStateMessage(
        message = message,
        actionText = "재시도",
        actionTag = EVENT_DETAIL_TAB_RETRY_TAG,
        actionEnabled = enabled,
        onAction = onRetry,
    )
}

@Composable
private fun EventStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionText: String? = null,
    actionTag: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (loading) Modifier.semantics { stateDescription = message } else Modifier)
            .padding(VlrDimensions.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = VlrTheme.colors.actionPrimary)
            Spacer(Modifier.height(VlrDimensions.Space3))
        }
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
                enabled = actionEnabled,
                modifier = Modifier
                    .padding(top = VlrDimensions.Space4)
                    .then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier),
            )
        }
    }
}

private fun EventDetailTab.label(): String = when (this) {
    EventDetailTab.MATCHES -> "Matches"
    EventDetailTab.NEWS -> "News"
    EventDetailTab.STATS -> "Stats"
}

private fun EventStatus.displayLabel(): String = when (this) {
    EventStatus.ONGOING -> "진행 중"
    EventStatus.UPCOMING -> "예정"
    EventStatus.COMPLETED -> "종료"
    EventStatus.PAUSED -> "중단"
}

private fun EventStatus.toChipStatus(): StatusChipStatus = when (this) {
    EventStatus.ONGOING -> StatusChipStatus.Live
    EventStatus.UPCOMING -> StatusChipStatus.Upcoming
    EventStatus.COMPLETED -> StatusChipStatus.Completed
    EventStatus.PAUSED -> StatusChipStatus.Unavailable
}

private val eventStatsColumns = listOf(
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.ROUNDS, "Rounds", 80.dp, TextAlign.Center,
        { it.roundsPlayed?.toString().orMissing() }, { it.roundsPlayed?.toDouble() },
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.RATING, "Rating", 80.dp, TextAlign.Center,
        { it.rating?.toString().orMissing() }, EventPlayerStats::rating,
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.ACS, "ACS", 80.dp, TextAlign.Center,
        { it.averageCombatScore?.toString().orMissing() }, { it.averageCombatScore?.toDouble() },
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.K_D, "K-D", 80.dp, TextAlign.Center,
        { it.killDeathRatio?.toString().orMissing() }, EventPlayerStats::killDeathRatio,
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.ADR, "ADR", 80.dp, TextAlign.Center,
        { it.averageDamagePerRound?.toString().orMissing() }, EventPlayerStats::averageDamagePerRound,
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.KAST, "KAST", 80.dp, TextAlign.Center,
        { it.killAssistSurvivedTradedPercentage?.let { value -> "$value%" }.orMissing() },
        EventPlayerStats::killAssistSurvivedTradedPercentage,
    ),
)

private fun String?.orMissing(): String = this ?: "—"
