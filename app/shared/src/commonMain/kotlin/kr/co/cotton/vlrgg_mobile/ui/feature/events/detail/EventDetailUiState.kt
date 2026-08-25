package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStats
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary

enum class EventDetailTab(
    val savedStateId: String,
) {
    MATCHES("matches"),
    NEWS("news"),
    STATS("stats"),
    ;

    companion object {
        internal fun fromSavedStateId(savedStateId: String?): EventDetailTab =
            entries.firstOrNull { it.savedStateId == savedStateId } ?: MATCHES
    }
}

sealed interface EventIdentityContentState {
    data object Loading : EventIdentityContentState
    data class Content(val event: EventDetail) : EventIdentityContentState
    data object Error : EventIdentityContentState
}

sealed interface EventMatchesContentState {
    data object Loading : EventMatchesContentState
    data object Empty : EventMatchesContentState
    data class Content(val matches: List<MatchSummary>) : EventMatchesContentState
    data object Error : EventMatchesContentState
}

sealed interface EventNewsContentState {
    data object Loading : EventNewsContentState
    data object Empty : EventNewsContentState
    data class Content(val news: List<NewsSummary>) : EventNewsContentState
    data object Error : EventNewsContentState
}

sealed interface EventStatsContentState {
    data object Loading : EventStatsContentState
    data object Empty : EventStatsContentState
    data class Content(val stats: EventStats) : EventStatsContentState
    data object Error : EventStatsContentState
}

data class EventDetailUiState(
    val selectedTab: EventDetailTab = EventDetailTab.MATCHES,
    val identity: EventIdentityContentState = EventIdentityContentState.Loading,
    val matches: EventMatchesContentState = EventMatchesContentState.Loading,
    val news: EventNewsContentState = EventNewsContentState.Loading,
    val stats: EventStatsContentState = EventStatsContentState.Loading,
)
