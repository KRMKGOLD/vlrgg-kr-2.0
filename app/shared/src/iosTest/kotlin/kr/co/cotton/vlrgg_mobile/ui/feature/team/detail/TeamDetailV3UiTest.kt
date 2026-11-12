package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import kr.co.cotton.vlrgg_mobile.ui.format.LocalDeviceClock
import kotlinx.datetime.LocalTime
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.theme.initializeVlrMaterial3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v3 team detail (`.pen` 50): Korean sections and dates, canvas gaps, large fonts, RTL and favorite rollback. */
@OptIn(ExperimentalTestApi::class)
class TeamDetailV3UiTest {

    @Test
    fun scheduleNewsDateAndRemainingTimeAreKoreanWithSourceFallback() = runComposeUiTest {
        setContent { Fixture(TeamDetailUiState(TeamDetailContentState.Content(team))) }

        assertRowText("9월 24일 오후 6:00", UPCOMING_ID)
        assertRowText("1일 6시간 후", UPCOMING_ID)
        // A non remaining-time source keeps its text.
        assertRowText("LIVE", LIVE_ID)
        assertRowText("2026/09/20 3:00 pm", LIVE_ID)
        scrollToTag(teamMatchCardTag(LAST_YEAR_ID))
        assertRowText("${YEAR - 1}년 8월 28일", LAST_YEAR_ID)
        assertRowText("2 : 1", LAST_YEAR_ID)
        assertRowText("0 : 2", SOURCE_DATE_ID)
        assertRowText("2026/07/01", SOURCE_DATE_ID)
        onNodeWithText("1d 6h").assertDoesNotExist()

        scrollToTag(teamNewsRowTag("1", "korean-date"))
        onNodeWithTag(teamNewsPublishedDateTag("1", "korean-date"), useUnmergedTree = true)
            .assertLayoutText("2026년 9월 15일")
        onNodeWithTag(teamNewsPublishedDateTag("2", "source-date"), useUnmergedTree = true)
            .assertLayoutText("2026/09/02")
    }

    @Test
    fun sectionsUseKoreanHeadingsCountsAndEightDpCanvasGaps() =
        runSkikoComposeUiTest(size = Size(360f, 2400f)) {
            setContent { Fixture(TeamDetailUiState(TeamDetailContentState.Content(team))) }

            listOf("예정 경기" to "2", "최근 경기" to "2", "선수" to "1", "스태프" to "1", "뉴스" to "2").forEach { (title, count) ->
                val heading = onNode(hasText(title) and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val countNode = onNode(
                    hasText(count) and hasAnyAncestor(hasTestTag(sectionTagOf(title))),
                    useUnmergedTree = true,
                ).fetchSemanticsNode().boundsInRoot
                assertTrue(countNode.left > heading.right, "$title count is not after the title")
            }
            // Each section title sits 8dp under the hero or the previous section's last row.
            listOf(
                TEAM_DETAIL_HEADER_TAG to TEAM_DETAIL_UPCOMING_SECTION_TAG,
                teamMatchCardTag(LIVE_ID) to TEAM_DETAIL_RECENT_SECTION_TAG,
                teamMatchCardTag(SOURCE_DATE_ID) to TEAM_DETAIL_PLAYERS_SECTION_TAG,
                teamPlayerRowTag("4462") to TEAM_DETAIL_STAFF_SECTION_TAG,
                teamStaffRowTag("775") to TEAM_DETAIL_NEWS_SECTION_TAG,
            ).forEach { (above, below) ->
                val gap = bounds(below).top - bounds(above).bottom
                assertEquals(8f, gap, 0.5f, "gap between $above and $below")
            }
            // Section rows follow their title without a gap.
            assertEquals(bounds(TEAM_DETAIL_NEWS_SECTION_TAG).bottom, bounds(teamNewsRowTag("1", "korean-date")).top, 0.5f)
        }

    @Test
    fun sparseTeamKeepsEveryTitleWithoutCountsAndShowsSectionMessages() = runComposeUiTest {
        setContent {
            Fixture(
                TeamDetailUiState(
                    TeamDetailContentState.Content(
                        team.copy(
                            upcomingMatches = emptyList(),
                            recentMatches = emptyList(),
                            players = emptyList(),
                            staff = emptyList(),
                            news = emptyList(),
                        ),
                    ),
                ),
            )
        }

        listOf(
            TEAM_DETAIL_UPCOMING_SECTION_TAG to "예정된 경기가 없습니다",
            TEAM_DETAIL_RECENT_SECTION_TAG to "최근 경기 기록이 없습니다",
            TEAM_DETAIL_PLAYERS_SECTION_TAG to "선수 정보가 없습니다",
            TEAM_DETAIL_STAFF_SECTION_TAG to "스태프 정보가 없습니다",
            TEAM_DETAIL_NEWS_SECTION_TAG to "관련 뉴스가 없습니다",
        ).forEach { (tag, message) ->
            scrollToTag(tag)
            onNode(hasText(message) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
            // `.pen` 50-4: an empty section shows no count.
            onNode(hasText("0") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun repeatedSourceRowsKeepDistinctLazyKeys() = runComposeUiTest {
        val article = team.news.first()
        setContent {
            Fixture(TeamDetailUiState(TeamDetailContentState.Content(team.copy(news = List(3) { article }))))
        }

        // A repeated row must not crash the list with a duplicate key.
        scrollToTag(TEAM_DETAIL_NEWS_SECTION_TAG)
        onNode(hasText("3") and hasAnyAncestor(hasTestTag(TEAM_DETAIL_NEWS_SECTION_TAG)), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun longNamesDatesAndTitlesWrapWithoutClippingOnNarrowScreensAndDoubleFonts() {
        val texts = listOf(
            LONG_TEAM_NAME,
            "KRX · South Korea",
            "예정 경기",
            "vs $LONG_OPPONENT",
            "VCT Pacific 2026 · Playoffs–Upper Final",
            "1일 6시간 후",
            "9월 24일 오후 6:00",
            "MaKo",
            "Kim Myeong-gwan · Initiator",
            "termi",
            "KIWOOM DRX advance to the Pacific playoffs after a long reverse sweep",
            "2026년 9월 15일",
        )
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 6000f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            Fixture(TeamDetailUiState(TeamDetailContentState.Content(team)))
                        }
                    }
                    texts.forEach { text -> assertWhole(text, "width=$width fontScale=$scale", width) }
                }
            }
        }
    }

    @Test
    fun otherYearScheduleWrapsBesideTheMatchInsteadOfSqueezingIt() {
        val dateText = "${YEAR - 1}년 12월 24일 오후 12:00"
        val match = team.upcomingMatches.first().copy(
            opponentName = "Paper Rex",
            scheduledDate = LocalDate(YEAR - 1, 12, 24),
            scheduledTime = LocalTime(12, 0),
        )
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 1.5f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 4000f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            Fixture(TeamDetailUiState(TeamDetailContentState.Content(team.copy(upcomingMatches = listOf(match)))))
                        }
                    }
                    val label = "width=$width fontScale=$scale"
                    val row = onNodeWithTag(teamMatchCardTag(UPCOMING_ID)).fetchSemanticsNode().boundsInRoot
                    listOf("vs Paper Rex", "VCT Pacific 2026 · Playoffs–Upper Final").forEach { text ->
                        val node = rowNode(text, UPCOMING_ID)
                        assertWholeNode(node, text, label, width)
                        // The match identity keeps a readable share of the row next to the schedule.
                        val oneLine = node.textLayout().multiParagraph.intrinsics.maxIntrinsicWidth
                        val wanted = minOf(oneLine, row.width * 0.4f)
                        assertTrue(node.boundsInRoot.width >= wanted - 0.5f, "$label '$text' is squeezed: ${node.boundsInRoot}")
                    }
                    listOf("1일 6시간 후", dateText).forEach { text ->
                        assertWholeNode(rowNode(text, UPCOMING_ID), text, label, width)
                    }
                }
            }
        }
    }

    @Test
    fun resumingInANewYearAddsTheYearToLastYearsSchedule() = runComposeUiTest {
        val clock = MutableClock(at(2026, 12, 31, 21, 0))
        val owner = ResumedLifecycleOwner()
        val match = team.upcomingMatches.first().copy(scheduledDate = LocalDate(2026, 12, 24), scheduledTime = LocalTime(18, 0))
        setContent {
            CompositionLocalProvider(LocalDeviceClock provides clock, LocalLifecycleOwner provides owner) {
                Fixture(TeamDetailUiState(TeamDetailContentState.Content(team.copy(upcomingMatches = listOf(match)))))
            }
        }
        assertRowText("12월 24일 오후 6:00", UPCOMING_ID)

        owner.registry.currentState = Lifecycle.State.STARTED
        clock.now = at(2027, 1, 1, 9, 0)
        owner.registry.currentState = Lifecycle.State.RESUMED
        waitForIdle()
        assertRowText("2026년 12월 24일 오후 6:00", UPCOMING_ID)
    }

    @Test
    fun rtlMirrorsTheHeroSectionTitlesAndMatchRows() =
        runSkikoComposeUiTest(size = Size(360f, 2400f)) {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Fixture(TeamDetailUiState(TeamDetailContentState.Content(team)))
                }
            }

            val name = onNode(hasText(LONG_TEAM_NAME) and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(360f - 16f - 72f - 16f, name.right, 1f)
            val section = onNode(hasText("예정 경기") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(360f - 16f, section.right, 1f)
            val title = textBounds("vs $LONG_OPPONENT", UPCOMING_ID)
            val status = textBounds("1일 6시간 후", UPCOMING_ID)
            val date = textBounds("9월 24일 오후 6:00", UPCOMING_ID)
            assertTrue(status.right < title.left, "status is not on the left in RTL")
            assertTrue(date.right < title.left, "date is not on the left in RTL")
        }

    @Test
    fun favoriteFailuresRollTheStarBackAndOfferRetry() {
        initializeVlrMaterial3()
        runComposeUiTest {
            var favorite by mutableStateOf(
                TeamFavoriteUiState(isRestored = true, isFavorite = false, failedIntent = TeamFavoriteMutationIntent.Add),
            )
            var retries = 0
            setContent {
                Fixture(
                    TeamDetailUiState(TeamDetailContentState.Content(team), favorite),
                    onFavoriteRetry = { retries += 1 },
                )
            }

            // `.pen` 50-6: an add failure leaves the outline star and a retry Snackbar.
            onNodeWithTag(TEAM_DETAIL_FAVORITE_OUTLINE_TAG).assertIsDisplayed()
            onNodeWithText("즐겨찾기 추가에 실패했습니다.").assertIsDisplayed()
            onNodeWithText("재시도").performClick()
            assertEquals(1, retries)

            // `.pen` 50-7: a remove failure restores the filled star.
            favorite = TeamFavoriteUiState(isRestored = true, isFavorite = true, failedIntent = TeamFavoriteMutationIntent.Remove)
            waitForIdle()
            onNodeWithTag(TEAM_DETAIL_FAVORITE_FILLED_TAG).assertIsDisplayed()
            onNodeWithText("즐겨찾기 해제에 실패했습니다.").assertIsDisplayed()
            onNodeWithText("재시도").assertIsDisplayed()
        }
    }

    @Test
    fun scrollPositionSurvivesFavoriteChangesAndRefreshedContent() = runComposeUiTest {
        var uiState by mutableStateOf(
            TeamDetailUiState(TeamDetailContentState.Content(team), TeamFavoriteUiState(isRestored = true)),
        )
        lateinit var listState: LazyListState
        setContent {
            listState = rememberLazyListState()
            VlrTheme {
                TeamDetailContent(
                    uiState = uiState,
                    listState = listState,
                    onBack = {},
                    onMatchClick = {},
                    onPlayerClick = {},
                    onNewsClick = { _, _ -> },
                    onRetry = {},
                    onFavoriteToggle = {},
                    onFavoriteRetry = {},
                    onFavoriteRestoreRetry = {},
                    onFavoriteErrorDismiss = {},
                )
            }
        }

        scrollToTag(TEAM_DETAIL_NEWS_SECTION_TAG)
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        assertTrue(index > 0)

        uiState = uiState.copy(favorite = TeamFavoriteUiState(isRestored = true, isFavorite = true))
        // A refetch with the same sections keeps the place.
        uiState = uiState.copy(
            contentState = TeamDetailContentState.Content(
                team.copy(players = team.players.map { it.copy(realName = "Kim Myeong-kwan") }),
            ),
        )
        waitForIdle()
        assertEquals(index, listState.firstVisibleItemIndex)
        assertEquals(offset, listState.firstVisibleItemScrollOffset)
    }

    private fun ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.textBounds(text: String, matchId: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(teamMatchCardTag(matchId))), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.assertRowText(text: String, matchId: String) {
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(teamMatchCardTag(matchId))), useUnmergedTree = true)
            .assertExists("'$text' is not in match $matchId")
    }

    private fun ComposeUiTest.scrollToTag(tag: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun ComposeUiTest.rowNode(text: String, matchId: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(teamMatchCardTag(matchId))), useUnmergedTree = true)
            .fetchSemanticsNode()

    private fun assertWholeNode(node: SemanticsNode, text: String, label: String, width: Float) {
        val layout = node.textLayout()
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
        assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
        assertTrue(node.boundsInRoot.right <= width + 0.5f, "$label '$text' leaves the screen")
    }

    private fun ComposeUiTest.assertWhole(text: String, label: String, width: Float) {
        val nodes = onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty(), "$label '$text' is missing")
        nodes.forEach { node ->
            val layout = node.textLayout()
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
            assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
            assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
            assertTrue(node.boundsInRoot.right <= width + 0.5f, "$label '$text' leaves the screen")
        }
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    private fun SemanticsNodeInteraction.assertLayoutText(text: String) {
        val node = fetchSemanticsNode()
        assertEquals(text, node.textLayout().layoutInput.text.text)
    }

    private fun sectionTagOf(title: String) = when (title) {
        "예정 경기" -> TEAM_DETAIL_UPCOMING_SECTION_TAG
        "최근 경기" -> TEAM_DETAIL_RECENT_SECTION_TAG
        "선수" -> TEAM_DETAIL_PLAYERS_SECTION_TAG
        "스태프" -> TEAM_DETAIL_STAFF_SECTION_TAG
        else -> TEAM_DETAIL_NEWS_SECTION_TAG
    }

    @Composable
    private fun Fixture(
        uiState: TeamDetailUiState,
        onFavoriteRetry: () -> Unit = {},
    ) {
        VlrTheme {
            TeamDetailContent(
                uiState = uiState,
                listState = rememberLazyListState(),
                onBack = {},
                onMatchClick = {},
                onPlayerClick = {},
                onNewsClick = { _, _ -> },
                onRetry = {},
                onFavoriteToggle = {},
                onFavoriteRetry = onFavoriteRetry,
                onFavoriteRestoreRetry = {},
                onFavoriteErrorDismiss = {},
            )
        }
    }

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class ResumedLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.currentSystemDefault())

    private companion object {
        val YEAR = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
        const val UPCOMING_ID = "1"
        const val LIVE_ID = "2"
        const val LAST_YEAR_ID = "3"
        const val SOURCE_DATE_ID = "4"
        const val LONG_TEAM_NAME = "KIWOOM DRX Official Esports Organization Team"
        const val LONG_OPPONENT = "Paper Rex International Championship Roster"

        val team = TeamDetail(
            id = "8185",
            name = LONG_TEAM_NAME,
            tag = "KRX",
            country = "South Korea",
            upcomingMatches = listOf(
                TeamMatch(
                    id = UPCOMING_ID,
                    eventName = "VCT Pacific 2026",
                    eventStage = "Playoffs–Upper Final",
                    teamName = LONG_TEAM_NAME,
                    opponentName = LONG_OPPONENT,
                    statusText = "1d 6h",
                    scheduledAtText = "2026/09/24 6:00 pm",
                    scheduledDate = LocalDate(YEAR, 9, 24),
                    scheduledTime = LocalTime(18, 0),
                ),
                TeamMatch(
                    id = LIVE_ID,
                    eventName = "VCT Pacific 2026",
                    eventStage = null,
                    teamName = LONG_TEAM_NAME,
                    opponentName = "T1",
                    statusText = "LIVE",
                    scheduledAtText = "2026/09/20 3:00 pm",
                ),
            ),
            recentMatches = listOf(
                TeamMatch(
                    id = LAST_YEAR_ID,
                    eventName = "VCT Pacific",
                    eventStage = "Stage 2",
                    teamName = LONG_TEAM_NAME,
                    opponentName = "Gen.G",
                    statusText = "2 : 1",
                    scheduledAtText = "2025/08/28",
                    scheduledDate = LocalDate(YEAR - 1, 8, 28),
                ),
                TeamMatch(
                    id = SOURCE_DATE_ID,
                    eventName = "VCT Pacific",
                    eventStage = null,
                    teamName = LONG_TEAM_NAME,
                    opponentName = "DRX",
                    statusText = "0 : 2",
                    scheduledAtText = "2026/07/01",
                ),
            ),
            players = listOf(TeamRosterMember("4462", "MaKo", "Kim Myeong-gwan", listOf("Initiator"))),
            staff = listOf(TeamRosterMember("775", "termi", null, listOf("Head Coach"))),
            news = listOf(
                TeamNews(
                    articleId = "1",
                    slug = "korean-date",
                    title = "KIWOOM DRX advance to the Pacific playoffs after a long reverse sweep",
                    publishedDateText = "2026/09/15",
                    publishedDate = LocalDate(2026, 9, 15),
                ),
                TeamNews(
                    articleId = "2",
                    slug = "source-date",
                    title = "MaKo on the road to Champions 2026",
                    publishedDateText = "2026/09/02",
                ),
            ),
        )
    }
}
