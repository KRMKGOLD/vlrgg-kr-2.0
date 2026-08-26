package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.onFailure
import kr.co.cotton.vlrgg_mobile.domain.onSuccess
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatsAvailability
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository

@AssistedInject
class EventDetailViewModel(
    private val eventRepository: EventRepository,
    @Assisted private val eventId: String,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val initialTab = EventDetailTab.fromSavedStateId(savedStateHandle[SELECTED_TAB_KEY])
    private val _uiState = MutableStateFlow(EventDetailUiState(selectedTab = initialTab))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private val loadedTabs = mutableSetOf<EventDetailTab>()

    init {
        loadIdentity()
    }

    fun selectTab(tab: EventDetailTab) {
        if (_uiState.value.selectedTab == tab) return

        savedStateHandle[SELECTED_TAB_KEY] = tab.savedStateId
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        if (_uiState.value.identity is EventIdentityContentState.Content) {
            ensureTabLoaded(tab)
        }
    }

    fun retryIdentity() {
        if (_uiState.value.identity != EventIdentityContentState.Error) return

        _uiState.value = _uiState.value.copy(identity = EventIdentityContentState.Loading)
        loadIdentity()
    }

    fun retrySelectedTab() {
        when (_uiState.value.selectedTab) {
            EventDetailTab.MATCHES -> if (_uiState.value.matches == EventMatchesContentState.Error) {
                _uiState.value = _uiState.value.copy(matches = EventMatchesContentState.Loading)
                loadMatches()
            }

            EventDetailTab.NEWS -> if (_uiState.value.news == EventNewsContentState.Error) {
                _uiState.value = _uiState.value.copy(news = EventNewsContentState.Loading)
                loadNews()
            }

            EventDetailTab.STATS -> if (_uiState.value.stats == EventStatsContentState.Error) {
                _uiState.value = _uiState.value.copy(stats = EventStatsContentState.Loading)
                loadStats()
            }
        }
    }

    private fun loadIdentity() = viewModelScope.launch {
        eventRepository.getEventDetail(eventId)
            .onSuccess { event ->
                _uiState.value = _uiState.value.copy(
                    identity = EventIdentityContentState.Content(event),
                )
                ensureTabLoaded(_uiState.value.selectedTab)
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(identity = EventIdentityContentState.Error)
            }
    }

    private fun ensureTabLoaded(tab: EventDetailTab) {
        if (!loadedTabs.add(tab)) return

        when (tab) {
            EventDetailTab.MATCHES -> loadMatches()
            EventDetailTab.NEWS -> loadNews()
            EventDetailTab.STATS -> loadStats()
        }
    }

    private fun loadMatches() = viewModelScope.launch {
        eventRepository.getEventMatches(eventId)
            .onSuccess { matches ->
                _uiState.value = _uiState.value.copy(
                    matches = if (matches.isEmpty()) {
                        EventMatchesContentState.Empty
                    } else {
                        EventMatchesContentState.Content(matches)
                    },
                )
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(matches = EventMatchesContentState.Error)
            }
    }

    private fun loadNews() = viewModelScope.launch {
        eventRepository.getEventNews(eventId)
            .onSuccess { news ->
                _uiState.value = _uiState.value.copy(
                    news = if (news.isEmpty()) {
                        EventNewsContentState.Empty
                    } else {
                        EventNewsContentState.Content(news)
                    },
                )
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(news = EventNewsContentState.Error)
            }
    }

    private fun loadStats() = viewModelScope.launch {
        eventRepository.getEventStats(eventId)
            .onSuccess { stats ->
                _uiState.value = _uiState.value.copy(
                    stats = if (
                        stats.availability == EventStatsAvailability.NOT_AVAILABLE ||
                        stats.players.isEmpty()
                    ) {
                        EventStatsContentState.Empty
                    } else {
                        EventStatsContentState.Content(stats)
                    },
                )
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(stats = EventStatsContentState.Error)
            }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted eventId: String,
            @Assisted savedStateHandle: SavedStateHandle,
        ): EventDetailViewModel
    }

    private companion object {
        const val SELECTED_TAB_KEY = "event-detail-selected-tab"
    }
}
