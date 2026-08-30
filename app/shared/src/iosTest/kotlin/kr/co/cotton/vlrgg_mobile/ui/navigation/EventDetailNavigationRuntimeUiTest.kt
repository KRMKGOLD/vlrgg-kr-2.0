package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kr.co.cotton.vlrgg_mobile.di.AppViewModelFactory
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail as EventIdentity
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatsAvailability
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventPlayerStats
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.PlayerRepository
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail as PlayerIdentity
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerProfile
import kr.co.cotton.vlrgg_mobile.ui.feature.events.detail.EventDetailTab
import kr.co.cotton.vlrgg_mobile.ui.feature.events.detail.EventDetailViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.events.detail.eventDetailTabTag
import kr.co.cotton.vlrgg_mobile.ui.feature.events.detail.eventStatsPlayerTag
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.matchCardTag
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.MatchDetailViewModel
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.feature.player.detail.PLAYER_DETAIL_HEADER_TAG
import kr.co.cotton.vlrgg_mobile.ui.feature.player.detail.PlayerDetailViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class EventDetailNavigationRuntimeUiTest {

    @Test
    fun eventDetailPreservesSelectedTabDataAndScrollAcrossRootAndChildRoundTrips() {
        val repository = FakeEventRepository()
        val matchRepository = FixtureMatchRepository()
        val viewModelFactory = AppViewModelFactory(
            viewModelProviders = emptyMap(),
            assistedFactoryProviders = emptyMap(),
            manualAssistedFactoryProviders = mapOf(
                EventDetailViewModel.Factory::class to {
                    EventDetailViewModel.Factory { eventId, savedStateHandle ->
                        EventDetailViewModel(repository, eventId, savedStateHandle)
                    }
                },
                PlayerDetailViewModel.Factory::class to {
                    PlayerDetailViewModel.Factory { playerId ->
                        PlayerDetailViewModel(FakePlayerRepository(), playerId)
                    }
                },
                MatchDetailViewModel.Factory::class to {
                    fixtureMatchDetailFactory(matchRepository)
                },
            ),
        )
        val hostOwner = TestHostViewModelStoreOwner()
        var navigationState: AppNavigationState? = null

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalMetroViewModelFactory provides viewModelFactory,
                    LocalViewModelStoreOwner provides hostOwner,
                ) {
                    VlrTheme {
                        AppNavigationRuntime(
                            initialSelectedRoot = EventsRoot,
                            onNavigationStateAvailable = { navigationState = it },
                            entryContent = { destination, onSearch, onPush, onBack ->
                                if (destination is EventDetail || destination is PlayerDetail || destination is MatchDetail) {
                                    NavigationContent(
                                        destination = destination,
                                        onSearch = onSearch,
                                        onPush = onPush,
                                        onBack = onBack,
                                        modifier = Modifier.testTag(EVENT_DETAIL_ROUTE_TAG),
                                    )
                                } else {
                                    Text("fixture:${destination.destinationDescriptor.marker}")
                                }
                            },
                        )
                    }
                }
            }

            runOnIdle { requireNotNull(navigationState).push(EventDetail(EVENT_ID)) }
            onNodeWithTag(EVENT_DETAIL_ROUTE_TAG).assertExists()
            onNodeWithTag(matchCardTag("match-1")).performClick()
            val matchEntry = assertIs<OverlayNavEntry>(
                runOnIdle { requireNotNull(navigationState).currentBackStack.last() },
            )
            assertEquals(MatchDetail("match-1"), matchEntry.destination)
            assertRealMatchDetailDestination()
            onNodeWithContentDescription("뒤로 가기").performClick()
            onNodeWithTag(EVENT_DETAIL_ROUTE_TAG).assertExists()
            onNodeWithTag(eventDetailTabTag(EventDetailTab.NEWS)).performClick()
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("News 24"))
            onNodeWithText("News 24").assertExists()

            runOnIdle { requireNotNull(navigationState).selectRoot(NewsRoot) }
            onNodeWithText("fixture:news").assertExists()
            runOnIdle { requireNotNull(navigationState).selectRoot(EventsRoot) }

            onNodeWithTag(eventDetailTabTag(EventDetailTab.NEWS)).assertExists()
            onNodeWithText("News 24").assertExists()
            assertEquals(1, repository.identityRequests)
            assertEquals(1, repository.matchesRequests)
            assertEquals(1, repository.newsRequests)

            onNodeWithText("News 24").performClick()
            val newsDetailEntry = assertIs<OverlayNavEntry>(
                runOnIdle { requireNotNull(navigationState).currentBackStack.last() },
            )
            assertEquals(
                NewsDetail(articleId = "124", slug = "news-24"),
                newsDetailEntry.destination,
            )
            runOnIdle { requireNotNull(navigationState).popOverlay() }
            onNodeWithText("News 24").assertExists()
            assertEquals(1, repository.newsRequests)

            onNodeWithTag(eventDetailTabTag(EventDetailTab.STATS)).performClick()
            onNodeWithTag(eventStatsPlayerTag(EVENT_PLAYER_ID)).performClick()
            val playerEntry = assertIs<OverlayNavEntry>(
                runOnIdle { requireNotNull(navigationState).currentBackStack.last() },
            )
            assertEquals(PlayerDetail(EVENT_PLAYER_ID), playerEntry.destination)
            onNodeWithTag(PLAYER_DETAIL_HEADER_TAG).assertExists()
            onNodeWithContentDescription("뒤로 가기").performClick()
            onNodeWithTag(eventStatsPlayerTag(EVENT_PLAYER_ID)).assertExists()
        }
    }

    private class FakeEventRepository : EventRepository {
        var identityRequests = 0
        var matchesRequests = 0
        var newsRequests = 0

        override suspend fun getEvents() = error("Event list is not used")

        override suspend fun getEventDetail(eventId: String): AppResult<EventIdentity> {
            identityRequests += 1
            return AppResult.Success(
                EventIdentity(eventId, "Masters Seoul", null, null, null, null, null, null),
            )
        }

        override suspend fun getEventMatches(eventId: String): AppResult<List<MatchSummary>> {
            matchesRequests += 1
            return AppResult.Success(
                listOf(
                    MatchSummary(
                        id = "match-1",
                        status = MatchStatus.UPCOMING,
                        timeLabel = "18:00",
                        relativeTimeLabel = null,
                        homeTeam = MatchTeam("Alpha", null),
                        awayTeam = MatchTeam("Beta", null),
                        homeScore = null,
                        awayScore = null,
                        event = MatchEvent("Masters Seoul", "Playoffs", eventId),
                    ),
                ),
            )
        }

        override suspend fun getEventNews(eventId: String): AppResult<List<NewsSummary>> {
            newsRequests += 1
            return AppResult.Success(
                (1..24).map { index ->
                    NewsSummary(
                        articleId = "${100 + index}",
                        slug = "news-$index",
                        title = "News $index",
                        author = null,
                        publishedAt = "2026-08-25",
                    )
                },
            )
        }

        override suspend fun getEventStats(eventId: String): AppResult<EventStats> =
            AppResult.Success(
                EventStats(
                    EventStatsAvailability.AVAILABLE,
                    listOf(EventPlayerStats(EVENT_PLAYER_ID, "Event Player", "T1", 12, 1.2, 220, 1.3, 145.0, 72.0)),
                ),
            )
    }

    private class FakePlayerRepository : PlayerRepository {
        override suspend fun getPlayerDetail(playerId: String): AppResult<PlayerIdentity> = AppResult.Success(
            PlayerIdentity(playerId, PlayerProfile("Event Player", null, emptyList(), null, null), null, emptyList(), emptyList()),
        )
    }

    private class TestHostViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private companion object {
        const val EVENT_ID = "100"
        const val EVENT_DETAIL_ROUTE_TAG = "event-detail-route"
        const val EVENT_PLAYER_ID = "2002"
    }
}
