package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventPlayerStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.component.StatsColumn
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.VlrTab
import kr.co.cotton.vlrgg_mobile.ui.component.VlrTabs
import kr.co.cotton.vlrgg_mobile.ui.component.VlrStatsTable
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.MatchCard
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components.NewsListItem
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_bar_chart
import vlrggmobile.app.shared.generated.resources.ic_newspaper
import vlrggmobile.app.shared.generated.resources.ic_scoreboard
import org.jetbrains.compose.resources.DrawableResource
import kr.co.cotton.vlrgg_mobile.ui.component.DetailTopBand
import kr.co.cotton.vlrgg_mobile.ui.component.EntityHero
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotDefaults
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotSurface
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonHero
import kr.co.cotton.vlrgg_mobile.ui.component.StatsMissingValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

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
        topBar = { DetailTopBand(onBack = onBack) },
    ) { contentPadding ->
        when (val identity = uiState.identity) {
            EventIdentityContentState.Loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .testTag(EVENT_DETAIL_LOADING_TAG),
            ) {
                SkeletonHero()
                EventStateMessage(message = "이벤트 정보를 불러오는 중", loading = true)
            }

            EventIdentityContentState.Error -> if (uiState.busyRetry?.isDialogVisible == true) {
                Box(Modifier.fillMaxSize().padding(contentPadding))
            } else {
                StatePanel(
                    title = "이벤트 정보를 불러오지 못했습니다.",
                    body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                    actionLabel = "재시도",
                    actionTestTag = EVENT_DETAIL_IDENTITY_RETRY_TAG,
                    actionEnabled = retryEnabled,
                    onAction = onRetryIdentity,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }

            is EventIdentityContentState.Content -> EventDetailCollapsingBody(
                hero = { EventIdentityHeader(identity.event) },
                tabs = { EventDetailTabs(uiState.selectedTab, onSelectTab) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
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

/**
 * Hero, tabs and the selected tab share one vertical scroll. Scrolling up collapses the hero
 * before the tab list moves, and the tab content keeps the viewport height under the tabs, so a
 * tall hero (long text, large font, short screen) never pushes the tabs out of reach.
 */
@Composable
private fun EventDetailCollapsingBody(
    hero: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val scrollState = rememberScrollState()
        val collapseHeroFirst = remember(scrollState) { CollapseHeroFirst(scrollState) }
        var tabsHeight by remember { mutableIntStateOf(0) }
        val contentHeight = (maxHeight - with(LocalDensity.current) { tabsHeight.toDp() }).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(collapseHeroFirst)
                .verticalScroll(scrollState),
        ) {
            hero()
            Box(Modifier.fillMaxWidth().onSizeChanged { tabsHeight = it.height }) { tabs() }
            Box(Modifier.fillMaxWidth().height(contentHeight)) { content() }
        }
    }
}

/** Lets the outer scroll take upward drags from the tab list until the hero is gone. */
private class CollapseHeroFirst(private val outer: ScrollState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f || !outer.canScrollForward) return Offset.Zero
        return Offset(0f, -outer.dispatchRawDelta(-available.y))
    }
}

@Composable
private fun EventIdentityHeader(event: EventDetail) {
    EntityHero(
        name = event.name,
        meta = listOfNotNull(
            listOfNotNull(event.series, event.dateLabel, event.location)
                .joinToString(" · ")
                .takeIf(String::isNotEmpty),
            event.description,
        ),
        logo = {
            LogoSlot(
                imageUrl = event.imageUrl,
                kind = LogoSlotKind.Event,
                size = LogoSlotDefaults.HeroSize,
                surface = LogoSlotSurface.Dark,
            )
        },
        facts = event.status?.let { status ->
            { StatusChip(status.toChipStatus(), status.displayLabel()) }
        },
    )
}

@Composable
private fun EventDetailTabs(
    selectedTab: EventDetailTab,
    onSelectTab: (EventDetailTab) -> Unit,
) {
    VlrTabs {
        EventDetailTab.entries.forEach { tab ->
            VlrTab(
                label = tab.label(),
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                testTag = eventDetailTabTag(tab),
            )
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
        EventMatchesContentState.Empty -> TabStatePanel("표시할 경기가 없어요.", Res.drawable.ic_scoreboard)
        EventMatchesContentState.Error -> if (suppressError) Box(Modifier.fillMaxSize()) else EventTabError("경기를 불러오지 못했습니다.", onRetry, retryEnabled)
        is EventMatchesContentState.Content -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
        EventNewsContentState.Empty -> TabStatePanel("표시할 뉴스가 없어요.", Res.drawable.ic_newspaper)
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
        EventStatsContentState.Empty -> TabStatePanel("아직 제공되는 통계가 없어요.", Res.drawable.ic_bar_chart)
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
    StatePanel(
        title = message,
        actionLabel = "재시도",
        actionTestTag = EVENT_DETAIL_TAB_RETRY_TAG,
        actionEnabled = enabled,
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TabStatePanel(title: String, icon: DrawableResource) {
    StatePanel(title = title, icon = icon, modifier = Modifier.fillMaxSize())
}

@Composable
private fun EventStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
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
            CircularProgressIndicator(color = VlrTheme.colors.accent)
            Spacer(Modifier.height(VlrDimensions.Space3))
        }
        Text(
            text = message,
            style = VlrTheme.typography.body,
            color = VlrTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

private fun EventDetailTab.label(): String = when (this) {
    EventDetailTab.MATCHES -> "경기"
    EventDetailTab.NEWS -> "뉴스"
    EventDetailTab.STATS -> "통계"
}

private fun EventStatus.displayLabel(): String = when (this) {
    EventStatus.ONGOING -> "진행 중"
    EventStatus.UPCOMING -> "예정"
    EventStatus.COMPLETED -> "종료"
    EventStatus.PAUSED -> "중단"
}

private fun EventStatus.toChipStatus(): StatusChipStatus = when (this) {
    EventStatus.ONGOING -> StatusChipStatus.InProgress
    EventStatus.UPCOMING -> StatusChipStatus.Upcoming
    EventStatus.COMPLETED -> StatusChipStatus.Completed
    EventStatus.PAUSED -> StatusChipStatus.Unavailable
}

private val eventStatsColumns = listOf(
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.ROUNDS, "Rounds",
        { it.roundsPlayed?.toString().orMissing() }, { it.roundsPlayed?.toDouble() },
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.RATING, "Rating",
        { it.rating?.toString().orMissing() }, EventPlayerStats::rating,
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.ACS, "ACS",
        { it.averageCombatScore?.toString().orMissing() }, { it.averageCombatScore?.toDouble() },
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.K_D, "K-D",
        { it.killDeathRatio?.toString().orMissing() }, EventPlayerStats::killDeathRatio,
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.ADR, "ADR",
        { it.averageDamagePerRound?.toString().orMissing() }, EventPlayerStats::averageDamagePerRound,
    ),
    StatsColumn<EventPlayerStats, EventStatsSortColumn>(
        EventStatsSortColumn.KAST, "KAST",
        { it.killAssistSurvivedTradedPercentage?.let { value -> "$value%" }.orMissing() },
        EventPlayerStats::killAssistSurvivedTradedPercentage,
    ),
)

private fun String?.orMissing(): String = this ?: StatsMissingValue
