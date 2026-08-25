package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.matchCardTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

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
        onNodeWithText("Matches").assertIsDisplayed()
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
    fun statsKeepMetricOrderNullMarkerAndOnlyPlayerIdentityNavigates() = runComposeUiTest {
        var clickedPlayer: String? = null
        setContent {
            Fixture(
                uiState = populatedState.copy(selectedTab = EventDetailTab.STATS),
                onPlayerClick = { clickedPlayer = it },
            )
        }

        listOf("Rounds", "Rating", "ACS", "K-D", "ADR", "KAST").forEach { label ->
            onNodeWithText(label).assertExists()
        }
        onNodeWithText("—").assertExists()
        onNodeWithTag(eventStatsPlayerTag(player.playerId)).performClick()
        assertEquals(player.playerId, clickedPlayer)
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

    @androidx.compose.runtime.Composable
    private fun Fixture(
        uiState: EventDetailUiState,
        onMatchClick: (String) -> Unit = {},
        onNewsClick: (String, String) -> Unit = { _, _ -> },
        onPlayerClick: (String) -> Unit = {},
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
                onSelectTab = {},
                onMatchClick = onMatchClick,
                onNewsClick = onNewsClick,
                onPlayerClick = onPlayerClick,
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
