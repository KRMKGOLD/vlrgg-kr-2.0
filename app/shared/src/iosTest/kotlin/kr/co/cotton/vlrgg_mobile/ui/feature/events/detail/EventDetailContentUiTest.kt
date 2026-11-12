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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventMonthDay
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kotlin.test.Test
import kotlin.test.assertFalse
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
            // Three matches a day, so date headers and canvas gaps sit between the restored rows.
            val matches = (1..30).map { match.copy(id = "match-$it", date = LocalDate(2026, 9, 1 + (it - 1) / 3)) }
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

    @Test
    fun identityLoadingIsAHeroSkeletonWithRowSkeletonsAndNoTabs() = runComposeUiTest {
        setContent { Fixture(uiState = EventDetailUiState()) }

        val loading = onNodeWithTag(EVENT_DETAIL_LOADING_TAG).fetchSemanticsNode()
        assertEquals("이벤트 정보를 불러오는 중", loading.config[SemanticsProperties.StateDescription])
        assertEquals(ProgressBarRangeInfo.Indeterminate, loading.config[SemanticsProperties.ProgressBarRangeInfo])
        EventDetailTab.entries.forEach { onNodeWithTag(eventDetailTabTag(it)).assertDoesNotExist() }
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(1)
    }

    @Test
    fun eachTabLoadingIsASkeletonWithProgressSemanticsInsteadOfASpinner() = runComposeUiTest {
        var state by mutableStateOf(
            populatedState.copy(
                matches = EventMatchesContentState.Loading,
                news = EventNewsContentState.Loading,
                stats = EventStatsContentState.Loading,
            ),
        )
        setContent { Fixture(uiState = state) }

        listOf(
            EventDetailTab.MATCHES to "경기를 불러오는 중",
            EventDetailTab.NEWS to "뉴스를 불러오는 중",
            EventDetailTab.STATS to "통계를 불러오는 중",
        ).forEach { (tab, description) ->
            state = state.copy(selectedTab = tab)
            waitForIdle()
            // One progress node per tab: the skeleton, with no extra indeterminate spinner.
            val progress = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .fetchSemanticsNodes()
            assertEquals(1, progress.size, "$tab progress nodes")
            assertEquals(description, progress.single().config[SemanticsProperties.StateDescription])
            onNodeWithText("Masters Seoul").assertIsDisplayed()
        }
    }

    @Test
    fun matchesTabGroupsConsecutiveDatesUnderKoreanDateHeadersInResponseOrder() =
        runSkikoComposeUiTest(size = Size(360f, 1600f)) {
            val finished = match.copy(
                id = "finished",
                status = MatchStatus.COMPLETED,
                time = LocalTime(18, 0),
                timeLabel = "6:00 PM",
                homeScore = 2,
                awayScore = 0,
                date = LocalDate(2026, 9, 13),
            )
            val matches = listOf(
                finished,
                match.copy(id = "same-day", date = LocalDate(2026, 9, 13)),
                match.copy(id = "next-day", date = LocalDate(2026, 9, 14)),
            )
            setContent { Fixture(uiState = populatedState.copy(matches = EventMatchesContentState.Content(matches))) }

            val firstHeader = onNode(hasText("9월 13일 (일)") and isHeading(), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val secondHeader = onNode(hasText("9월 14일 (월)") and isHeading(), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            onAllNodesWithText("한국 시간", useUnmergedTree = true).assertCountEquals(2)
            val rows = matches.map { onNodeWithTag(matchCardTag(it.id)).fetchSemanticsNode().boundsInRoot }
            assertTrue(firstHeader.bottom <= rows[0].top && rows[0].bottom <= rows[1].top)
            assertTrue(rows[1].bottom < secondHeader.top && secondHeader.bottom <= rows[2].top)
            // 8dp canvas gap, then the 20dp top padding of the next header.
            assertEquals(rows[1].bottom + 8f + 20f, secondHeader.top, 1f)
            // A finished match shows its Korean start time with `종료`, as in the match list results.
            onNode(hasText("오후 6:00") and hasAnyAncestor(hasTestTag(matchCardTag("finished"))), useUnmergedTree = true)
                .assertExists()
            onNode(hasText("종료") and hasAnyAncestor(hasTestTag(matchCardTag("finished"))), useUnmergedTree = true)
                .assertExists()
        }

    @Test
    fun matchesWithoutDatesKeepTheHeaderlessList() = runComposeUiTest {
        setContent { Fixture(uiState = populatedState) }

        onNodeWithTag(matchCardTag(match.id)).assertIsDisplayed()
        onNodeWithText("한국 시간").assertDoesNotExist()
        onAllNodes(isHeading()).fetchSemanticsNodes().forEach {
            assertEquals(listOf("Masters Seoul"), it.config[SemanticsProperties.Text].map { text -> text.text })
        }
    }

    @Test
    fun newsTabShowsTheKoreanPublishedDateWhenStructured() = runComposeUiTest {
        val dated = news.copy(articleId = "102", publishedAt = "2026/09/15", publishedDate = LocalDate(2026, 9, 15))
        setContent {
            Fixture(
                uiState = populatedState.copy(
                    selectedTab = EventDetailTab.NEWS,
                    news = EventNewsContentState.Content(listOf(dated, news)),
                ),
            )
        }

        onNodeWithText("2026년 9월 15일", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("2026/09/15", substring = true, useUnmergedTree = true).assertDoesNotExist()
        // Without the structured date the source text stays.
        onNodeWithText(news.publishedAt, substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun statsUseTheKoreanPlayerColumnAndTheScrollHint() =
        runSkikoComposeUiTest(size = Size(360f, 900f)) {
            setContent { Fixture(uiState = populatedState.copy(selectedTab = EventDetailTab.STATS)) }

            onNodeWithText("선수").assertExists()
            onNodeWithText("Player").assertDoesNotExist()
            onNodeWithText("옆으로 밀어 지표 더 보기 · — 는 기록 없음").assertExists()
        }

    @Test
    fun heroShowsTheKoreanPeriodReadAsARangeAndTheStatusChip() = runComposeUiTest {
        setContent {
            Fixture(
                uiState = populatedState.copy(
                    identity = EventIdentityContentState.Content(
                        event.copy(
                            status = EventStatus.ONGOING,
                            startDate = EventMonthDay(8, 20),
                            endDate = EventMonthDay(9, 4),
                        ),
                    ),
                ),
            )
        }

        onNodeWithText("VCT 2026 · 8월 20일 ~ 9월 4일 · Seoul", useUnmergedTree = true)
            .assert(hasContentDescriptionExactly("VCT 2026 · 8월 20일부터 9월 4일까지 · Seoul"))
        onNodeWithText("Aug 20—Sep 4", substring = true, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("진행 중", useUnmergedTree = true).assertExists()
        onNode(hasText("Masters Seoul") and isHeading(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun heroWithoutStructuredPeriodKeepsTheSourceLabel() = runComposeUiTest {
        setContent { Fixture(uiState = populatedState) }

        onNodeWithText("VCT 2026 · Aug 20—Sep 4 · Seoul", useUnmergedTree = true).assertExists()
    }

    @Test
    fun heroDateHeaderAndTabsFitWithoutClippingOnNarrowScreensAndDoubleFonts() {
        val longEvent = event.copy(
            name = "VCT 2027: EMEA Kickoff - Türkiye Qualifier Presented by Example",
            startDate = EventMonthDay(12, 28),
            endDate = EventMonthDay(1, 14),
        )
        val dated = match.copy(date = LocalDate(2026, 12, 30))
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 2400f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            Fixture(
                                uiState = populatedState.copy(
                                    identity = EventIdentityContentState.Content(longEvent),
                                    matches = EventMatchesContentState.Content(listOf(dated)),
                                ),
                            )
                        }
                    }

                    val label = "width=$width fontScale=$scale"
                    listOf(
                        longEvent.name,
                        "VCT 2026 · 12월 28일 ~ 1월 14일 · Seoul",
                        "12월 30일 (수)",
                        "한국 시간",
                        "경기",
                        "뉴스",
                        "통계",
                    ).forEach { text ->
                        val node = onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
                        val layout = node.textLayout()
                        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
                        assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
                        assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
                        assertTrue(node.boundsInRoot.left >= -0.5f && node.boundsInRoot.right <= width + 0.5f, "$label '$text' leaves the screen")
                    }
                }
            }
        }
    }

    @Test
    fun rtlMirrorsTheHeroDateHeaderAndTabs() =
        runSkikoComposeUiTest(size = Size(360f, 1200f)) {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Fixture(
                        uiState = populatedState.copy(
                            matches = EventMatchesContentState.Content(listOf(match.copy(date = LocalDate(2026, 9, 13)))),
                        ),
                    )
                }
            }

            val name = onNode(hasText("Masters Seoul") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // The 72dp logo slot leads on the right with a 16dp inset and 16dp gap.
            assertEquals(360f - 16f - 72f - 16f, name.right, 1f)
            val header = onNode(hasText("9월 13일 (일)") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val timeZone = onNodeWithText("한국 시간", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(360f - 16f, header.right, 1f)
            assertTrue(timeZone.right < header.left, "time zone meta is not on the trailing (left) side")
            val matchesTab = onNodeWithTag(eventDetailTabTag(EventDetailTab.MATCHES)).fetchSemanticsNode().boundsInRoot
            val statsTab = onNodeWithTag(eventDetailTabTag(EventDetailTab.STATS)).fetchSemanticsNode().boundsInRoot
            assertTrue(matchesTab.left > statsTab.left, "the first tab is not on the right")
        }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
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
