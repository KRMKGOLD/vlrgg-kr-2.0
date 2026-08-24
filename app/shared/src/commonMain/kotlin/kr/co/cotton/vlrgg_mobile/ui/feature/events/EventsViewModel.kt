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

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class EventsViewModel(
    private val eventRepository: EventRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private var requestJob: Job? = null

    init {
        requestEvents()
    }

    fun retry() {
        if (_uiState.value.contentState != EventsContentState.Error) return

        _uiState.value = EventsUiState()
        requestEvents()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return

        requestJob?.cancel()
        _uiState.value = EventsUiState(isRefreshing = true)
        requestEvents()
    }

    private fun requestEvents() {
        requestJob = viewModelScope.launch {
            when (val result = eventRepository.getEvents()) {
                is AppResult.Success -> _uiState.value = EventsUiState(
                    contentState = result.data.toContentState(),
                )

                AppResult.Failure -> _uiState.value = EventsUiState(
                    contentState = EventsContentState.Error,
                )
            }
        }
    }
}

private fun EventList.toContentState(): EventsContentState =
    if (ongoing.isEmpty() && upcoming.isEmpty() && completedOrPaused.isEmpty()) {
        EventsContentState.Empty
    } else {
        EventsContentState.Content(this)
    }
