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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatsAvailability
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

@AssistedInject
class EventDetailViewModel(
    private val eventRepository: EventRepository,
    @Assisted private val eventId: String,
    @Assisted private val savedStateHandle: SavedStateHandle,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    private val initialTab = EventDetailTab.fromSavedStateId(savedStateHandle[SELECTED_TAB_KEY])
    private val _uiState = MutableStateFlow(EventDetailUiState(selectedTab = initialTab))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private val loadedTabs = mutableSetOf<EventDetailTab>()
    private val tabJobs = mutableMapOf<EventDetailTab, Job>()
    private val tabGenerations = mutableMapOf<EventDetailTab, Int>()
    private var identityJob: Job? = null
    private var identityGeneration = 0

    init { loadIdentity() }

    fun selectTab(tab: EventDetailTab) {
        val previous = _uiState.value.selectedTab
        if (previous == tab) return
        cancelTabIfInFlight(previous)
        savedStateHandle[SELECTED_TAB_KEY] = tab.savedStateId
        _uiState.value = _uiState.value.copy(selectedTab = tab, busyRetry = null)
        if (_uiState.value.identity is EventIdentityContentState.Content) ensureTabLoaded(tab)
    }

    fun retryIdentity() {
        if (identityJob?.isActive == true) return
        if (isBusyCooldown(IDENTITY_OPERATION) || _uiState.value.identity != EventIdentityContentState.Error) return
        _uiState.value = _uiState.value.copy(identity = EventIdentityContentState.Loading, busyRetry = null)
        loadIdentity()
    }

    fun retrySelectedTab() {
        val tab = _uiState.value.selectedTab
        if (tabJobs[tab]?.isActive == true) return
        if (isBusyCooldown(operationId(tab))) return
        when (tab) {
            EventDetailTab.MATCHES -> if (_uiState.value.matches == EventMatchesContentState.Error) {
                _uiState.value = _uiState.value.copy(matches = EventMatchesContentState.Loading, busyRetry = null); loadMatches()
            }
            EventDetailTab.NEWS -> if (_uiState.value.news == EventNewsContentState.Error) {
                _uiState.value = _uiState.value.copy(news = EventNewsContentState.Loading, busyRetry = null); loadNews()
            }
            EventDetailTab.STATS -> if (_uiState.value.stats == EventStatsContentState.Error) {
                _uiState.value = _uiState.value.copy(stats = EventStatsContentState.Loading, busyRetry = null); loadStats()
            }
        }
    }

    fun retryBusy() {
        val busy = _uiState.value.busyRetry ?: return
        if (!busy.canRetry()) return
        when (busy.operationId) {
            IDENTITY_OPERATION -> {
                if (identityJob?.isActive == true) return
                _uiState.value = _uiState.value.copy(identity = EventIdentityContentState.Loading, busyRetry = null)
                loadIdentity()
            }
            operationId(EventDetailTab.MATCHES) -> retryTabBusy(EventDetailTab.MATCHES) { loadMatches() }
            operationId(EventDetailTab.NEWS) -> retryTabBusy(EventDetailTab.NEWS) { loadNews() }
            operationId(EventDetailTab.STATS) -> retryTabBusy(EventDetailTab.STATS) { loadStats() }
        }
    }

    fun dismissBusy() { _uiState.value = _uiState.value.copy(busyRetry = _uiState.value.busyRetry?.dismiss()) }

    private fun retryTabBusy(tab: EventDetailTab, load: () -> Unit) {
        if (_uiState.value.selectedTab != tab) return
        if (tabJobs[tab]?.isActive == true) return
        _uiState.value = when (tab) {
            EventDetailTab.MATCHES -> _uiState.value.copy(matches = EventMatchesContentState.Loading)
            EventDetailTab.NEWS -> _uiState.value.copy(news = EventNewsContentState.Loading)
            EventDetailTab.STATS -> _uiState.value.copy(stats = EventStatsContentState.Loading)
        }.copy(busyRetry = null)
        load()
    }

    private fun loadIdentity() {
        identityJob?.cancel()
        val generation = ++identityGeneration
        identityJob = viewModelScope.launch {
            when (val result = eventRepository.getEventDetail(eventId)) {
                is AppResult.Success -> if (generation == identityGeneration) {
                    _uiState.value = _uiState.value.copy(identity = EventIdentityContentState.Content(result.data))
                    ensureTabLoaded(_uiState.value.selectedTab)
                }
                AppResult.Failure -> if (generation == identityGeneration) _uiState.value = _uiState.value.copy(identity = EventIdentityContentState.Error)
                is AppResult.Busy -> if (generation == identityGeneration) _uiState.value = _uiState.value.copy(
                    identity = EventIdentityContentState.Error,
                    busyRetry = busyRetryStateFactory.create(IDENTITY_OPERATION, result.retryDelay),
                )
            }
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

    private fun loadMatches() = launchTab(EventDetailTab.MATCHES) { generation ->
        when (val result = eventRepository.getEventMatches(eventId)) {
            is AppResult.Success -> ifCurrent(EventDetailTab.MATCHES, generation) { _uiState.value = _uiState.value.copy(matches = result.data.toMatchesContent()) }
            AppResult.Failure -> ifCurrent(EventDetailTab.MATCHES, generation) { _uiState.value = _uiState.value.copy(matches = EventMatchesContentState.Error) }
            is AppResult.Busy -> onTabBusy(EventDetailTab.MATCHES, generation, result.retryDelay)
        }
    }

    private fun loadNews() = launchTab(EventDetailTab.NEWS) { generation ->
        when (val result = eventRepository.getEventNews(eventId)) {
            is AppResult.Success -> ifCurrent(EventDetailTab.NEWS, generation) { _uiState.value = _uiState.value.copy(news = result.data.toNewsContent()) }
            AppResult.Failure -> ifCurrent(EventDetailTab.NEWS, generation) { _uiState.value = _uiState.value.copy(news = EventNewsContentState.Error) }
            is AppResult.Busy -> onTabBusy(EventDetailTab.NEWS, generation, result.retryDelay)
        }
    }

    private fun loadStats() = launchTab(EventDetailTab.STATS) { generation ->
        when (val result = eventRepository.getEventStats(eventId)) {
            is AppResult.Success -> ifCurrent(EventDetailTab.STATS, generation) { _uiState.value = _uiState.value.copy(stats = result.data.toStatsContent()) }
            AppResult.Failure -> ifCurrent(EventDetailTab.STATS, generation) { _uiState.value = _uiState.value.copy(stats = EventStatsContentState.Error) }
            is AppResult.Busy -> onTabBusy(EventDetailTab.STATS, generation, result.retryDelay)
        }
    }

    private fun launchTab(tab: EventDetailTab, request: suspend (Int) -> Unit) {
        tabJobs[tab]?.cancel()
        val generation = (tabGenerations[tab] ?: 0) + 1
        tabGenerations[tab] = generation
        tabJobs[tab] = viewModelScope.launch { request(generation) }
    }

    private fun ifCurrent(tab: EventDetailTab, generation: Int, action: () -> Unit) {
        if (tabGenerations[tab] == generation) action()
    }

    private fun onTabBusy(tab: EventDetailTab, generation: Int, retryDelay: kotlin.time.Duration) = ifCurrent(tab, generation) {
        loadedTabs.remove(tab)
        _uiState.value = when (tab) {
            EventDetailTab.MATCHES -> _uiState.value.copy(matches = EventMatchesContentState.Error)
            EventDetailTab.NEWS -> _uiState.value.copy(news = EventNewsContentState.Error)
            EventDetailTab.STATS -> _uiState.value.copy(stats = EventStatsContentState.Error)
        }.copy(busyRetry = busyRetryStateFactory.create(operationId(tab), retryDelay))
    }

    private fun cancelTabIfInFlight(tab: EventDetailTab) {
        val job = tabJobs[tab] ?: return
        if (job.isActive) {
            job.cancel()
            tabGenerations[tab] = (tabGenerations[tab] ?: 0) + 1
            loadedTabs.remove(tab)
        }
    }

    private fun isBusyCooldown(operationId: String): Boolean = _uiState.value.busyRetry?.takeIf { it.operationId == operationId }?.canRetry() == false

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted eventId: String, @Assisted savedStateHandle: SavedStateHandle): EventDetailViewModel
    }

    private companion object {
        const val SELECTED_TAB_KEY = "event-detail-selected-tab"
        const val IDENTITY_OPERATION = "event:identity"
        fun operationId(tab: EventDetailTab): String = "event:${tab.savedStateId}"
    }
}

private fun List<kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary>.toMatchesContent() = if (isEmpty()) EventMatchesContentState.Empty else EventMatchesContentState.Content(this)
private fun List<kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary>.toNewsContent() = if (isEmpty()) EventNewsContentState.Empty else EventNewsContentState.Content(this)
private fun kr.co.cotton.vlrgg_mobile.domain.model.events.EventStats.toStatsContent() = if (availability == EventStatsAvailability.NOT_AVAILABLE || players.isEmpty()) EventStatsContentState.Empty else EventStatsContentState.Content(this)
