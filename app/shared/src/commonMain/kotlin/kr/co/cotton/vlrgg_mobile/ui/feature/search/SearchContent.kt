package kr.co.cotton.vlrgg_mobile.ui.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrSearchField
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.rowDivider
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_chevron_right
import vlrggmobile.app.shared.generated.resources.ic_progress_activity
import vlrggmobile.app.shared.generated.resources.ic_search
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding

internal const val SEARCH_LOADING_TAG = "search-loading"
internal const val SEARCH_RETRY_TAG = "search-retry"
internal const val SEARCH_HEADER_TAG = "search-header"
internal const val SEARCH_RESULTS_SECTION_TAG = "search-results-section"
private const val SEARCH_RESULTS_HEADER_KEY = "search-results-header"

internal fun searchRowTag(result: SearchResult): String = "search-row-${result.stableListKey}"

@Composable
fun SearchContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusyCooldownActive = rememberBusyRetryCooldown(uiState.busyRetry)
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focusRequester) {
        if (!hasRequestedInitialFocus) {
            withFrameNanos { }
            hasRequestedInitialFocus = focusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Results leave `canvas` below the last row, down through the system bar area.
        containerColor = if (uiState.contentState is SearchContentState.Populated) {
            VlrTheme.colors.canvas
        } else {
            VlrTheme.colors.surface
        },
        topBar = {
            SearchTopBar(
                query = uiState.query,
                canSubmit = uiState.canSubmit && uiState.contentState != SearchContentState.Loading,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onBack = onBack,
                focusRequester = focusRequester,
            )
        },
    ) { contentPadding ->
        when (val contentState = uiState.contentState) {
            SearchContentState.Initial -> SearchInitialState(Modifier.padding(contentPadding))
            SearchContentState.Loading -> SearchLoadingState(uiState.query, Modifier.padding(contentPadding))
            SearchContentState.Empty -> SearchEmptyState(uiState.query, Modifier.padding(contentPadding))
            SearchContentState.Error -> if (uiState.busyRetry?.isDialogVisible == true || isBusyCooldownActive) {
                Box(Modifier.fillMaxSize().padding(contentPadding))
            } else {
                SearchErrorState(onRetry, Modifier.padding(contentPadding))
            }
            is SearchContentState.Populated -> SearchResults(
                query = uiState.query,
                items = contentState.items,
                onResultClick = onResultClick,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    canSubmit: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
    // Scaffold lays the body out first; the header group reads first (Back → input → clear → submit).
    Column(
        Modifier
            .background(VlrTheme.colors.surface)
            .testTag(SEARCH_HEADER_TAG)
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            }
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = VlrDimensions.Space1,
                    end = VlrDimensions.Space1,
                    top = VlrDimensions.Space1,
                    bottom = VlrDimensions.Space3,
                ),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VlrIconButton(
                contentDescription = "검색 화면 닫기",
                onClick = onBack,
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_arrow_back),
                        contentDescription = null,
                    )
                },
            )
            VlrSearchField(
                value = query,
                onValueChange = onQueryChange,
                label = "검색어",
                focusRequester = focusRequester,
                onSearch = { if (canSubmit) onSubmit() },
                modifier = Modifier.weight(1f),
            )
            VlrButton(
                text = "검색",
                onClick = onSubmit,
                variant = VlrButtonVariant.Text,
                enabled = canSubmit,
            )
        }
        HorizontalDivider(
            thickness = VlrDimensions.OutlineWidth,
            color = VlrTheme.colors.line,
        )
    }
}

@Composable
private fun SearchInitialState(modifier: Modifier = Modifier) {
    StatePanel(
        title = "검색어를 입력해 주세요",
        body = "팀, 선수, 대회, 시리즈를 검색할 수 있습니다.",
        icon = Res.drawable.ic_search,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun SearchLoadingState(query: String, modifier: Modifier = Modifier) {
    StatePanel(
        title = "$query 검색 중…",
        body = "잠시만 기다려 주세요.",
        icon = Res.drawable.ic_progress_activity,
        modifier = modifier
            .fillMaxSize()
            .testTag(SEARCH_LOADING_TAG)
            .semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    )
}

@Composable
private fun SearchEmptyState(query: String, modifier: Modifier = Modifier) {
    StatePanel(
        title = "${query}에 대한 검색 결과가 없어요.",
        body = "검색어를 바꿔 다시 시도해 주세요.",
        icon = Res.drawable.ic_search,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun SearchErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatePanel(
        title = "검색 결과를 불러오지 못했습니다.",
        body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
        actionLabel = "다시 시도",
        onAction = onRetry,
        actionTestTag = SEARCH_RETRY_TAG,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun SearchResults(
    query: String,
    items: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedItems = orderedSearchResults(items)
    val typeLabelWidth = rememberSearchTypeLabelWidth()
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(VlrTheme.colors.canvas),
    ) {
        item(key = SEARCH_RESULTS_HEADER_KEY) {
            SectionHeader(
                title = "‘$query’ 검색 결과",
                count = orderedItems.size.toString(),
                modifier = Modifier
                    .background(VlrTheme.colors.surface)
                    .testTag(SEARCH_RESULTS_SECTION_TAG),
            )
        }
        itemsIndexed(
            items = orderedItems,
            key = { index, result -> searchResultListKey(result, index) },
        ) { _, result ->
            SearchResultRow(result, typeLabelWidth, onClick = { onResultClick(result) })
        }
    }
}

/**
 * Search result row (`DESIGN.md` 8.2): a text type label column, title and meta, and a chevron.
 * It has no image and the whole row opens the detail.
 */
@Composable
private fun SearchResultRow(
    result: SearchResult,
    typeLabelWidth: Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .rowDivider(VlrTheme.colors.line)
            .testTag(searchRowTag(result))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space1,
                top = VlrDimensions.Space2,
                bottom = VlrDimensions.Space2,
            ),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = result.typeLabel,
            modifier = Modifier.width(typeLabelWidth),
            style = SearchTypeLabelStyle,
            color = VlrTheme.colors.muted,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = result.name,
                style = VlrTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = VlrTheme.colors.ink,
            )
            result.metadata?.takeIf(String::isNotBlank)?.let { metadata ->
                Text(
                    text = metadata,
                    style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                    color = VlrTheme.colors.muted,
                )
            }
        }
        Box(
            modifier = Modifier.size(VlrDimensions.MinimumTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = VlrTheme.colors.muted,
            )
        }
    }
}

private val SearchTypeLabelStyle: TextStyle
    @Composable get() = VlrTheme.typography.label.copy(fontWeight = FontWeight.Bold)

/**
 * The type column is 32dp, or the widest label (`시리즈`) at the current font scale, so labels stay
 * on one line and titles of every row start at the same x.
 */
@Composable
private fun rememberSearchTypeLabelWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = SearchTypeLabelStyle
    val density = LocalDensity.current
    return remember(measurer, style, density) {
        val widest = SearchTypeLabels.maxOf { label -> measurer.measure(label, style, maxLines = 1).size.width }
        maxOf(SearchTypeLabelMinWidth, with(density) { widest.toDp() })
    }
}

internal val SearchTypeLabelMinWidth = 32.dp

private val SearchTypeLabels = listOf("팀", "선수", "대회", "시리즈")

internal val SearchResult.typeLabel: String
    get() = when (this) {
        is SeriesSearchResult -> "시리즈"
        is EventSearchResult -> "대회"
        is TeamSearchResult -> "팀"
        is PlayerSearchResult -> "선수"
    }

internal fun orderedSearchResults(items: List<SearchResult>): List<SearchResult> =
    items.sortedBy(SearchResult::searchTypeOrder)

internal fun searchResultListKey(result: SearchResult, index: Int): String =
    "${result.stableListKey}:$index"

private val SearchResult.searchTypeOrder: Int
    get() = when (this) {
        is SeriesSearchResult -> 0
        is EventSearchResult -> 1
        is TeamSearchResult -> 2
        is PlayerSearchResult -> 3
    }

private val SearchResult.stableListKey: String
    get() = "$typeKey:$id"

private val SearchResult.typeKey: String
    get() = when (this) {
        is SeriesSearchResult -> "series"
        is EventSearchResult -> "event"
        is TeamSearchResult -> "team"
        is PlayerSearchResult -> "player"
    }
