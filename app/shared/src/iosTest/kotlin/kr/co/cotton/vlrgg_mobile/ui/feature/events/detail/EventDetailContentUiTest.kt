package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventPlayerStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStats
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatsAvailability
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSortDirection
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.matchCardTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import kotlin.test.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.swipeDown

@OptIn(ExperimentalTestApi::class)
class EventDetailContentUiTest {

    @Test
    fun matchesIsDefaultShowsStageAndWholeCardNavigates() = runComposeUiTest {
        var clickedMatch: String? = null
        setContent {
            Fixture(
                uiState = populatedState,
                onMatchClick = { clickedMatch = it },
            )
        }

        onNodeWithText("Masters Seoul").assertIsDisplayed()
        onNodeWithText("경기").assertIsDisplayed()
        onNodeWithText("Playoffs").assertIsDisplayed()
        onNodeWithTag(matchCardTag(match.id)).performClick()
        assertEquals(match.id, clickedMatch)
    }

    @Test
    fun newsUsesFullRowAndEmitsParsedArticleIdentity() = runComposeUiTest {
        var clickedNews: Pair<String, String>? = null
        setContent {
            Fixture(
                uiState = populatedState.copy(selectedTab = EventDetailTab.NEWS),
                onNewsClick = { articleId, slug -> clickedNews = articleId to slug },
            )
        }

        onNodeWithText(news.title).performClick()
        assertEquals(news.articleId to news.slug, clickedNews)
        onNodeWithText(news.publishedAt).assertIsDisplayed()
    }

    @Test
    fun statsKeepMetricOrderNullMarkerAndOnlyPlayerIdentityNavigatesWhileEveryHeaderEmitsSort() = runComposeUiTest {
        var clickedPlayer: String? = null
        val sortedColumns = mutableListOf<EventStatsSortColumn>()
        setContent {
            Fixture(
                uiState = populatedState.copy(selectedTab = EventDetailTab.STATS),
                onPlayerClick = { clickedPlayer = it },
                onSortStats = sortedColumns::add,
            )
        }

        val headers = listOf(
            "Rounds" to EventStatsSortColumn.ROUNDS,
            "Rating" to EventStatsSortColumn.RATING,
            "ACS" to EventStatsSortColumn.ACS,
            "K-D" to EventStatsSortColumn.K_D,
            "ADR" to EventStatsSortColumn.ADR,
            "KAST" to EventStatsSortColumn.KAST,
        )
        headers.forEach { (_, column) ->
            onNodeWithTag(eventStatsMetricHeaderTag(column)).assertExists().performScrollTo().performClick()
        }
        assertEquals(headers.map { it.second }, sortedColumns)
        onNodeWithText("—").assertExists()
        onNodeWithTag(eventStatsPlayerTag(player.playerId)).performClick()
        assertEquals(player.playerId, clickedPlayer)
    }

    @Test
    fun statsSortKeepsIdentityAndMetricCellsInTheSameNumericOrder() = runComposeUiTest {
        val low = player.copy(playerId = "low", playerName = "Rating 2", rating = 2.0)
        val high = player.copy(playerId = "high", playerName = "Rating 10", rating = 10.0)
        setContent {
            Fixture(
                uiState = populatedState.copy(
                    selectedTab = EventDetailTab.STATS,
                    stats = EventStatsContentState.Content(
                        EventStats(EventStatsAvailability.AVAILABLE, listOf(low, high)),
                    ),
                    statsSort = StatsSort(EventStatsSortColumn.RATING, StatsSortDirection.DESCENDING),
                ),
            )
        }

        val highIdentity = onNodeWithTag(eventStatsPlayerTag("high")).fetchSemanticsNode().boundsInRoot
        val lowIdentity = onNodeWithTag(eventStatsPlayerTag("low")).fetchSemanticsNode().boundsInRoot
        val highRating = onNodeWithTag(eventStatsMetricValueTag("high", EventStatsSortColumn.RATING)).fetchSemanticsNode().boundsInRoot
        val lowRating = onNodeWithTag(eventStatsMetricValueTag("low", EventStatsSortColumn.RATING)).fetchSemanticsNode().boundsInRoot
        assertEquals(highIdentity.top, highRating.top)
        assertEquals(lowIdentity.top, lowRating.top)
        kotlin.test.assertTrue(highIdentity.top < lowIdentity.top)
    }

    @Test
    fun statsLazyTableReachesTheLastLongRowWithoutClippingItsValue() = runComposeUiTest {
        val players = (1..30).map { index ->
            player.copy(
                playerId = "player-$index",
                playerName = "A very long player name number $index that wraps",
                averageDamagePerRound = 1234567890.123 + index,
            )
        }
        setContent {
            Fixture(
                uiState = populatedState.copy(
                    selectedTab = EventDetailTab.STATS,
                    stats = EventStatsContentState.Content(EventStats(EventStatsAvailability.AVAILABLE, players)),
                ),
            )
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(eventStatsPlayerTag("player-30")))
        onNodeWithTag(eventStatsPlayerTag("player-30")).assertExists()
        onNodeWithTag(eventStatsMetricValueTag("player-30", EventStatsSortColumn.ADR)).assertExists()
    }

    @Test
    fun identityAndTabFailuresExposeDifferentRetryCallbacks() = runComposeUiTest {
        var identityRetries = 0
        var tabRetries = 0
        setContent {
            Fixture(
                uiState = EventDetailUiState(identity = EventIdentityContentState.Error),
                onRetryIdentity = { identityRetries += 1 },
                onRetrySelectedTab = { tabRetries += 1 },
            )
        }

        onNodeWithTag(EVENT_DETAIL_IDENTITY_RETRY_TAG).performClick()
        assertEquals(1, identityRetries)
        assertEquals(0, tabRetries)

        setContent {
            Fixture(
                uiState = populatedState.copy(matches = EventMatchesContentState.Error),
                onRetryIdentity = { identityRetries += 1 },
                onRetrySelectedTab = { tabRetries += 1 },
            )
        }
        onNodeWithTag(EVENT_DETAIL_TAB_RETRY_TAG).performClick()
        assertEquals(1, identityRetries)
        assertEquals(1, tabRetries)
    }

    @Test
    fun dismissedBusyCooldownReenablesInlineRetryAfterExpiry() = runComposeUiTest {
        val clock = TestTimeSource()
        val state = mutableStateOf(
            EventDetailUiState(
                identity = EventIdentityContentState.Error,
                busyRetry = BusyRetryStateFactory.forTest(clock)
                    .create("event:identity", 1.seconds)
                    .dismiss(),
            ),
        )
        var retries = 0
        mainClock.autoAdvance = false
        setContent {
            Fixture(
                uiState = state.value,
                onRetryIdentity = { retries += 1 },
            )
        }

        onNodeWithTag(EVENT_DETAIL_IDENTITY_RETRY_TAG).assertIsNotEnabled()
        clock += 1.seconds
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(EVENT_DETAIL_IDENTITY_RETRY_TAG).assertIsEnabled().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun tallHeroAtDoubleFontOnAShortScreenKeepsTabsAndTabContentReachable() =
        runSkikoComposeUiTest(size = Size(360f, 480f)) {
            val selectedTabs = mutableListOf<EventDetailTab>()
            val longDescription = List(12) { "Part of the Valorant Champions Tour official circuit." }.joinToString(" ")
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
                ) {
                    Fixture(
                        uiState = populatedState.copy(
                            identity = EventIdentityContentState.Content(
                                event.copy(
                                    name = "VCT 2027: EMEA Kickoff - Türkiye Qualifier",
                                    description = longDescription,
                                ),
                            ),
                        ),
                        onSelectTab = { selectedTabs += it },
                    )
                }
            }

            // The description stays within three lines instead of growing the hero without bound.
            val descriptionLayouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(longDescription, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(descriptionLayouts)
            assertTrue(descriptionLayouts.first().lineCount <= 3)

            // The hero scrolls away, so both the tabs and the selected tab content can be reached.
            onNodeWithTag(eventDetailTabTag(EventDetailTab.STATS)).performScrollTo().assertIsDisplayed().performClick()
            assertEquals(listOf(EventDetailTab.STATS), selectedTabs)
            repeat(2) { onRoot().performTouchInput { swipeUp() } }
            onNodeWithTag(matchCardTag(match.id)).assertIsDisplayed()
        }

    @Test
    fun draggingTheTabListCollapsesTheHeroBeforeTheListScrolls() =
        runSkikoComposeUiTest(size = Size(360f, 640f)) {
            val matches = (1..30).map { match.copy(id = "match-$it") }
            setContent {
                Fixture(uiState = populatedState.copy(matches = EventMatchesContentState.Content(matches)))
            }

            val tabsTopBefore = onNodeWithTag(eventDetailTabTag(EventDetailTab.MATCHES)).fetchSemanticsNode().boundsInRoot.top
            onNodeWithTag(matchCardTag("match-2")).performTouchInput { swipeUp(startY = bottom - 10f, endY = top) }
            waitForIdle()
            val tabsTopAfter = onNodeWithTag(eventDetailTabTag(EventDetailTab.MATCHES)).fetchSemanticsNode().boundsInRoot.top
            assertTrue(tabsTopAfter < tabsTopBefore, "Hero did not collapse: $tabsTopBefore -> $tabsTopAfter")
            // The tabs stop at the top of the content area instead of scrolling out with the hero.
            assertTrue(tabsTopAfter >= 0f, "Tabs scrolled out of view: $tabsTopAfter")
        }

    @Test
    fun eachTabRestoresItsOwnHeroCollapseAndListPositionAfterSwitchingTabs() =
        runSkikoComposeUiTest(size = Size(360f, 640f)) {
            val matches = (1..30).map { match.copy(id = "match-$it") }
            val newsItems = (1..30).map { news.copy(articleId = "${200 + it}", slug = "news-$it", title = "News $it") }
            var state by mutableStateOf(
                populatedState.copy(
                    matches = EventMatchesContentState.Content(matches),
                    news = EventNewsContentState.Content(newsItems),
                ),
            )
            setContent {
                Fixture(uiState = state, onSelectTab = { state = state.copy(selectedTab = it) })
            }
            fun tabsTop() = onNodeWithTag(eventDetailTabTag(EventDetailTab.MATCHES)).fetchSemanticsNode().boundsInRoot.top
            fun firstVisibleMatch() = matches.firstNotNullOf { item ->
                onAllNodesWithTag(matchCardTag(item.id)).fetchSemanticsNodes()
                    .firstOrNull { it.boundsInRoot.top >= tabsTop() }
                    ?.let { item.id to it.boundsInRoot.top }
            }
            val expandedTabsTop = tabsTop()

            // Matches: collapse the hero and scroll the list.
            repeat(3) { onRoot().performTouchInput { swipeUp(startY = bottom - 20f, endY = centerY) } }
            waitForIdle()
            val collapsedTabsTop = tabsTop()
            val (matchId, matchTop) = firstVisibleMatch()
            assertTrue(collapsedTabsTop < expandedTabsTop, "Hero did not collapse on the matches tab")
            assertTrue(matchId != matches.first().id, "Matches list did not scroll")

            // News keeps its own hero, and expanding it there must not leak back into matches.
            onNodeWithTag(eventDetailTabTag(EventDetailTab.NEWS)).performClick()
            repeat(2) { onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom - 20f) } }
            waitForIdle()
            assertEquals(expandedTabsTop, tabsTop(), 1f)

            onNodeWithTag(eventDetailTabTag(EventDetailTab.MATCHES)).performClick()
            waitForIdle()
            assertEquals(collapsedTabsTop, tabsTop(), 1f)
            assertEquals(matchTop, onNodeWithTag(matchCardTag(matchId)).fetchSemanticsNode().boundsInRoot.top, 1f)
        }

    @androidx.compose.runtime.Composable
    private fun Fixture(
        uiState: EventDetailUiState,
        onSelectTab: (EventDetailTab) -> Unit = {},
        onMatchClick: (String) -> Unit = {},
        onNewsClick: (String, String) -> Unit = { _, _ -> },
        onPlayerClick: (String) -> Unit = {},
        onSortStats: (EventStatsSortColumn) -> Unit = {},
        onRetryIdentity: () -> Unit = {},
        onRetrySelectedTab: () -> Unit = {},
    ) {
        VlrTheme {
            EventDetailContent(
                uiState = uiState,
                matchesListState = rememberLazyListState(),
                newsListState = rememberLazyListState(),
                statsListState = rememberLazyListState(),
                statsHorizontalScrollState = rememberScrollState(),
                onBack = {},
                onSelectTab = onSelectTab,
                onMatchClick = onMatchClick,
                onNewsClick = onNewsClick,
                onPlayerClick = onPlayerClick,
                onSortStats = onSortStats,
                onRetryIdentity = onRetryIdentity,
                onRetrySelectedTab = onRetrySelectedTab,
            )
        }
    }

    private companion object {
        val event = EventDetail(
            id = "100",
            name = "Masters Seoul",
            status = null,
            dateLabel = "Aug 20—Sep 4",
            location = "Seoul",
            series = "VCT 2026",
            description = null,
            imageUrl = null,
        )
        val match = MatchSummary(
            id = "match-1",
            status = MatchStatus.UPCOMING,
            timeLabel = "18:00",
            relativeTimeLabel = null,
            homeTeam = MatchTeam("Alpha", null),
            awayTeam = MatchTeam("Beta", null),
            homeScore = null,
            awayScore = null,
            event = MatchEvent("Masters Seoul", "Playoffs", "100"),
            time = null,
        )
        val news = NewsSummary("101", "masters-seoul", "Masters begins", null, "2026-08-25")
        val player = EventPlayerStats(
            playerId = "player-1",
            playerName = "Meteor",
            teamAbbreviation = "GEN",
            roundsPlayed = 120,
            rating = null,
            averageCombatScore = 240,
            killDeathRatio = 1.3,
            averageDamagePerRound = 155.5,
            killAssistSurvivedTradedPercentage = 78.0,
        )
        val populatedState = EventDetailUiState(
            identity = EventIdentityContentState.Content(event),
            matches = EventMatchesContentState.Content(listOf(match)),
            news = EventNewsContentState.Content(listOf(news)),
            stats = EventStatsContentState.Content(
                EventStats(EventStatsAvailability.AVAILABLE, listOf(player)),
            ),
        )
    }
}
