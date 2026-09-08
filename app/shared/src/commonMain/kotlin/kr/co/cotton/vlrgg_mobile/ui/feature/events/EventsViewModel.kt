package kr.co.cotton.vlrgg_mobile.ui.feature.events

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
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class EventsViewModel(
    private val eventRepository: EventRepository,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private var requestJob: Job? = null
    private var requestGeneration = 0

    init {
        requestEvents()
    }

    fun retry() {
        if (requestJob?.isActive == true) return
        if (isBusyCooldown()) return
        if (_uiState.value.contentState != EventsContentState.Error) return
        _uiState.value = _uiState.value.copy(contentState = EventsContentState.Loading, busyRetry = null)
        requestEvents()
    }

    fun retryBusy() {
        if (requestJob?.isActive == true) return
        val busy = _uiState.value.busyRetry ?: return
        if (busy.operationId != OPERATION_ID || !busy.canRetry()) return
        val isRefresh = _uiState.value.contentState is EventsContentState.Content
        _uiState.value = _uiState.value.copy(busyRetry = null, isRefreshing = isRefresh)
        requestEvents(isRefresh = isRefresh)
    }

    fun dismissBusy() { _uiState.value = _uiState.value.copy(busyRetry = _uiState.value.busyRetry?.dismiss()) }

    fun refresh() {
        if (isBusyCooldown()) return
        if (_uiState.value.isRefreshing) return

        requestJob?.cancel()
        _uiState.value = _uiState.value.copy(isRefreshing = true, busyRetry = null)
        requestEvents(isRefresh = true)
    }

    private fun requestEvents(isRefresh: Boolean = false) {
        val generation = ++requestGeneration
        requestJob = viewModelScope.launch {
            when (val result = eventRepository.getEvents()) {
                is AppResult.Success -> {
                    if (requestGeneration != generation) return@launch
                    _uiState.value = EventsUiState(
                        contentState = result.data.toContentState(),
                    )
                }

                AppResult.Failure -> {
                    if (requestGeneration != generation) return@launch
                    val state = _uiState.value
                    _uiState.value = if (isRefresh && state.contentState is EventsContentState.Content) {
                        state.copy(isRefreshing = false)
                    } else {
                        EventsUiState(contentState = EventsContentState.Error)
                    }
                }

                is AppResult.Busy -> {
                    if (requestGeneration != generation) return@launch
                    val state = _uiState.value
                    _uiState.value = state.copy(
                        contentState = state.contentState.takeUnless { it is EventsContentState.Loading }
                            ?: EventsContentState.Error,
                        isRefreshing = false,
                        busyRetry = busyRetryStateFactory.create(OPERATION_ID, result.retryDelay),
                    )
                }
            }
        }
    }

    private fun isBusyCooldown(): Boolean = _uiState.value.busyRetry
        ?.takeIf { it.operationId == OPERATION_ID }
        ?.canRetry() == false

    private companion object { const val OPERATION_ID = "events" }
}

private fun EventList.toContentState(): EventsContentState =
    if (ongoing.isEmpty() && upcoming.isEmpty() && completedOrPaused.isEmpty()) {
        EventsContentState.Empty
    } else {
        EventsContentState.Content(this)
    }
