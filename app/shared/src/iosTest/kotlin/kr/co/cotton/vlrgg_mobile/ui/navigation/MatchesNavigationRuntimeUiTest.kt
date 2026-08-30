package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
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
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail as MatchDetailModel
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchPage
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.MatchesViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.matchCardTag
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.MatchDetailViewModel
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class MatchesNavigationRuntimeUiTest {

    @Test
    fun matchesEntryPreservesBothFeedsSelectedTabAndScrollAcrossRootAndDetailRoundTrips() {
        val repository = FakeMatchRepository()
        val viewModelFactory = AppViewModelFactory(
            viewModelProviders = emptyMap(),
            assistedFactoryProviders = mapOf(
                MatchesViewModel::class to {
                    MatchesViewModel.Factory { savedStateHandle ->
                        MatchesViewModel(repository, savedStateHandle)
                    }
                },
            ),
            manualAssistedFactoryProviders = mapOf(
                MatchDetailViewModel.Factory::class to {
                    fixtureMatchDetailFactory(repository)
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
                            initialSelectedRoot = MatchesRoot,
                            onNavigationStateAvailable = { navigationState = it },
                            entryContent = { destination, onSearch, onPush, onBack ->
                                when (destination) {
                                    MatchesRoot,
                                    is MatchDetail,
                                    -> NavigationContent(
                                        destination = destination,
                                        onSearch = onSearch,
                                        onPush = onPush,
                                        onBack = onBack,
                                    )

                                    is RootNavKey -> Text("root:${destination.destinationDescriptor.title}")
                                    else -> Text("fixture:${destination.destinationDescriptor.marker}")
                                }
                            },
                        )
                    }
                }
            }

            onNodeWithTag(matchCardTag("upcoming-1")).assertExists()
            scrollToCard("upcoming-10")

            onNodeWithText("결과").performClick()
            onNodeWithTag(matchCardTag("result-1")).assertExists()
            scrollToCard("result-10")

            onNodeWithText("예정 · 라이브").performClick()
            onNodeWithTag(matchCardTag("upcoming-10")).assertIsDisplayed()
            onNodeWithText("결과").performClick()
            onNodeWithTag(matchCardTag("result-10")).assertIsDisplayed()

            onNodeWithText("News").performClick()
            onNodeWithText("root:News").assertExists()
            onNodeWithText("Matches").performClick()
            onNodeWithTag(matchCardTag("result-10")).assertIsDisplayed()
            assertEquals(1, repository.upcomingRequests)
            assertEquals(1, repository.resultsRequests)

            onNodeWithTag(matchCardTag("result-10")).performClick()
            val detailEntry = assertIs<OverlayNavEntry>(runOnIdle {
                requireNotNull(navigationState).currentBackStack.last()
            })
            assertEquals(MatchDetail(matchId = "result-10"), detailEntry.destination)
            assertRealMatchDetailDestination()

            onNodeWithContentDescription("뒤로 가기").performClick()
            onNodeWithTag(matchCardTag("result-10")).assertIsDisplayed()
            onNodeWithText("예정 · 라이브").performClick()
            onNodeWithTag(matchCardTag("upcoming-10")).assertIsDisplayed()
            onNodeWithTag(matchCardTag("upcoming-10")).performClick()
            val upcomingDetailEntry = assertIs<OverlayNavEntry>(runOnIdle {
                requireNotNull(navigationState).currentBackStack.last()
            })
            assertEquals(MatchDetail(matchId = "upcoming-10"), upcomingDetailEntry.destination)
            assertRealMatchDetailDestination()
            onNodeWithContentDescription("뒤로 가기").performClick()
            onNodeWithTag(matchCardTag("upcoming-10")).assertIsDisplayed()
            assertEquals(1, repository.upcomingRequests)
            assertEquals(1, repository.resultsRequests)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollToCard(matchId: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(matchCardTag(matchId)))
        onNodeWithTag(matchCardTag(matchId)).assertIsDisplayed()
    }

    private class FakeMatchRepository : MatchRepository {
        var upcomingRequests = 0
            private set
        var resultsRequests = 0
            private set

        override suspend fun getUpcomingMatches(page: Int): AppResult<MatchPage> {
            check(page == 1) { "Unexpected upcoming pagination request: $page" }
            upcomingRequests += 1
            return page(
                category = MatchListCategory.UPCOMING,
                prefix = "upcoming",
                status = MatchStatus.UPCOMING,
            )
        }

        override suspend fun getResults(page: Int): AppResult<MatchPage> {
            check(page == 1) { "Unexpected results pagination request: $page" }
            resultsRequests += 1
            return page(
                category = MatchListCategory.RESULTS,
                prefix = "result",
                status = MatchStatus.COMPLETED,
            )
        }

        override suspend fun getMatchDetail(matchId: String): AppResult<MatchDetailModel> =
            AppResult.Success(fixtureMatchDetail(matchId))

        private fun page(
            category: MatchListCategory,
            prefix: String,
            status: MatchStatus,
        ): AppResult<MatchPage> = AppResult.Success(
            MatchPage(
                category = category,
                page = 1,
                groups = listOf(
                    MatchDateGroup(
                        dateLabel = if (category == MatchListCategory.UPCOMING) "TODAY" else "YESTERDAY",
                        matches = (1..24).map { index ->
                            MatchSummary(
                                id = "$prefix-$index",
                                status = status,
                                timeLabel = "$index:00",
                                relativeTimeLabel = if (status == MatchStatus.UPCOMING) "IN $index HOURS" else null,
                                homeTeam = MatchTeam(name = "$prefix-home-$index", id = null),
                                awayTeam = MatchTeam(name = "$prefix-away-$index", id = null),
                                homeScore = if (status == MatchStatus.COMPLETED) 13 else null,
                                awayScore = if (status == MatchStatus.COMPLETED) index % 13 else null,
                                event = MatchEvent(name = "$prefix-event", series = "series-$index", id = null),
                            )
                        },
                    ),
                ),
            ),
        )
    }

    private class TestHostViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
