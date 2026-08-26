package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventPlayerStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatsAvailability
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {

    @Test
    fun identitySuccessLoadsMatchesAsDefaultWithoutEagerlyLoadingOtherTabs() = runViewModelTest {
        val repository = FakeEventRepository()
        val viewModel = EventDetailViewModel(repository, EVENT_ID, SavedStateHandle())

        assertEquals(EventDetailUiState(), viewModel.uiState.value)
        assertEquals(emptyList(), repository.requests)

        advanceUntilIdle()

        assertEquals(listOf("identity", "matches"), repository.requests)
        assertEquals(EventDetailTab.MATCHES, viewModel.uiState.value.selectedTab)
        assertEquals(EventIdentityContentState.Content(eventDetail), viewModel.uiState.value.identity)
        assertEquals(EventMatchesContentState.Content(listOf(match)), viewModel.uiState.value.matches)
        assertEquals(EventNewsContentState.Loading, viewModel.uiState.value.news)
        assertEquals(EventStatsContentState.Loading, viewModel.uiState.value.stats)
    }

    @Test
    fun identityFailureIsWholeScreenErrorAndRetryDoesNotStartTabsUntilIdentitySucceeds() = runViewModelTest {
        val repository = FakeEventRepository(
            identityResults = ArrayDeque(
                listOf(
                    AppResult.Failure,
                    AppResult.Success(eventDetail),
                ),
            ),
        )
        val viewModel = EventDetailViewModel(repository, EVENT_ID, SavedStateHandle())
        advanceUntilIdle()

        assertEquals(EventIdentityContentState.Error, viewModel.uiState.value.identity)
        assertEquals(listOf("identity"), repository.requests)

        viewModel.retryIdentity()
        advanceUntilIdle()

        assertEquals(EventIdentityContentState.Content(eventDetail), viewModel.uiState.value.identity)
        assertEquals(listOf("identity", "identity", "matches"), repository.requests)
    }

    @Test
    fun tabsLoadLazilyOnceAndKeepIndependentSuccessfulData() = runViewModelTest {
        val repository = FakeEventRepository()
        val viewModel = EventDetailViewModel(repository, EVENT_ID, SavedStateHandle())
        advanceUntilIdle()

        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.STATS)
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()

        assertEquals(
            listOf("identity", "matches", "news", "stats"),
            repository.requests,
        )
        assertEquals(EventMatchesContentState.Content(listOf(match)), viewModel.uiState.value.matches)
        assertEquals(EventNewsContentState.Content(listOf(news)), viewModel.uiState.value.news)
        assertEquals(EventStatsContentState.Content(stats), viewModel.uiState.value.stats)
    }

    @Test
    fun tabFailureIsLocalAndRetryOnlyReloadsFailedTab() = runViewModelTest {
        val repository = FakeEventRepository(
            newsResults = ArrayDeque(
                listOf(
                    AppResult.Failure,
                    AppResult.Success(listOf(news)),
                ),
            ),
        )
        val viewModel = EventDetailViewModel(repository, EVENT_ID, SavedStateHandle())
        advanceUntilIdle()

        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()

        assertEquals(EventIdentityContentState.Content(eventDetail), viewModel.uiState.value.identity)
        assertEquals(EventMatchesContentState.Content(listOf(match)), viewModel.uiState.value.matches)
        assertEquals(EventNewsContentState.Error, viewModel.uiState.value.news)

        viewModel.retrySelectedTab()
        advanceUntilIdle()

        assertEquals(EventNewsContentState.Content(listOf(news)), viewModel.uiState.value.news)
        assertEquals(2, repository.requests.count { it == "news" })
        assertEquals(1, repository.requests.count { it == "matches" })
    }

    @Test
    fun emptyAndNotAvailableRemainDistinctFromFailure() = runViewModelTest {
        val repository = FakeEventRepository(
            matchesResults = ArrayDeque(listOf(AppResult.Success(emptyList()))),
            newsResults = ArrayDeque(listOf(AppResult.Success(emptyList()))),
            statsResults = ArrayDeque(
                listOf(
                    AppResult.Success(
                        EventStats(
                            availability = EventStatsAvailability.NOT_AVAILABLE,
                            players = emptyList(),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = EventDetailViewModel(repository, EVENT_ID, SavedStateHandle())
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.STATS)
        advanceUntilIdle()

        assertEquals(EventMatchesContentState.Empty, viewModel.uiState.value.matches)
        assertEquals(EventNewsContentState.Empty, viewModel.uiState.value.news)
        assertEquals(EventStatsContentState.Empty, viewModel.uiState.value.stats)
    }

    @Test
    fun selectedTabRestoresFromSavedStateAndOnlyRestoredTabLoadsAfterIdentity() = runViewModelTest {
        val savedStateHandle = SavedStateHandle(
            mapOf("event-detail-selected-tab" to EventDetailTab.STATS.savedStateId),
        )
        val repository = FakeEventRepository()
        val viewModel = EventDetailViewModel(repository, EVENT_ID, savedStateHandle)
        advanceUntilIdle()

        assertEquals(EventDetailTab.STATS, viewModel.uiState.value.selectedTab)
        assertEquals(listOf("identity", "stats"), repository.requests)

        viewModel.selectTab(EventDetailTab.NEWS)

        assertEquals(EventDetailTab.NEWS.savedStateId, savedStateHandle["event-detail-selected-tab"])
    }

    private fun runViewModelTest(
        testBody: suspend TestScope.() -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeEventRepository(
        private val identityResults: ArrayDeque<AppResult<EventDetail>> = ArrayDeque(
            listOf(AppResult.Success(eventDetail)),
        ),
        private val matchesResults: ArrayDeque<AppResult<List<MatchSummary>>> = ArrayDeque(
            listOf(AppResult.Success(listOf(match))),
        ),
        private val newsResults: ArrayDeque<AppResult<List<NewsSummary>>> = ArrayDeque(
            listOf(AppResult.Success(listOf(news))),
        ),
        private val statsResults: ArrayDeque<AppResult<EventStats>> = ArrayDeque(
            listOf(AppResult.Success(stats)),
        ),
    ) : EventRepository {
        val requests = mutableListOf<String>()

        override suspend fun getEvents() = error("Event list is not used in detail tests")

        override suspend fun getEventDetail(eventId: String): AppResult<EventDetail> {
            assertEquals(EVENT_ID, eventId)
            requests += "identity"
            return identityResults.removeFirst()
        }

        override suspend fun getEventMatches(eventId: String): AppResult<List<MatchSummary>> {
            assertEquals(EVENT_ID, eventId)
            requests += "matches"
            return matchesResults.removeFirst()
        }

        override suspend fun getEventNews(eventId: String): AppResult<List<NewsSummary>> {
            assertEquals(EVENT_ID, eventId)
            requests += "news"
            return newsResults.removeFirst()
        }

        override suspend fun getEventStats(eventId: String): AppResult<EventStats> {
            assertEquals(EVENT_ID, eventId)
            requests += "stats"
            return statsResults.removeFirst()
        }
    }

    private companion object {
        const val EVENT_ID = "100"

        val eventDetail = EventDetail(
            id = EVENT_ID,
            name = "Masters Seoul",
            status = null,
            dateLabel = "Aug 20—Sep 4",
            location = "Seoul",
            series = "VCT 2026",
            description = "International event",
            imageUrl = null,
        )
        val match = MatchSummary(
            id = "match-1",
            status = MatchStatus.UPCOMING,
            timeLabel = "18:00",
            relativeTimeLabel = "IN 2 HOURS",
            homeTeam = MatchTeam("Alpha", id = null),
            awayTeam = MatchTeam("Beta", id = null),
            homeScore = null,
            awayScore = null,
            event = MatchEvent("Masters Seoul", series = "Playoffs", id = null),
        )
        val news = NewsSummary(
            articleId = "101",
            slug = "masters-seoul",
            title = "Masters Seoul begins",
            author = null,
            publishedAt = "2026-08-25",
        )
        val stats = EventStats(
            availability = EventStatsAvailability.AVAILABLE,
            players = listOf(
                EventPlayerStats(
                    playerId = "player-1",
                    playerName = "Meteor",
                    teamAbbreviation = "GEN",
                    roundsPlayed = 120,
                    rating = 1.2,
                    averageCombatScore = 240,
                    killDeathRatio = 1.3,
                    averageDamagePerRound = 155.5,
                    killAssistSurvivedTradedPercentage = 78.0,
                ),
            ),
        )
    }
}
