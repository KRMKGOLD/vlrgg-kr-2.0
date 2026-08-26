package kr.co.cotton.vlrgg_mobile.ui.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonSize
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrSearchField
import kr.co.cotton.vlrgg_mobile.ui.component.VlrSearchFieldVariant
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_search

internal const val SEARCH_LOADING_TAG = "search-loading"
internal const val SEARCH_RETRY_TAG = "search-retry"

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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VlrTheme.colors.surface,
        topBar = {
            SearchTopBar(
                query = uiState.query,
                canSubmit = uiState.canSubmit && uiState.contentState != SearchContentState.Loading,
                isLoading = uiState.contentState == SearchContentState.Loading,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onBack = onBack,
            )
        },
    ) { contentPadding ->
        when (val contentState = uiState.contentState) {
            SearchContentState.Initial -> SearchInitialState(Modifier.padding(contentPadding))
            SearchContentState.Loading -> SearchLoadingState(uiState.query, Modifier.padding(contentPadding))
            SearchContentState.Empty -> SearchEmptyState(uiState.query, Modifier.padding(contentPadding))
            SearchContentState.Error -> SearchErrorState(onRetry, Modifier.padding(contentPadding))
            is SearchContentState.Populated -> SearchResults(
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
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VlrDimensions.Space2, vertical = VlrDimensions.Space2),
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
                variant = VlrSearchFieldVariant.Compact,
                isLoading = isLoading,
                label = "검색어",
                onSearch = { if (canSubmit) onSubmit() },
                modifier = Modifier.weight(1f),
            )
            VlrButton(
                text = "검색",
                onClick = onSubmit,
                variant = VlrButtonVariant.Text,
                size = VlrButtonSize.Compact,
                enabled = canSubmit,
            )
        }
        HorizontalDivider(
            thickness = VlrDimensions.OutlineWidth,
            color = VlrTheme.colors.outline,
        )
    }
}

@Composable
private fun SearchInitialState(modifier: Modifier = Modifier) {
    SearchMessage(
        title = "검색어를 입력해 주세요",
        body = "팀, 선수, 대회, 시리즈를 검색할 수 있습니다.",
        modifier = modifier,
        icon = {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = VlrTheme.colors.textSecondary,
            )
        },
    )
}

@Composable
private fun SearchLoadingState(query: String, modifier: Modifier = Modifier) {
    SearchMessage(
        title = "$query 검색 중…",
        body = "잠시만 기다려 주세요.",
        modifier = modifier.testTag(SEARCH_LOADING_TAG),
    )
}

@Composable
private fun SearchEmptyState(query: String, modifier: Modifier = Modifier) {
    SearchMessage(
        title = "${query}에 대한 검색 결과가 없어요.",
        body = "검색어를 바꿔 다시 시도해 주세요.",
        modifier = modifier,
    )
}

@Composable
private fun SearchErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(VlrDimensions.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "검색 결과를 불러오지 못했습니다.",
            style = VlrTheme.typography.sectionTitle,
            color = VlrTheme.colors.textPrimary,
        )
        Spacer(Modifier.size(VlrDimensions.Space2))
        Text(
            text = "네트워크 상태를 확인하고 다시 시도해 주세요.",
            color = VlrTheme.colors.textSecondary,
            style = VlrTheme.typography.body,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(VlrDimensions.Space4))
        VlrButton(
            text = "다시 시도",
            onClick = onRetry,
            variant = VlrButtonVariant.Secondary,
            modifier = Modifier.testTag(SEARCH_RETRY_TAG),
        )
    }
}

@Composable
private fun SearchMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(VlrDimensions.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.size(VlrDimensions.Space3))
        Text(
            text = title,
            style = VlrTheme.typography.sectionTitle,
            color = VlrTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(VlrDimensions.Space2))
        Text(
            text = body,
            style = VlrTheme.typography.body,
            color = VlrTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchResults(
    items: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(
            items = orderedSearchResults(items),
            key = SearchResult::stableListKey,
        ) { result ->
            SearchResultRow(result, onClick = { onResultClick(result) })
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = VlrDimensions.MinimumTouchTarget)
                .testTag(searchRowTag(result))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    style = VlrTheme.typography.body,
                    color = VlrTheme.colors.textPrimary,
                )
                result.metadata?.let { metadata ->
                    Text(
                        text = metadata,
                        style = VlrTheme.typography.labelSmall,
                        color = VlrTheme.colors.textSecondary,
                    )
                }
            }
            Text(
                text = result.typeLabel,
                style = VlrTheme.typography.labelSmall,
                color = VlrTheme.colors.textSecondary,
            )
        }
        HorizontalDivider(
            thickness = VlrDimensions.OutlineWidth,
            color = VlrTheme.colors.outline,
        )
    }
}

private val SearchResult.typeLabel: String
    get() = when (this) {
        is SeriesSearchResult -> "Series"
        is EventSearchResult -> "Event"
        is TeamSearchResult -> "Team"
        is PlayerSearchResult -> "Player"
    }

internal fun orderedSearchResults(items: List<SearchResult>): List<SearchResult> =
    items.sortedBy(SearchResult::searchTypeOrder)

internal fun searchResultListKey(result: SearchResult): String = result.stableListKey

private val SearchResult.searchTypeOrder: Int
    get() = when (this) {
        is SeriesSearchResult -> 0
        is EventSearchResult -> 1
        is TeamSearchResult -> 2
        is PlayerSearchResult -> 3
    }

private val SearchResult.stableListKey: String
    get() = "$typeLabel:$id"
