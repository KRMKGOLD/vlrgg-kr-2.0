package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kr.co.cotton.vlrgg_mobile.di.AppViewModelFactory
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail as MatchDetailModel
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchMap
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.matches.RelatedMatch
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.MATCH_DETAIL_EVENT_TAG
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.matchDetailHeadToHeadTag
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.matchDetailTeamTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MatchDetailNavigationRuntimeUiTest {

    @Test
    fun matchDetailUsesRealDestinationForEventTeamsAndHeadToHeadAndRetainsItsOwningRoot() {
        val repository = FixtureMatchRepository(::navigationMatchDetail)
        val factory = AppViewModelFactory(
            viewModelProviders = emptyMap(),
            assistedFactoryProviders = emptyMap(),
            manualAssistedFactoryProviders = mapOf(
                kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.MatchDetailViewModel.Factory::class to {
                    fixtureMatchDetailFactory(repository)
                },
            ),
        )
        val hostOwner = TestHostViewModelStoreOwner()
        var navigationState: AppNavigationState? = null

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalMetroViewModelFactory provides factory,
                    LocalViewModelStoreOwner provides hostOwner,
                ) {
                    VlrTheme {
                        AppNavigationRuntime(
                            initialSelectedRoot = MatchesRoot,
                            onNavigationStateAvailable = { navigationState = it },
                            entryContent = { destination, onSearch, onPush, onBack ->
                                when (destination) {
                                    MatchesRoot -> MatchRootFixture(onPush)
                                    is MatchDetail -> NavigationContent(
                                        destination = destination,
                                        onSearch = onSearch,
                                        onPush = onPush,
                                        onBack = onBack,
                                    )

                                    is EventDetail,
                                    is TeamDetail,
                                        -> ChildDestinationFixture(destination, onBack)

                                    is RootNavKey -> Text("root:${destination.destinationDescriptor.title}")
                                    else -> error("Unexpected fixture destination: $destination")
                                }
                            },
                        )
                    }
                }
            }

            onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("match-root-row-20"))
            onNodeWithTag("match-root-row-20").assertIsDisplayed()
            runOnIdle { requireNotNull(navigationState).push(MatchDetail(MATCH_ID)) }

            assertRealMatchDetailDestination()
            onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(matchDetailHeadToHeadTag(RELATED_MATCH_ID)))
            onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH_ID)).assertIsDisplayed()

            onNodeWithTag(MATCH_DETAIL_EVENT_TAG).performClick()
            assertTopDestination(navigationState, EventDetail(EVENT_ID))
            onNodeWithText("fixture-back").performClick()
            assertRealMatchDetailDestination()
            onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH_ID)).assertIsDisplayed()

            onNodeWithTag(matchDetailTeamTag("home")).performClick()
            assertTopDestination(navigationState, TeamDetail(HOME_TEAM_ID))
            onNodeWithText("fixture-back").performClick()
            assertRealMatchDetailDestination()

            onNodeWithTag(matchDetailTeamTag("away")).performClick()
            assertTopDestination(navigationState, TeamDetail(AWAY_TEAM_ID))
            onNodeWithText("fixture-back").performClick()
            assertRealMatchDetailDestination()

            onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH_ID)).performClick()
            assertTopDestination(navigationState, MatchDetail(RELATED_MATCH_ID))
            assertRealMatchDetailDestination()
            onNodeWithContentDescription("뒤로 가기").performClick()
            assertRealMatchDetailDestination()
            onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH_ID)).assertIsDisplayed()

            runOnIdle { requireNotNull(navigationState).selectRoot(NewsRoot) }
            onNodeWithText("root:News").assertExists()
            runOnIdle { requireNotNull(navigationState).selectRoot(MatchesRoot) }
            assertRealMatchDetailDestination()
            onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH_ID)).assertIsDisplayed()

            onNodeWithContentDescription("뒤로 가기").performClick()
            onNodeWithText("match-root-loaded").assertExists()
            onNodeWithTag("match-root-row-20").assertIsDisplayed()
            assertEquals(listOf(MATCH_ID, RELATED_MATCH_ID), repository.requestedIds)
        }
    }

    private fun assertTopDestination(
        navigationState: AppNavigationState?,
        expected: AppNavKey,
    ) {
        assertEquals(
            expected,
            (requireNotNull(navigationState).currentBackStack.last() as OverlayNavEntry).destination,
        )
    }

    @androidx.compose.runtime.Composable
    private fun MatchRootFixture(onPush: (AppNavKey) -> Unit) {
        val listState = rememberLazyListState()
        Column {
            Text("match-root-loaded")
            Button(onClick = { onPush(MatchDetail(MATCH_ID)) }) { Text("open-match") }
            LazyColumn(
                modifier = Modifier.height(96.dp),
                state = listState,
            ) {
                items(30) { index ->
                    Text(
                        text = "match-root-row-$index",
                        modifier = Modifier.testTag("match-root-row-$index"),
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ChildDestinationFixture(
        destination: AppNavKey,
        onBack: () -> Unit,
    ) {
        Column {
            Text("fixture:${destination.destinationDescriptor.title}")
            Button(onClick = onBack) { Text("fixture-back") }
        }
    }

    private class TestHostViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private companion object {
        const val MATCH_ID = "match-45"
        const val EVENT_ID = "event-45"
        const val HOME_TEAM_ID = "team-a-45"
        const val AWAY_TEAM_ID = "team-b-45"
        const val RELATED_MATCH_ID = "related-45"

        fun navigationMatchDetail(matchId: String): MatchDetailModel = MatchDetailModel(
            id = matchId,
            status = MatchStatus.COMPLETED,
            timeLabel = "18:00",
            relativeTimeLabel = null,
            scheduledAt = "2026-08-29",
            homeTeam = MatchTeam("Alpha", HOME_TEAM_ID),
            awayTeam = MatchTeam("Beta", AWAY_TEAM_ID),
            homeScore = 2,
            awayScore = 1,
            event = MatchEvent("Masters Seoul", "Playoffs", EVENT_ID),
            description = "Upper final",
            seriesFormat = "Bo3",
            maps = listOf(MatchMap("Haven", 13, 9)),
            headToHead = listOf(RelatedMatch(RELATED_MATCH_ID, "Alpha", "Beta", 2, 0)),
            pastMatches = emptyList(),
        )
    }
}
