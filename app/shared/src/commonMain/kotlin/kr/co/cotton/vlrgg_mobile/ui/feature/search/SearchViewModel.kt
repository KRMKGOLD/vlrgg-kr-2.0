package kr.co.cotton.vlrgg_mobile.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.repository.SearchRepository
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var requestJob: Job? = null
    private var requestGeneration = 0
    private var submittedQuery: String? = null

    fun onQueryChange(query: String) {
        if (query.isEmpty()) {
            clear()
            return
        }

        val limitedQuery = query.take(MAX_SEARCH_QUERY_LENGTH)
        if (limitedQuery == _uiState.value.query) return

        requestJob?.cancel()
        requestGeneration += 1
        submittedQuery = null
        _uiState.value = SearchUiState(query = limitedQuery)
    }

    fun submit() {
        submit(force = false)
    }

    fun retry() {
        if (requestJob?.isActive == true) return
        if (isBusyCooldown()) return
        if (_uiState.value.contentState == SearchContentState.Error) {
            submit(force = true)
        }
    }

    fun retryBusy() {
        if (requestJob?.isActive == true) return
        val busy = _uiState.value.busyRetry ?: return
        if (!busy.canRetry() || busy.operationId != operationId(_uiState.value.query.trim())) return
        submit(force = true)
    }

    fun dismissBusy() { _uiState.value = _uiState.value.copy(busyRetry = _uiState.value.busyRetry?.dismiss()) }

    fun clear() {
        requestJob?.cancel()
        requestGeneration += 1
        submittedQuery = null
        _uiState.value = SearchUiState()
    }

    private fun submit(force: Boolean) {
        val query = _uiState.value.query.trim()
        if (!isSearchQueryValid(query)) return
        if (isBusyCooldown(operationId(query))) return
        if (!force && submittedQuery == query) return

        requestJob?.cancel()
        val generation = ++requestGeneration
        submittedQuery = query
        _uiState.value = SearchUiState(query = query, contentState = SearchContentState.Loading)
        requestJob = viewModelScope.launch {
            when (val result = searchRepository.getSearch(query)) {
                is AppResult.Success -> {
                    if (generation != requestGeneration) return@launch
                    _uiState.value = SearchUiState(
                        query = query,
                        contentState = result.data.items.toContentState(),
                    )
                }

                AppResult.Failure -> {
                    if (generation != requestGeneration) return@launch
                    _uiState.value = SearchUiState(query = query, contentState = SearchContentState.Error)
                }

                is AppResult.Busy -> {
                    if (generation != requestGeneration) return@launch
                    submittedQuery = null
                    _uiState.value = SearchUiState(
                        query = query,
                        contentState = SearchContentState.Error,
                        busyRetry = busyRetryStateFactory.create(operationId(query), result.retryDelay),
                    )
                }
            }
        }
    }

    private fun isBusyCooldown(operationId: String = operationId(_uiState.value.query.trim())): Boolean =
        _uiState.value.busyRetry?.takeIf { it.operationId == operationId }?.canRetry() == false

    private fun operationId(query: String): String = "search:$query"
}

private fun List<kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult>.toContentState(): SearchContentState =
    if (isEmpty()) SearchContentState.Empty else SearchContentState.Populated(this)
