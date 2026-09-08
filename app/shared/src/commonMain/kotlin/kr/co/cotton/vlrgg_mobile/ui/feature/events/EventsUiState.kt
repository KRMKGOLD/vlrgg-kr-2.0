package kr.co.cotton.vlrgg_mobile.ui.feature.events

import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState

sealed interface EventsContentState {
    data object Loading : EventsContentState

    data object Empty : EventsContentState

    data class Content(
        val events: EventList,
    ) : EventsContentState

    data object Error : EventsContentState
}

data class EventsUiState(
    val contentState: EventsContentState = EventsContentState.Loading,
    val isRefreshing: Boolean = false,
    val busyRetry: BusyRetryState? = null,
)
