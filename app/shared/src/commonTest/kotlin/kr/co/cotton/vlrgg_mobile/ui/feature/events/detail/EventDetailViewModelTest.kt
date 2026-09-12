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
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSortDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

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
    fun busyIdentityUsesClockGuardAndManualRetryWithoutLoadingTabsEarly() = runViewModelTest {
        val repository = FakeEventRepository(
            identityResults = ArrayDeque(listOf(AppResult.Busy(ZERO), AppResult.Success(eventDetail))),
        )
        val viewModel = EventDetailViewModel(
            repository,
            EVENT_ID,
            SavedStateHandle(),
            BusyRetryStateFactory.forTest(TestTimeSource()),
        )
        advanceUntilIdle()

        assertEquals(listOf("identity"), repository.requests)
        assertTrue(viewModel.uiState.value.busyRetry?.canRetry() == true)
        viewModel.retryBusy()
        advanceUntilIdle()

        assertEquals(listOf("identity", "identity", "matches"), repository.requests)
        assertEquals(EventIdentityContentState.Content(eventDetail), viewModel.uiState.value.identity)
    }

    @Test
    fun staleBusyTabRetryDoesNotRunAfterSwitchingToAnUnrelatedTab() = runViewModelTest {
        val clock = TestTimeSource()
        val repository = FakeEventRepository(
            matchesResults = ArrayDeque(listOf(AppResult.Busy(10.seconds))),
        )
        val viewModel = EventDetailViewModel(
            repository,
            EVENT_ID,
            SavedStateHandle(),
            BusyRetryStateFactory.forTest(clock),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.busyRetry != null)
        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        viewModel.retryBusy()
        advanceUntilIdle()

        assertEquals(1, repository.requests.count { it == "matches" })
        assertEquals(1, repository.requests.count { it == "news" })
        assertEquals(EventDetailTab.NEWS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun busyTabCooldownSurvivesSwitchingAwayAndBackBeforeItsDeadline() = runViewModelTest {
        val clock = TestTimeSource()
        val repository = FakeEventRepository(
            matchesResults = ArrayDeque(listOf(AppResult.Busy(10.seconds))),
        )
        val viewModel = EventDetailViewModel(
            repository,
            EVENT_ID,
            SavedStateHandle(),
            BusyRetryStateFactory.forTest(clock),
        )
        advanceUntilIdle()

        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.MATCHES)
        advanceUntilIdle()

        assertEquals(1, repository.requests.count { it == "matches" })
        assertEquals("event:matches", viewModel.uiState.value.busyRetry?.operationId)
        assertTrue(viewModel.uiState.value.busyRetry?.canRetry() == false)
    }

    @Test
    fun expiredBusyTabReloadsWithoutKeepingItsRetryDialog() = runViewModelTest {
        val clock = TestTimeSource()
        val repository = FakeEventRepository(
            matchesResults = ArrayDeque(
                listOf(
                    AppResult.Busy(10.seconds),
                    AppResult.Success(listOf(match)),
                ),
            ),
        )
        val viewModel = EventDetailViewModel(
            repository,
            EVENT_ID,
            SavedStateHandle(),
            BusyRetryStateFactory.forTest(clock),
        )
        advanceUntilIdle()

        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        clock += 10.seconds
        viewModel.selectTab(EventDetailTab.MATCHES)

        assertEquals(EventMatchesContentState.Loading, viewModel.uiState.value.matches)
        assertEquals(null, viewModel.uiState.value.busyRetry)
        advanceUntilIdle()

        assertEquals(EventMatchesContentState.Content(listOf(match)), viewModel.uiState.value.matches)
        assertEquals(null, viewModel.uiState.value.busyRetry)
        assertEquals(2, repository.requests.count { it == "matches" })

        viewModel.selectTab(EventDetailTab.NEWS)
        viewModel.selectTab(EventDetailTab.MATCHES)
        advanceUntilIdle()

        assertEquals(2, repository.requests.count { it == "matches" })
    }

    @Test
    fun twoBusyTabsKeepIndependentCooldownsWhileOnlyTheSelectedTabIsVisible() = runViewModelTest {
        val clock = TestTimeSource()
        val repository = FakeEventRepository(
            matchesResults = ArrayDeque(listOf(AppResult.Busy(10.seconds))),
            newsResults = ArrayDeque(listOf(AppResult.Busy(20.seconds))),
        )
        val viewModel = EventDetailViewModel(
            repository,
            EVENT_ID,
            SavedStateHandle(),
            BusyRetryStateFactory.forTest(clock),
        )
        advanceUntilIdle()

        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        assertEquals("event:news", viewModel.uiState.value.busyRetry?.operationId)

        viewModel.selectTab(EventDetailTab.MATCHES)
        assertEquals("event:matches", viewModel.uiState.value.busyRetry?.operationId)

        viewModel.selectTab(EventDetailTab.NEWS)
        assertEquals("event:news", viewModel.uiState.value.busyRetry?.operationId)
        assertEquals(1, repository.requests.count { it == "matches" })
        assertEquals(1, repository.requests.count { it == "news" })
    }

    @Test
    fun identityBusyRemainsTheVisibleRetryAcrossTabSwitches() = runViewModelTest {
        val clock = TestTimeSource()
        val repository = FakeEventRepository(
            identityResults = ArrayDeque(
                listOf(
                    AppResult.Busy(10.seconds),
                    AppResult.Success(eventDetail),
                ),
            ),
        )
        val viewModel = EventDetailViewModel(
            repository,
            EVENT_ID,
            SavedStateHandle(),
            BusyRetryStateFactory.forTest(clock),
        )
        advanceUntilIdle()

        viewModel.selectTab(EventDetailTab.NEWS)

        assertEquals("event:identity", viewModel.uiState.value.busyRetry?.operationId)
        assertTrue(viewModel.uiState.value.busyRetry?.canRetry() == false)

        clock += 10.seconds
        viewModel.retryBusy()
        advanceUntilIdle()

        assertEquals(listOf("identity", "identity", "news"), repository.requests)
        assertEquals(EventIdentityContentState.Content(eventDetail), viewModel.uiState.value.identity)
    }

    @Test
    fun successfulBusyDialogRetryKeepsTabLoadedWhenItIsRevisited() = runViewModelTest {
        val repository = FakeEventRepository(
            matchesResults = ArrayDeque(
                listOf(
                    AppResult.Busy(ZERO),
                    AppResult.Success(listOf(match)),
                ),
            ),
        )
        val viewModel = EventDetailViewModel(repository, EVENT_ID, SavedStateHandle())
        advanceUntilIdle()

        viewModel.retryBusy()
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.MATCHES)
        advanceUntilIdle()

        assertEquals(2, repository.requests.count { it == "matches" })
    }

    @Test
    fun successfulInlineBusyRetryKeepsTabLoadedWhenItIsRevisited() = runViewModelTest {
        val repository = FakeEventRepository(
            matchesResults = ArrayDeque(
                listOf(
                    AppResult.Busy(ZERO),
                    AppResult.Success(listOf(match)),
                ),
            ),
        )
        val viewModel = EventDetailViewModel(repository, EVENT_ID, SavedStateHandle())
        advanceUntilIdle()

        viewModel.dismissBusy()
        viewModel.retrySelectedTab()
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.MATCHES)
        advanceUntilIdle()

        assertEquals(2, repository.requests.count { it == "matches" })
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

    @Test
    fun statsSortCyclesAndSavesPrimitiveColumnAndDirection() = runViewModelTest {
        val savedStateHandle = SavedStateHandle()
        val repository = FakeEventRepository()
        val viewModel = EventDetailViewModel(repository, EVENT_ID, savedStateHandle)
        advanceUntilIdle()

        viewModel.sortStats(EventStatsSortColumn.RATING)
        assertEquals(StatsSort(EventStatsSortColumn.RATING, StatsSortDirection.DESCENDING), viewModel.uiState.value.statsSort)
        assertEquals("rating", savedStateHandle["event-detail-stats-sort-column"])
        assertEquals("desc", savedStateHandle["event-detail-stats-sort-direction"])

        viewModel.sortStats(EventStatsSortColumn.RATING)
        assertEquals(StatsSort(EventStatsSortColumn.RATING, StatsSortDirection.ASCENDING), viewModel.uiState.value.statsSort)
        assertEquals("asc", savedStateHandle["event-detail-stats-sort-direction"])

        viewModel.sortStats(EventStatsSortColumn.RATING)
        assertEquals(null, viewModel.uiState.value.statsSort)
        assertEquals(null, savedStateHandle.get<String>("event-detail-stats-sort-column"))
        assertEquals(null, savedStateHandle.get<String>("event-detail-stats-sort-direction"))

        viewModel.sortStats(EventStatsSortColumn.ACS)
        viewModel.sortStats(EventStatsSortColumn.K_D)
        assertEquals(StatsSort(EventStatsSortColumn.K_D, StatsSortDirection.DESCENDING), viewModel.uiState.value.statsSort)
    }

    @Test
    fun statsSortRestoresOnlyWhenBothPrimitiveIdsAreValid() = runViewModelTest {
        val valid = EventDetailViewModel(
            FakeEventRepository(),
            EVENT_ID,
            SavedStateHandle(
                mapOf(
                    "event-detail-stats-sort-column" to "adr",
                    "event-detail-stats-sort-direction" to "asc",
                ),
            ),
        )
        val invalidColumn = EventDetailViewModel(
            FakeEventRepository(),
            EVENT_ID,
            SavedStateHandle(
                mapOf(
                    "event-detail-stats-sort-column" to "unknown",
                    "event-detail-stats-sort-direction" to "desc",
                ),
            ),
        )
        val partial = EventDetailViewModel(
            FakeEventRepository(),
            EVENT_ID,
            SavedStateHandle(mapOf("event-detail-stats-sort-column" to "rounds")),
        )

        assertEquals(StatsSort(EventStatsSortColumn.ADR, StatsSortDirection.ASCENDING), valid.uiState.value.statsSort)
        assertEquals(null, invalidColumn.uiState.value.statsSort)
        assertEquals(null, partial.uiState.value.statsSort)
        advanceUntilIdle()
    }

    @Test
    fun statsSortSurvivesTabChangesRetryAndReplacementWithoutExtraRequests() = runViewModelTest {
        val replacementStats = stats.copy(players = stats.players.map { it.copy(rating = 9.9) })
        val repository = FakeEventRepository(
            statsResults = ArrayDeque(
                listOf(
                    AppResult.Failure,
                    AppResult.Success(replacementStats),
                ),
            ),
        )
        val viewModel = EventDetailViewModel(repository, EVENT_ID, SavedStateHandle())
        advanceUntilIdle()

        viewModel.sortStats(EventStatsSortColumn.RATING)
        viewModel.selectTab(EventDetailTab.STATS)
        advanceUntilIdle()
        assertEquals(EventStatsContentState.Error, viewModel.uiState.value.stats)

        viewModel.retrySelectedTab()
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.NEWS)
        advanceUntilIdle()
        viewModel.selectTab(EventDetailTab.STATS)
        advanceUntilIdle()

        assertEquals(StatsSort(EventStatsSortColumn.RATING, StatsSortDirection.DESCENDING), viewModel.uiState.value.statsSort)
        assertEquals(EventStatsContentState.Content(replacementStats), viewModel.uiState.value.stats)
        assertEquals(2, repository.requests.count { it == "stats" })
    }

    @Test
    fun separateSavedStateHandlesKeepStatsSortIsolated() = runViewModelTest {
        val firstHandle = SavedStateHandle()
        val secondHandle = SavedStateHandle()
        val first = EventDetailViewModel(FakeEventRepository(), EVENT_ID, firstHandle)
        val second = EventDetailViewModel(FakeEventRepository(expectedEventId = SECOND_EVENT_ID), SECOND_EVENT_ID, secondHandle)

        first.sortStats(EventStatsSortColumn.KAST)

        assertEquals(StatsSort(EventStatsSortColumn.KAST, StatsSortDirection.DESCENDING), first.uiState.value.statsSort)
        assertEquals(null, second.uiState.value.statsSort)
        assertEquals("kast", firstHandle["event-detail-stats-sort-column"])
        assertEquals(null, secondHandle.get<String>("event-detail-stats-sort-column"))
        advanceUntilIdle()
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
        private val expectedEventId: String = EVENT_ID,
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
            assertEquals(expectedEventId, eventId)
            requests += "identity"
            return identityResults.removeFirst()
        }

        override suspend fun getEventMatches(eventId: String): AppResult<List<MatchSummary>> {
            assertEquals(expectedEventId, eventId)
            requests += "matches"
            return matchesResults.removeFirst()
        }

        override suspend fun getEventNews(eventId: String): AppResult<List<NewsSummary>> {
            assertEquals(expectedEventId, eventId)
            requests += "news"
            return newsResults.removeFirst()
        }

        override suspend fun getEventStats(eventId: String): AppResult<EventStats> {
            assertEquals(expectedEventId, eventId)
            requests += "stats"
            return statsResults.removeFirst()
        }
    }

    private companion object {
        const val EVENT_ID = "100"
        const val SECOND_EVENT_ID = "200"

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
