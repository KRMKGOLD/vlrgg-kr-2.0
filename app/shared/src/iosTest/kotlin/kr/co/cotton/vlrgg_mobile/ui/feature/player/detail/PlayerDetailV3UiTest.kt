package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.LocalDate
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerCurrentTeam
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerProfile
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchTeam
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSortIconTag
import kr.co.cotton.vlrgg_mobile.ui.component.nextStatsSort
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.theme.initializeVlrMaterial3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v3 player detail (`.pen` 51): Korean sections and dates, sort cycle, large fonts, RTL and favorite rollback. */
@OptIn(ExperimentalTestApi::class)
class PlayerDetailV3UiTest {

    @Test
    fun sectionsUseKoreanHeadingsMetaCountAndEightDpCanvasGaps() =
        runSkikoComposeUiTest(size = Size(360f, 2400f)) {
            setContent { Fixture(player) }

            listOf("현재 팀", "요원별 기록", "최근 경기").forEach {
                onNode(hasText(it) and isHeading(), useUnmergedTree = true).assertExists()
            }
            inSection("전체 기간", PLAYER_DETAIL_STATS_SECTION_TAG)
            inSection("최대 5경기", PLAYER_DETAIL_MATCHES_SECTION_TAG)
            inSection("3", PLAYER_DETAIL_MATCHES_SECTION_TAG)
            inSection("요원", PLAYER_DETAIL_STATS_SECTION_TAG)
            val tags = listOf(
                PLAYER_DETAIL_HEADER_TAG,
                PLAYER_DETAIL_TEAM_SECTION_TAG,
                PLAYER_DETAIL_STATS_SECTION_TAG,
                PLAYER_DETAIL_MATCHES_SECTION_TAG,
            )
            tags.zipWithNext().forEach { (above, below) ->
                val gap = bounds(below).top - bounds(above).bottom
                assertEquals(8f, gap, 0.5f, "gap between $above and $below")
            }
        }

    @Test
    fun sparsePlayerKeepsTitlesWithoutMetaOrCountAndShowsSectionMessages() = runComposeUiTest {
        setContent { Fixture(player.copy(currentTeam = null, agentStats = emptyList(), recentMatches = emptyList())) }

        listOf(
            PLAYER_DETAIL_TEAM_SECTION_TAG to "소속 팀 정보가 없습니다",
            PLAYER_DETAIL_STATS_SECTION_TAG to "에이전트 통계 정보가 없습니다",
            PLAYER_DETAIL_MATCHES_SECTION_TAG to "최근 경기 기록이 없습니다",
        ).forEach { (tag, message) -> inSection(message, tag) }
        // `.pen` 51-4: the meta and count only describe rows that are shown.
        onNodeWithText("전체 기간").assertDoesNotExist()
        onNodeWithText("최대 5경기").assertDoesNotExist()
        onNodeWithText("0").assertDoesNotExist()
    }

    @Test
    fun recentMatchDatesAreKoreanWithSourceFallbackAndOutcomesAreWritten() = runComposeUiTest {
        setContent { Fixture(player) }

        inRow("7월 12일", WIN_ID)
        inRow("승리", WIN_ID)
        inRow("${YEAR - 1}년 7월 10일", LOSS_ID)
        inRow("패배", LOSS_ID)
        inRow("2026/06/28", UNKNOWN_ID)
        inRow("결과 미정", UNKNOWN_ID)
        // The written outcome also ends the row's spoken label.
        onNodeWithContentDescription("경기 상세: Nongshim RedForce 2, BBL Esports 0, 승리").assertExists()
        onNodeWithContentDescription("경기 상세: Nongshim RedForce 0, Paper Rex 2, 패배").assertExists()
        onNodeWithContentDescription("경기 상세: Nongshim RedForce 대 T1, 결과 미정").assertExists()
    }

    @Test
    fun ratingHeaderCyclesDescendingAscendingAndOffWithArrowAndSpokenDirection() = runComposeUiTest {
        var sort by mutableStateOf<StatsSort<PlayerAgentStatsSortColumn>?>(null)
        setContent { Fixture(player, sort = sort, onSortColumn = { sort = nextStatsSort(sort, it) }) }
        val rating = onNodeWithTag(playerAgentMetricHeaderTag("Rating"))

        assertAgentOrder("Raze", "Jett", "Gekko")
        rating.assert(stateIs("정렬 안 함"))
        onNodeWithTag(StatsSortIconTag, useUnmergedTree = true).assertDoesNotExist()

        rating.performClick()
        rating.assert(stateIs("내림차순"))
        assertAgentOrder("Raze", "Jett", "Gekko")
        onNodeWithTag(StatsSortIconTag, useUnmergedTree = true).assertExists()

        // `.pen` 51-8: ascending puts the lower rating first and keeps the missing value last.
        rating.performClick()
        rating.assert(stateIs("오름차순"))
        assertAgentOrder("Jett", "Raze", "Gekko")
        onNodeWithTag(StatsSortIconTag, useUnmergedTree = true).assertExists()

        rating.performClick()
        rating.assert(stateIs("정렬 안 함"))
        assertAgentOrder("Raze", "Jett", "Gekko")
        onNodeWithTag(StatsSortIconTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun longNamesDatesAndTitlesWrapWithoutClippingOnNarrowScreensAndDoubleFonts() {
        val texts = listOf(
            LONG_HANDLE,
            "Goo Sang-min · Korea · KR",
            "현재 팀",
            "Nongshim RedForce Official Esports Organization",
            "요원별 기록",
            "전체 기간",
            "최근 경기",
            "최대 5경기",
            "Nongshim RedForce vs BBL Esports",
            "Esports World Cup 2026 · Playoffs",
            "승리",
            "결과 미정",
            "2 : 0",
            "7월 12일",
            "${YEAR - 1}년 7월 10일",
        )
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 6000f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            Fixture(player.copy(profile = player.profile.copy(handle = LONG_HANDLE)))
                        }
                    }
                    texts.forEach { text -> assertWhole(text, "width=$width fontScale=$scale", width) }
                }
            }
        }
    }

    @Test
    fun otherYearDateWrapsBesideTheMatchInsteadOfSqueezingIt() {
        val dateText = "${YEAR - 1}년 12월 24일"
        val match = player.recentMatches.first().copy(playedDate = LocalDate(YEAR - 1, 12, 24))
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 1.5f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 4000f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            Fixture(player.copy(recentMatches = listOf(match)))
                        }
                    }
                    val label = "width=$width fontScale=$scale"
                    val row = onNodeWithTag(playerMatchCardTag(WIN_ID)).fetchSemanticsNode().boundsInRoot
                    listOf("Nongshim RedForce vs BBL Esports", "Esports World Cup 2026 · Playoffs").forEach { text ->
                        val node = rowNode(text, WIN_ID)
                        assertWholeNode(node, text, label, width)
                        // The match identity keeps a readable share of the row next to the result and date.
                        val oneLine = node.textLayout().multiParagraph.intrinsics.maxIntrinsicWidth
                        val wanted = minOf(oneLine, row.width * 0.3f)
                        assertTrue(node.boundsInRoot.width >= wanted - 0.5f, "$label '$text' is squeezed: ${node.boundsInRoot}")
                    }
                    listOf("승리", "2 : 0", dateText).forEach { text ->
                        assertWholeNode(rowNode(text, WIN_ID), text, label, width)
                    }
                }
            }
        }
    }

    @Test
    fun rtlMirrorsTheHeroSectionTitlesAndResultColumn() =
        runSkikoComposeUiTest(size = Size(360f, 2400f)) {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Fixture(player)
                }
            }

            val name = onNode(hasText("Rb") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(360f - 16f - 72f - 16f, name.right, 1f)
            val section = onNode(hasText("최근 경기") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(360f - 16f, section.right, 1f)
            val outcome = textBounds("승리", WIN_ID)
            val title = textBounds("Nongshim RedForce vs BBL Esports", WIN_ID)
            val score = textBounds("2 : 0", WIN_ID)
            assertTrue(outcome.left > title.right, "result column is not on the right in RTL")
            assertTrue(score.right < title.left, "score is not on the left in RTL")
        }

    @Test
    fun favoriteFailuresRollTheStarBackAndOfferRetry() {
        initializeVlrMaterial3()
        runComposeUiTest {
            var favorite by mutableStateOf(
                PlayerFavoriteUiState(isRestored = true, isFavorite = false, failedIntent = PlayerFavoriteMutationIntent.Add),
            )
            var retries = 0
            setContent { Fixture(player, favorite = favorite, onFavoriteRetry = { retries += 1 }) }

            // `.pen` 51-6: an add failure leaves the outline star and a retry Snackbar.
            onNodeWithTag(PLAYER_DETAIL_FAVORITE_OUTLINE_TAG).assertIsDisplayed()
            onNodeWithText("즐겨찾기 추가에 실패했습니다.").assertIsDisplayed()
            onNodeWithText("재시도").performClick()
            assertEquals(1, retries)

            // `.pen` 51-7: a remove failure restores the filled star.
            favorite = PlayerFavoriteUiState(isRestored = true, isFavorite = true, failedIntent = PlayerFavoriteMutationIntent.Remove)
            waitForIdle()
            onNodeWithTag(PLAYER_DETAIL_FAVORITE_FILLED_TAG).assertIsDisplayed()
            onNodeWithText("즐겨찾기 해제에 실패했습니다.").assertIsDisplayed()
            onNodeWithText("재시도").assertIsDisplayed()
        }
    }

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun ComposeUiTest.assertAgentOrder(vararg agents: String) {
        val tops = agents.map { onNodeWithTag(playerAgentIdentityTag(it.lowercase())).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "agent order is not ${agents.toList()}")
    }

    private fun ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.inSection(text: String, tag: String) {
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .assertExists("'$text' is not in $tag")
    }

    private fun ComposeUiTest.inRow(text: String, matchId: String) = inSection(text, playerMatchCardTag(matchId))

    private fun ComposeUiTest.textBounds(text: String, matchId: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(playerMatchCardTag(matchId))), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

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

    private fun ComposeUiTest.rowNode(text: String, matchId: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(playerMatchCardTag(matchId))), useUnmergedTree = true)
            .fetchSemanticsNode()

    private fun assertWholeNode(node: SemanticsNode, text: String, label: String, width: Float) {
        val layout = node.textLayout()
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
        assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
        assertTrue(node.boundsInRoot.right <= width + 0.5f, "$label '$text' leaves the screen")
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    @Composable
    private fun Fixture(
        player: PlayerDetail,
        favorite: PlayerFavoriteUiState = PlayerFavoriteUiState(),
        sort: StatsSort<PlayerAgentStatsSortColumn>? = null,
        onSortColumn: (PlayerAgentStatsSortColumn) -> Unit = {},
        onFavoriteRetry: () -> Unit = {},
    ) {
        VlrTheme {
            PlayerDetailContent(
                uiState = PlayerDetailUiState(PlayerDetailContentState.Content(player), favorite, agentStatsSort = sort),
                listState = rememberLazyListState(),
                agentStatsHorizontalScrollState = rememberScrollState(),
                onBack = {},
                onTeamClick = {},
                onMatchClick = {},
                onRetry = {},
                onFavoriteClick = {},
                onFavoriteRetry = onFavoriteRetry,
                onFavoriteRestoreRetry = {},
                onFavoriteErrorDismiss = {},
                onAgentStatsSortColumn = onSortColumn,
            )
        }
    }

    private companion object {
        val YEAR = KoreanDateTimeFormat.currentYear()
        const val WIN_ID = "1"
        const val LOSS_ID = "2"
        const val UNKNOWN_ID = "3"
        const val LONG_HANDLE = "매우긴한국어선수이름과공식표기"

        fun agent(name: String, maps: Int, rating: Double?) = PlayerAgentStat(
            agentName = name,
            mapsPlayed = maps,
            pickRatePercent = 11,
            roundsPlayed = null,
            rating = rating,
            averageCombatScore = rating?.let { 245.0 },
            killDeathRatio = null,
            kastPercent = null,
            averageDamagePerRound = null,
            killsPerRound = null,
            assistsPerRound = null,
            firstKillDeathRatio = null,
            kills = null,
            deaths = null,
            assists = null,
            firstKills = null,
            firstDeaths = null,
        )

        fun match(
            id: String,
            teamB: String,
            scores: Pair<Int?, Int?>,
            outcome: PlayerRecentMatchOutcome,
            playedOn: String?,
            playedDate: LocalDate?,
        ) = PlayerRecentMatch(
            id = id,
            eventName = "Esports World Cup 2026",
            eventStage = "Playoffs",
            teamA = PlayerRecentMatchTeam("Nongshim RedForce", "NS"),
            teamB = PlayerRecentMatchTeam(teamB, null),
            teamAScore = scores.first,
            teamBScore = scores.second,
            outcome = outcome,
            playedOn = playedOn,
            playedDate = playedDate,
        )

        val player = PlayerDetail(
            id = "488",
            profile = PlayerProfile("Rb", "Goo Sang-min", emptyList(), "kr", "Korea"),
            currentTeam = PlayerCurrentTeam("11060", "Nongshim RedForce Official Esports Organization"),
            agentStats = listOf(agent("raze", 61, 1.12), agent("jett", 134, 1.07), agent("gekko", 1, null)),
            recentMatches = listOf(
                match(WIN_ID, "BBL Esports", 2 to 0, PlayerRecentMatchOutcome.WIN, "2026/07/12", LocalDate(YEAR, 7, 12)),
                match(LOSS_ID, "Paper Rex", 0 to 2, PlayerRecentMatchOutcome.LOSS, "2025/07/10", LocalDate(YEAR - 1, 7, 10)),
                match(UNKNOWN_ID, "T1", null to null, PlayerRecentMatchOutcome.UNKNOWN, "2026/06/28", null),
            ),
        )
    }
}
