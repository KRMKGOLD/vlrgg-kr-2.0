package kr.co.cotton.vlrgg_mobile.ui.feature.matches

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.ui.component.ROOT_TOP_BAR_TAG
import kr.co.cotton.vlrgg_mobile.ui.component.ROOT_TOP_BAR_TITLE_TAG
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.matchScoreTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MatchesContentUiTest {

    @Test
    fun loadingShowsSkeletonAndNoNotificationAction() = runComposeUiTest {
        var searchClicks = 0
        setContent {
            MatchesContentFixture(
                uiState = MatchesUiState(),
                onSearch = { searchClicks += 1 },
            )
        }

        onNodeWithTag(MATCHES_LOADING_TAG).assertIsDisplayed()
        onNodeWithTag(ROOT_TOP_BAR_TAG).assertIsDisplayed()
        onNodeWithTag(ROOT_TOP_BAR_TITLE_TAG).assertIsDisplayed()
        onNodeWithText("Matches").assertIsDisplayed()
        onNodeWithContentDescription("검색").performClick()
        assertEquals(1, searchClicks)
        onNodeWithContentDescription("알림").assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기").assertDoesNotExist()
    }

    @Test
    fun emptyShowsSelectedFeedEmptyMessage() = runComposeUiTest {
        setContent {
            MatchesContentFixture(
                uiState = MatchesUiState(
                    upcomingLive = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Empty,
                    ),
                ),
            )
        }

        onNodeWithText("표시할 예정 또는 라이브 경기가 없어요.").assertIsDisplayed()
    }

    @Test
    fun initialErrorRetryInvokesRetryCallback() = runComposeUiTest {
        var retries = 0
        setContent {
            MatchesContentFixture(
                uiState = MatchesUiState(
                    upcomingLive = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Error,
                    ),
                ),
                onRetryInitial = { retries += 1 },
            )
        }

        onNodeWithText("경기 목록을 불러오지 못했습니다.", substring = true).assertIsDisplayed()
        onNodeWithTag(MATCHES_INITIAL_RETRY_TAG).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun populatedCardShowsUpstreamFieldsAndEmitsStableId() = runComposeUiTest {
        var clickedMatchId: String? = null
        setContent {
            MatchesContentFixture(
                uiState = contentState(upcomingMatch),
                onMatchClick = { clickedMatchId = it },
            )
        }

        onNodeWithText("TODAY, AUG 23").assertIsDisplayed()
        onNodeWithText("LIVE").assertIsDisplayed()
        onNodeWithText("IN 2 HOURS").assertIsDisplayed()
        onNodeWithText("Paper Rex").assertIsDisplayed()
        onNodeWithText("Gen.G").assertIsDisplayed()
        onNodeWithText("Valorant Champions 2026").assertIsDisplayed()
        onNodeWithText("Playoffs · Upper Final").assertIsDisplayed()
        onNodeWithText("VS").assertIsDisplayed()
        onNodeWithTag(matchScoreTag(upcomingMatch.id), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("match-team-placeholder-${upcomingMatch.id}-home", useUnmergedTree = true)
            .assertDoesNotExist()
        onNodeWithTag("match-team-placeholder-${upcomingMatch.id}-away", useUnmergedTree = true)
            .assertDoesNotExist()

        onNodeWithTag(matchCardTag(upcomingMatch.id)).performClick()
        assertEquals(upcomingMatch.id, clickedMatchId)
    }

    @Test
    fun completedCardShowsScoreInsteadOfScheduledMarker() = runComposeUiTest {
        setContent {
            MatchesContentFixture(
                uiState = MatchesUiState(
                    selectedTab = MatchesTab.RESULTS,
                    upcomingLive = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Empty,
                    ),
                    results = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Content(
                            listOf(MatchDateGroup("YESTERDAY", listOf(completedMatch))),
                        ),
                    ),
                ),
            )
        }

        onNodeWithText("종료").assertIsDisplayed()
        onNodeWithText("13 : 9").assertIsDisplayed()
        onNodeWithText("VS").assertDoesNotExist()
        onNodeWithText("Paper Rex").assertDoesNotExist()
        onNodeWithText("Fnatic").assertIsDisplayed()
    }

    @Test
    fun completedCardWithoutScoreShowsTerminalMarkerInsteadOfScheduledMarker() = runComposeUiTest {
        setContent {
            MatchesContentFixture(
                uiState = MatchesUiState(
                    selectedTab = MatchesTab.RESULTS,
                    results = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Content(
                            listOf(MatchDateGroup("YESTERDAY", listOf(completedMatch.copy(homeScore = null, awayScore = null)))),
                        ),
                    ),
                ),
            )
        }

        onNodeWithText("—").assertIsDisplayed()
        onNodeWithText("VS").assertDoesNotExist()
    }

    @Test
    fun refreshFailureShowsInitialErrorAndRetryInvokesInitialRetry() = runComposeUiTest {
        var retries = 0
        var loadMoreRequests = 0
        setContent {
            MatchesContentFixture(
                uiState = MatchesUiState(
                    upcomingLive = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Error,
                    ),
                ),
                onRetryInitial = { retries += 1 },
                onLoadMore = { loadMoreRequests += 1 },
            )
        }

        onNodeWithText("Paper Rex").assertDoesNotExist()
        onNodeWithText("경기 목록을 불러오지 못했습니다.\n네트워크 상태를 확인하고 다시 시도해 주세요.")
            .assertIsDisplayed()
        waitForIdle()
        assertEquals(0, loadMoreRequests)
        onNodeWithTag(MATCHES_INITIAL_RETRY_TAG).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun activeRefreshClearsCardsAndExposesLoadingProgressState() = runComposeUiTest {
        setContent {
            MatchesContentFixture(
                uiState = MatchesUiState(
                    upcomingLive = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Loading,
                        isRefreshing = true,
                    ),
                ),
            )
        }

        onNodeWithText("Paper Rex").assertDoesNotExist()
        onNodeWithTag(MATCHES_LOADING_TAG).assertExists()
        onNodeWithTag(MATCHES_REFRESHING_TAG).assertExists()
    }

    @Test
    fun paginationLoadingPreservesCardsAndShowsFooterProgress() = runComposeUiTest {
        setContent {
            MatchesContentFixture(
                uiState = contentState(upcomingMatch).copy(
                    upcomingLive = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Content(
                            listOf(MatchDateGroup("TODAY, AUG 23", listOf(upcomingMatch))),
                        ),
                        isLoadingMore = true,
                    ),
                ),
            )
        }

        onNodeWithText("Paper Rex").assertIsDisplayed()
        onNodeWithTag(MATCHES_PAGINATION_LOADING_TAG).assertIsDisplayed()
    }

    @Test
    fun paginationStatesPreserveCardsAndErrorRetryInvokesLoadMoreRetry() = runComposeUiTest {
        var paginationRetries = 0
        setContent {
            MatchesContentFixture(
                uiState = contentState(upcomingMatch).copy(
                    upcomingLive = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Content(
                            listOf(MatchDateGroup("TODAY, AUG 23", listOf(upcomingMatch))),
                        ),
                        hasPaginationError = true,
                    ),
                ),
                onRetryLoadMore = { paginationRetries += 1 },
            )
        }

        onNodeWithText("Paper Rex").assertIsDisplayed()
        onNodeWithText("경기를 더 불러오지 못했습니다.").assertIsDisplayed()
        onNodeWithTag(MATCHES_PAGINATION_RETRY_TAG).performClick()
        assertEquals(1, paginationRetries)
    }

    @Test
    fun tabSelectionChangesOnlyTheSelectedFeed() = runComposeUiTest {
        var selectedTab by mutableStateOf(MatchesTab.UPCOMING_LIVE)
        val state = MatchesUiState(
            upcomingLive = MatchesFeedUiState(
                contentState = MatchesFeedContentState.Content(
                    listOf(MatchDateGroup("TODAY", listOf(upcomingMatch))),
                ),
            ),
            results = MatchesFeedUiState(
                contentState = MatchesFeedContentState.Content(
                    listOf(MatchDateGroup("YESTERDAY", listOf(completedMatch))),
                ),
            ),
        )
        setContent {
            MatchesContentFixture(
                uiState = state.copy(selectedTab = selectedTab),
                onSelectTab = { selectedTab = it },
            )
        }

        onNodeWithText("Paper Rex").assertIsDisplayed()
        onNodeWithText("Fnatic").assertDoesNotExist()
        onNodeWithTag(MATCHES_TABS_TAG).assertIsDisplayed()
        onNodeWithTag(matchTabTag(MatchesTab.UPCOMING_LIVE)).assertIsSelected()
        onNodeWithText("결과").performClick()
        onNodeWithTag(matchTabTag(MatchesTab.RESULTS)).assertIsSelected()
        onNodeWithText("Fnatic").assertIsDisplayed()
        onNodeWithText("Paper Rex").assertDoesNotExist()
    }

    @Test
    fun exceptionalStatusesRemainExplicitAndAccessibleInCompactCards() = runComposeUiTest {
        val postponedMatch = upcomingMatch.copy(
            id = "match-postponed-303",
            status = MatchStatus.POSTPONED,
            relativeTimeLabel = "2시간 후",
        )
        val cancelledMatch = upcomingMatch.copy(
            id = "match-cancelled-404",
            status = MatchStatus.CANCELLED,
        )
        val unavailableMatch = upcomingMatch.copy(
            id = "match-unavailable-505",
            status = MatchStatus.UNAVAILABLE,
        )
        setContent {
            MatchesContentFixture(
                uiState = MatchesUiState(
                    upcomingLive = MatchesFeedUiState(
                        contentState = MatchesFeedContentState.Content(
                            listOf(
                                MatchDateGroup(
                                    "TODAY",
                                    listOf(postponedMatch, cancelledMatch, unavailableMatch),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        onNodeWithText("연기").assertIsDisplayed()
        onNodeWithText("취소").assertIsDisplayed()
        onNodeWithText("정보 없음").assertIsDisplayed()
        onNodeWithText("2시간 후").assertIsDisplayed()
        onNodeWithTag(matchCardTag(postponedMatch.id)).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "경기가 연기되었습니다",
            ),
        )
        onNodeWithTag(matchCardTag(cancelledMatch.id)).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "경기가 취소되었습니다",
            ),
        )
        onNodeWithTag(matchCardTag(unavailableMatch.id)).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "경기 정보가 없습니다",
            ),
        )
    }

    @Test
    fun nearingTheEndRequestsTheNextPage() = runComposeUiTest {
        var loadMoreRequests = 0
        setContent {
            MatchesContentFixture(
                uiState = contentState(upcomingMatch),
                onLoadMore = { loadMoreRequests += 1 },
            )
        }

        waitUntil(timeoutMillis = 5_000) { loadMoreRequests > 0 }
        assertEquals(1, loadMoreRequests)
    }

    @androidx.compose.runtime.Composable
    private fun MatchesContentFixture(
        uiState: MatchesUiState,
        onSearch: () -> Unit = {},
        onSelectTab: (MatchesTab) -> Unit = {},
        onMatchClick: (String) -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetryInitial: () -> Unit = {},
        onLoadMore: () -> Unit = {},
        onRetryLoadMore: () -> Unit = {},
    ) {
        VlrTheme {
            MatchesContent(
                uiState = uiState,
                upcomingLiveListState = rememberLazyListState(),
                resultsListState = rememberLazyListState(),
                onSearch = onSearch,
                onSelectTab = onSelectTab,
                onMatchClick = onMatchClick,
                onRefresh = onRefresh,
                onRetryInitial = onRetryInitial,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
            )
        }
    }

    private fun contentState(match: MatchSummary) = MatchesUiState(
        upcomingLive = MatchesFeedUiState(
            contentState = MatchesFeedContentState.Content(
                listOf(MatchDateGroup("TODAY, AUG 23", listOf(match))),
            ),
        ),
    )

    private companion object {
        val upcomingMatch = MatchSummary(
            id = "match-upcoming-101",
            status = MatchStatus.LIVE,
            timeLabel = "10:30 AM",
            relativeTimeLabel = "IN 2 HOURS",
            homeTeam = MatchTeam(name = "Paper Rex", id = "team-prx"),
            awayTeam = MatchTeam(name = "Gen.G", id = "team-geng"),
            homeScore = null,
            awayScore = null,
            event = MatchEvent(
                name = "Valorant Champions 2026",
                series = "Playoffs · Upper Final",
                id = "event-champions-2026",
            ),
        )

        val completedMatch = MatchSummary(
            id = "match-result-202",
            status = MatchStatus.COMPLETED,
            timeLabel = "8:00 PM",
            relativeTimeLabel = null,
            homeTeam = MatchTeam(name = "Fnatic", id = "team-fnc"),
            awayTeam = MatchTeam(name = "Sentinels", id = "team-sen"),
            homeScore = 13,
            awayScore = 9,
            event = MatchEvent(
                name = "Valorant Champions 2026",
                series = "Group Stage",
                id = "event-champions-2026",
            ),
        )
    }
}
