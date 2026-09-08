package kr.co.cotton.vlrgg_mobile.ui.feature.matches

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchPage
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

class MatchesViewModel @AssistedInject constructor(
    private val matchRepository: MatchRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    constructor(matchRepository: MatchRepository) : this(matchRepository, SavedStateHandle())

    private val initialTab = MatchesTab.fromSavedStateId(
        savedStateHandle[MATCHES_SELECTED_TAB_KEY],
    )
    private val _uiState = MutableStateFlow(MatchesUiState(selectedTab = initialTab))
    val uiState: StateFlow<MatchesUiState> = _uiState.asStateFlow()

    private val upcomingRuntime = FeedRuntime()
    private val resultsRuntime = FeedRuntime()
    private val busyRetries = mutableMapOf<String, BusyRetryState>()

    init {
        savedStateHandle[MATCHES_SELECTED_TAB_KEY] = initialTab.savedStateId
        requestInitial(initialTab)
    }

    fun selectTab(tab: MatchesTab) {
        savedStateHandle[MATCHES_SELECTED_TAB_KEY] = tab.savedStateId
        if (_uiState.value.selectedTab != tab) {
            runtime(_uiState.value.selectedTab).also { runtime ->
                if (runtime.hasActiveRequest()) {
                    runtime.cancelRequests()
                    runtime.initialRequested = false
                }
            }
            _uiState.value = _uiState.value.copy(selectedTab = tab, busyRetry = visibleBusyRetry(tab))
        }

        if (!runtime(tab).initialRequested) {
            requestInitial(tab)
        }
    }

    fun retryInitial(tab: MatchesTab = _uiState.value.selectedTab) {
        if (runtime(tab).hasActiveRequest()) return
        if (isBusyCooldown(operationId(tab, FIRST_PAGE))) return
        if (feedState(tab).contentState != MatchesFeedContentState.Error) return

        val runtime = runtime(tab)
        runtime.cancelRequests()
        runtime.resetForFirstPage()
        clearBusyRetry(operationId(tab, FIRST_PAGE))
        updateFeed(tab) { MatchesFeedUiState() }
        requestInitial(tab)
    }

    fun retryBusy() {
        val busy = _uiState.value.busyRetry ?: return
        if (!busy.canRetry()) return
        val operation = parseOperation(busy.operationId) ?: return
        if (_uiState.value.selectedTab != operation.tab) return
        if (runtime(operation.tab).hasActiveRequest()) return
        if (busyRetries[busy.operationId] != busy) return
        clearBusyRetry(busy.operationId)
        if (operation.page == FIRST_PAGE) {
            val runtime = runtime(operation.tab)
            val state = feedState(operation.tab)
            runtime.resetForFirstPage()
            if (state.contentState == MatchesFeedContentState.Error) {
                updateFeed(operation.tab) { MatchesFeedUiState() }
            } else {
                runtime.refreshContent = state.contentState
                updateFeed(operation.tab) { it.copy(isRefreshing = true) }
            }
            requestFirstPage(operation.tab)
        } else {
            loadMore(operation.tab)
        }
    }

    fun dismissBusy() {
        val busy = _uiState.value.busyRetry ?: return
        val operation = parseOperation(busy.operationId)
        val dismissed = busy.dismiss()
        busyRetries[busy.operationId] = dismissed
        _uiState.value = _uiState.value.copy(busyRetry = dismissed)
        if (operation != null && operation.page != FIRST_PAGE) {
            updateFeed(operation.tab) { it.copy(hasPaginationError = true) }
        }
    }

    fun refresh(tab: MatchesTab = _uiState.value.selectedTab) {
        if (isBusyCooldown(operationId(tab, FIRST_PAGE))) return
        val state = feedState(tab)
        if (state.isRefreshing) return

        val runtime = runtime(tab)
        runtime.refreshContent = state.contentState.takeUnless { it is MatchesFeedContentState.Loading || it is MatchesFeedContentState.Error }
        runtime.cancelRequests()
        runtime.resetForFirstPage()

        clearBusyRetry(operationId(tab, FIRST_PAGE))
        updateFeed(tab) { MatchesFeedUiState(contentState = MatchesFeedContentState.Loading, isRefreshing = true) }
        requestFirstPage(tab)
    }

    fun loadMore(tab: MatchesTab = _uiState.value.selectedTab) {
        val state = feedState(tab)
        val runtime = runtime(tab)
        if (state.contentState !is MatchesFeedContentState.Content) return
        if (state.isRefreshing || state.isLoadingMore || !runtime.canLoadMore) return
        if (runtime.loadMoreJob?.isActive == true) return

        val requestedPage = runtime.currentPage + 1
        if (isBusyCooldown(operationId(tab, requestedPage))) return
        clearBusyRetry(operationId(tab, requestedPage))
        val generation = runtime.generation
        updateFeed(tab) {
            it.copy(
                isLoadingMore = true,
                hasPaginationError = false,
            )
        }

        runtime.loadMoreJob = viewModelScope.launch {
            when (val result = requestPage(tab, requestedPage)) {
                is AppResult.Success -> {
                    if (runtime.generation != generation) return@launch
                    applyPaginationSuccess(tab, requestedPage, result.data)
                }

                AppResult.Failure -> {
                    if (runtime.generation != generation) return@launch
                    runtime.refreshContent = null
                    updateFeed(tab) {
                        it.copy(
                            isLoadingMore = false,
                            hasPaginationError = true,
                        )
                    }
                }

                is AppResult.Busy -> {
                    if (runtime.generation != generation) return@launch
                    updateFeed(tab) { it.copy(isLoadingMore = false) }
                    busyRetries[operationId(tab, requestedPage)] = busyRetryStateFactory.create(
                        operationId(tab, requestedPage), result.retryDelay,
                    )
                    _uiState.value = _uiState.value.copy(busyRetry = visibleBusyRetry())
                }
            }
        }
    }

    fun retryLoadMore(tab: MatchesTab = _uiState.value.selectedTab) {
        if (!feedState(tab).hasPaginationError) return
        if (runtime(tab).hasActiveRequest()) return
        if (isBusyCooldown(operationId(tab, runtime(tab).currentPage + 1))) return
        clearBusyRetry(operationId(tab, runtime(tab).currentPage + 1))
        loadMore(tab)
    }

    private fun requestInitial(tab: MatchesTab) {
        runtime(tab).resetForFirstPage()
        requestFirstPage(tab)
    }

    private fun requestFirstPage(tab: MatchesTab) {
        val runtime = runtime(tab)
        val operationId = operationId(tab, FIRST_PAGE)
        if (isBusyCooldown(operationId)) return
        clearBusyRetry(operationId)
        runtime.initialRequested = true
        val generation = runtime.generation
        runtime.firstPageJob = viewModelScope.launch {
            when (val result = requestPage(tab, FIRST_PAGE)) {
                is AppResult.Success -> {
                    if (runtime.generation != generation) return@launch
                    clearBusyRetry(operationId)
                    applyFirstPageSuccess(tab, result.data)
                }

                AppResult.Failure -> {
                    if (runtime.generation != generation) return@launch
                    clearBusyRetry(operationId)
                    runtime.refreshContent = null
                    updateFeed(tab) {
                        if (it.contentState == MatchesFeedContentState.Loading) {
                            MatchesFeedUiState(contentState = MatchesFeedContentState.Error)
                        } else {
                            it.copy(isRefreshing = false)
                        }
                    }
                }

                is AppResult.Busy -> {
                    if (runtime.generation != generation) return@launch
                    val preservedContent = runtime.refreshContent
                    updateFeed(tab) {
                        MatchesFeedUiState(contentState = preservedContent ?: MatchesFeedContentState.Error)
                    }
                    runtime.refreshContent = null
                    busyRetries[operationId] = busyRetryStateFactory.create(operationId, result.retryDelay)
                    _uiState.value = _uiState.value.copy(busyRetry = visibleBusyRetry())
                }
            }
        }
    }

    private suspend fun requestPage(
        tab: MatchesTab,
        page: Int,
    ): AppResult<MatchPage> = when (tab) {
        MatchesTab.UPCOMING_LIVE -> matchRepository.getUpcomingMatches(page)
        MatchesTab.RESULTS -> matchRepository.getResults(page)
    }

    private fun applyFirstPageSuccess(
        tab: MatchesTab,
        page: MatchPage,
    ) {
        val groups = mergeGroups(emptyList(), page.groups)
        val runtime = runtime(tab)
        runtime.currentPage = FIRST_PAGE
        runtime.canLoadMore = page.groups.hasMatches()
        runtime.refreshContent = null

        updateFeed(tab) {
            MatchesFeedUiState(
                contentState = groups.toContentState(),
            )
        }
    }

    private fun applyPaginationSuccess(
        tab: MatchesTab,
        requestedPage: Int,
        page: MatchPage,
    ) {
        val existingGroups = (feedState(tab).contentState as MatchesFeedContentState.Content).groups
        val mergedGroups = mergeGroups(existingGroups, page.groups)
        val runtime = runtime(tab)
        runtime.currentPage = requestedPage
        runtime.canLoadMore = mergedGroups.matchCount() > existingGroups.matchCount()

        updateFeed(tab) {
            it.copy(
                contentState = MatchesFeedContentState.Content(mergedGroups),
                isLoadingMore = false,
                hasPaginationError = false,
            )
        }
    }

    private fun feedState(tab: MatchesTab): MatchesFeedUiState = when (tab) {
        MatchesTab.UPCOMING_LIVE -> _uiState.value.upcomingLive
        MatchesTab.RESULTS -> _uiState.value.results
    }

    private inline fun updateFeed(
        tab: MatchesTab,
        transform: (MatchesFeedUiState) -> MatchesFeedUiState,
    ) {
        val state = _uiState.value
        _uiState.value = when (tab) {
            MatchesTab.UPCOMING_LIVE -> state.copy(upcomingLive = transform(state.upcomingLive))
            MatchesTab.RESULTS -> state.copy(results = transform(state.results))
        }
    }

    private fun runtime(tab: MatchesTab): FeedRuntime = when (tab) {
        MatchesTab.UPCOMING_LIVE -> upcomingRuntime
        MatchesTab.RESULTS -> resultsRuntime
    }

    private fun isBusyCooldown(operationId: String): Boolean = busyRetries[operationId]?.canRetry() == false

    private fun clearBusyRetry(operationId: String) {
        busyRetries.remove(operationId)
        if (_uiState.value.busyRetry?.operationId == operationId) {
            _uiState.value = _uiState.value.copy(busyRetry = visibleBusyRetry())
        }
    }

    private fun visibleBusyRetry(tab: MatchesTab = _uiState.value.selectedTab): BusyRetryState? =
        busyRetries[operationId(tab, FIRST_PAGE)] ?: busyRetries[operationId(tab, runtime(tab).currentPage + 1)]

    private fun operationId(tab: MatchesTab, page: Int): String = "$OPERATION_PREFIX${tab.savedStateId}:$page"

    private fun parseOperation(operationId: String): MatchOperation? {
        val parts = operationId.removePrefix(OPERATION_PREFIX).split(':')
        if (parts.size != 2) return null
        val tab = MatchesTab.entries.firstOrNull { it.savedStateId == parts[0] } ?: return null
        return parts[1].toIntOrNull()?.let { MatchOperation(tab, it) }
    }

    private data class MatchOperation(val tab: MatchesTab, val page: Int)

    private class FeedRuntime {
        var initialRequested: Boolean = false
        var currentPage: Int = 0
        var canLoadMore: Boolean = true
        var generation: Int = 0
        var firstPageJob: Job? = null
        var loadMoreJob: Job? = null
        var refreshContent: MatchesFeedContentState? = null

        fun hasActiveRequest(): Boolean = firstPageJob?.isActive == true || loadMoreJob?.isActive == true

        fun cancelRequests() {
            firstPageJob?.cancel()
            firstPageJob = null
            loadMoreJob?.cancel()
            loadMoreJob = null
            generation += 1
        }

        fun resetForFirstPage() {
            currentPage = 0
            canLoadMore = true
        }
    }

    @AssistedFactory
    @ViewModelAssistedFactoryKey(MatchesViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): MatchesViewModel =
            create(extras.createSavedStateHandle())

        fun create(@Assisted savedStateHandle: SavedStateHandle): MatchesViewModel
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val OPERATION_PREFIX = "matches:"
    }
}

internal const val MATCHES_SELECTED_TAB_KEY = "matches.selected_tab"

private fun List<MatchDateGroup>.toContentState(): MatchesFeedContentState =
    if (isEmpty()) {
        MatchesFeedContentState.Empty
    } else {
        MatchesFeedContentState.Content(this)
    }

private fun List<MatchDateGroup>.hasMatches(): Boolean = any { group -> group.matches.isNotEmpty() }

private fun List<MatchDateGroup>.matchCount(): Int = sumOf { group -> group.matches.size }

private fun mergeGroups(
    existing: List<MatchDateGroup>,
    incoming: List<MatchDateGroup>,
): List<MatchDateGroup> {
    val seenMatchIds = mutableSetOf<String>()
    val groupIndexes = mutableMapOf<String, Int>()
    val merged = mutableListOf<MatchDateGroup>()

    (existing + incoming).forEach { group ->
        val uniqueMatches = group.matches.filter { match -> seenMatchIds.add(match.id) }
        if (uniqueMatches.isEmpty()) return@forEach

        val groupIndex = groupIndexes[group.dateLabel]
        if (groupIndex == null) {
            groupIndexes[group.dateLabel] = merged.size
            merged += group.copy(matches = uniqueMatches)
        } else {
            val existingGroup = merged[groupIndex]
            merged[groupIndex] = existingGroup.copy(
                matches = existingGroup.matches + uniqueMatches,
            )
        }
    }

    return merged
}
