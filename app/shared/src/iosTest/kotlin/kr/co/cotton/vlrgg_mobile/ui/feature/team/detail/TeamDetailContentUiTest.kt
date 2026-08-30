package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TeamDetailContentUiTest {

    @Test
    fun loadingKeepsTopBarAndShowsGeometrySkeletonWithoutSampleTeamData() = runComposeUiTest {
        setContent { Fixture(uiState = TeamDetailUiState()) }

        onNodeWithText("Team Detail").assertIsDisplayed()
        onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
        onNodeWithTag(TEAM_DETAIL_LOADING_TAG).assertIsDisplayed()
        listOf("예정된 경기", "최근 경기", "로스터", "뉴스").forEach { section ->
            scrollToText(section)
            onNodeWithText(section).assertExists()
        }
        onNodeWithText(TEAM_NAME).assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기 추가").assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기 해제").assertDoesNotExist()
    }

    @Test
    fun favoriteStarIsAbsentUntilLocalRestoreCompletes() = runComposeUiTest {
        setContent {
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(populatedTeam),
                    favorite = TeamFavoriteUiState(),
                ),
            )
        }

        onNodeWithTag(TEAM_DETAIL_FAVORITE_OUTLINE_TAG).assertDoesNotExist()
        onNodeWithTag(TEAM_DETAIL_FAVORITE_FILLED_TAG).assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기 추가").assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기 해제").assertDoesNotExist()
    }

    @Test
    fun populatedRendersOrderedSectionsAndAllProvidedFields() = runComposeUiTest {
        setContent { Fixture(contentState = TeamDetailContentState.Content(populatedTeam)) }

        val headerTop = onNodeWithTag(TEAM_DETAIL_HEADER_TAG).fetchSemanticsNode().boundsInRoot.top
        val upcomingTop = onNodeWithTag(TEAM_DETAIL_UPCOMING_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue(headerTop < upcomingTop)

        assertTextInTaggedSection(TEAM_NAME, TEAM_DETAIL_HEADER_TAG)
        assertTextInTaggedSection("KRX · South Korea", TEAM_DETAIL_HEADER_TAG)

        scrollToTag(teamMatchCardTag(UPCOMING_MATCH_ID))
        assertTextInTaggedSection(TEAM_NAME, teamMatchCardTag(UPCOMING_MATCH_ID))
        assertTextInTaggedSection("Sentinels", teamMatchCardTag(UPCOMING_MATCH_ID))
        assertTextInTaggedSection("VCT Pacific", teamMatchCardTag(UPCOMING_MATCH_ID))
        assertTextInTaggedSection("Stage 2", teamMatchCardTag(UPCOMING_MATCH_ID))
        assertTextInTaggedSection("in 2d", teamMatchCardTag(UPCOMING_MATCH_ID))
        assertTextInTaggedSection("2026-08-28 17:00", teamMatchCardTag(UPCOMING_MATCH_ID))

        scrollToTag(teamMatchCardTag(RECENT_MATCH_ID))
        assertTextInTaggedSection(TEAM_NAME, teamMatchCardTag(RECENT_MATCH_ID))
        assertTextInTaggedSection("Gen.G", teamMatchCardTag(RECENT_MATCH_ID))
        assertTextInTaggedSection("VCT Pacific", teamMatchCardTag(RECENT_MATCH_ID))
        assertTextInTaggedSection("final", teamMatchCardTag(RECENT_MATCH_ID))
        assertTextInTaggedSection("2026-08-25", teamMatchCardTag(RECENT_MATCH_ID))
        scrollToTag(teamPlayerRowTag(PLAYER_ID))
        assertTextNodeCount("MaKo", expectedCount = 1)
        assertTextNodeCount("Kim Myeong-kwan", expectedCount = 1)
        assertTextNodeCount("player", expectedCount = 1)
        scrollToTag(teamStaffRowTag(STAFF_ID))
        assertTextNodeCount("termi", expectedCount = 1)
        assertTextNodeCount("head coach", expectedCount = 1)
        scrollToTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG))
        assertTextNodeCount("KIWOOM DRX releases rookie Hermes", expectedCount = 1)
        onNodeWithTag(
            teamNewsPublishedDateTag(ARTICLE_ID, ARTICLE_SLUG),
            useUnmergedTree = true,
        ).assert(hasText("2026-08-25"))
        listOf(
            TEAM_DETAIL_UPCOMING_SECTION_TAG,
            TEAM_DETAIL_RECENT_SECTION_TAG,
            TEAM_DETAIL_ROSTER_SECTION_TAG,
            TEAM_DETAIL_NEWS_SECTION_TAG,
        ).forEach { tag -> onNodeWithTag(tag).assertExists() }
    }

    @Test
    fun upcomingEmptyDoesNotHideOtherSuccessfulSections() = runComposeUiTest {
        setContent {
            Fixture(
                contentState = TeamDetailContentState.Content(
                    populatedTeam.copy(upcomingMatches = emptyList()),
                ),
            )
        }

        onNodeWithText("예정된 경기가 없습니다").assertExists()
        onNodeWithTag(teamMatchCardTag(RECENT_MATCH_ID)).assertExists()
        scrollToTag(teamPlayerRowTag(PLAYER_ID))
        onNodeWithText("MaKo").assertExists()
        scrollToTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG))
        onNodeWithText("KIWOOM DRX releases rookie Hermes").assertExists()
    }

    @Test
    fun recentEmptyDoesNotHideOtherSuccessfulSections() = runComposeUiTest {
        setContent {
            Fixture(
                contentState = TeamDetailContentState.Content(
                    populatedTeam.copy(recentMatches = emptyList()),
                ),
            )
        }

        onNodeWithText("최근 경기 기록이 없습니다").assertExists()
        onNodeWithTag(teamMatchCardTag(UPCOMING_MATCH_ID)).assertExists()
        scrollToTag(teamPlayerRowTag(PLAYER_ID))
        onNodeWithText("MaKo").assertExists()
        scrollToTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG))
        onNodeWithText("KIWOOM DRX releases rookie Hermes").assertExists()
    }

    @Test
    fun playersAndStaffExposeIndependentEmptyMarkers() = runComposeUiTest {
        setContent {
            Fixture(
                contentState = TeamDetailContentState.Content(
                    populatedTeam.copy(players = emptyList()),
                ),
            )
        }
        scrollToText("선수 정보가 없습니다")
        onNodeWithText("선수 정보가 없습니다").assertExists()
        onNodeWithText("termi").assertExists()
        onNodeWithText("로스터 정보가 없습니다").assertDoesNotExist()

        setContent {
            Fixture(
                contentState = TeamDetailContentState.Content(
                    populatedTeam.copy(staff = emptyList()),
                ),
            )
        }
        scrollToText("스태프 정보가 없습니다")
        onNodeWithText("스태프 정보가 없습니다").assertExists()
        onNodeWithText("MaKo").assertExists()
        onNodeWithText("로스터 정보가 없습니다").assertDoesNotExist()
    }

    @Test
    fun whollyEmptyRosterUsesStitchMarkerAndKeepsNews() = runComposeUiTest {
        setContent {
            Fixture(
                contentState = TeamDetailContentState.Content(
                    populatedTeam.copy(players = emptyList(), staff = emptyList()),
                ),
            )
        }

        scrollToText("로스터 정보가 없습니다")
        onNodeWithText("로스터 정보가 없습니다").assertExists()
        scrollToTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG))
        onNodeWithText("KIWOOM DRX releases rookie Hermes").assertExists()
    }

    @Test
    fun newsEmptyUsesFeatureMarkerAndKeepsTeamHeader() = runComposeUiTest {
        setContent {
            Fixture(
                contentState = TeamDetailContentState.Content(
                    populatedTeam.copy(news = emptyList()),
                ),
            )
        }

        scrollToText("관련 뉴스가 없습니다")
        onNodeWithText("관련 뉴스가 없습니다").assertExists()
        scrollToTag(TEAM_DETAIL_HEADER_TAG)
        onNodeWithTag(TEAM_DETAIL_HEADER_TAG).assertExists()
        onNodeWithTag(teamMatchCardTag(UPCOMING_MATCH_ID)).assertExists()
    }

    @Test
    fun everyOptionalSectionCanBeEmptyWithoutBecomingOverallError() = runComposeUiTest {
        setContent {
            Fixture(
                contentState = TeamDetailContentState.Content(
                    populatedTeam.copy(
                        upcomingMatches = emptyList(),
                        recentMatches = emptyList(),
                        players = emptyList(),
                        staff = emptyList(),
                        news = emptyList(),
                    ),
                ),
            )
        }

        onNodeWithTag(TEAM_DETAIL_HEADER_TAG).assertExists()
        onNodeWithText("예정된 경기가 없습니다").assertExists()
        onNodeWithText("최근 경기 기록이 없습니다").assertExists()
        onNodeWithText("로스터 정보가 없습니다").assertExists()
        scrollToText("관련 뉴스가 없습니다")
        onNodeWithText("관련 뉴스가 없습니다").assertExists()
        onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun overallErrorIsModalAndOnlyRetryAndBackInvokeRecoveryCallbacks() = runComposeUiTest {
        var retries = 0
        var backs = 0
        var dismisses = 0
        setContent {
            Fixture(
                contentState = TeamDetailContentState.Error,
                onRetry = { retries += 1 },
                onBack = { backs += 1 },
            )
        }

        onNode(isDialog()).assertExists()
        onNodeWithText("정보를 불러오지 못했습니다").assertIsDisplayed()
        onNodeWithText("네트워크 상태를 확인하고 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithText(TEAM_NAME).assertDoesNotExist()
        onNodeWithContentDescription("재시도").performClick()
        onNodeWithContentDescription("뒤로가기").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun interactiveRowsEmitExactStableIdentities() = runComposeUiTest {
        var matchId: String? = null
        var playerId: String? = null
        var article: Pair<String, String>? = null
        setContent {
            Fixture(
                contentState = TeamDetailContentState.Content(populatedTeam),
                onMatchClick = { matchId = it },
                onPlayerClick = { playerId = it },
                onNewsClick = { articleId, slug -> article = articleId to slug },
            )
        }

        onNodeWithTag(teamMatchCardTag(UPCOMING_MATCH_ID)).performClick()
        scrollToTag(teamPlayerRowTag(PLAYER_ID))
        onNodeWithTag(teamPlayerRowTag(PLAYER_ID)).performClick()
        scrollToTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG))
        onNodeWithTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG)).performClick()

        assertEquals(UPCOMING_MATCH_ID, matchId)
        assertEquals(PLAYER_ID, playerId)
        assertEquals(ARTICLE_ID to ARTICLE_SLUG, article)
    }

    @Test
    fun staffAndEventMetadataAreDisplayOnlyAndFavoriteUiIsLocalOnly() = runComposeUiTest {
        setContent {
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(populatedTeam),
                    favorite = TeamFavoriteUiState(isRestored = true),
                ),
            )
        }

        scrollToTag(teamMatchCardTag(UPCOMING_MATCH_ID))
        val eventNodes = onAllNodesWithText("VCT Pacific", useUnmergedTree = true)
            .fetchSemanticsNodes()
        val stageNodes = onAllNodesWithText("Stage 2", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(eventNodes.isNotEmpty(), "Upcoming match event name was not composed")
        assertTrue(stageNodes.isNotEmpty(), "Upcoming match stage was not composed")
        eventNodes.forEach { eventNode ->
            assertFalse(eventNode.config.contains(SemanticsActions.OnClick))
        }
        stageNodes.forEach { stageNode ->
            assertFalse(stageNode.config.contains(SemanticsActions.OnClick))
        }

        scrollToTag(teamStaffRowTag(STAFF_ID))
        val staffNode = onNodeWithTag(teamStaffRowTag(STAFF_ID)).fetchSemanticsNode()
        assertFalse(staffNode.config.contains(SemanticsActions.OnClick))
        onNodeWithContentDescription("즐겨찾기 추가").assertExists()
        onNodeWithText("알림", substring = true).assertDoesNotExist()
        onNodeWithText("MyPage").assertDoesNotExist()
    }

    @Test
    fun favoriteTopBarUsesOutlineOffAndFilledOnWithKoreanLabelsAnd48DpTarget() = runComposeUiTest {
        var density = 1f
        var toggles = 0
        setContent {
            density = LocalDensity.current.density
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(populatedTeam),
                    favorite = TeamFavoriteUiState(isFavorite = false, isRestored = true),
                ),
                onFavoriteToggle = { toggles += 1 },
            )
        }

        assertEquals(
            1,
            onAllNodesWithContentDescription("즐겨찾기 추가", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            onAllNodesWithContentDescription("즐겨찾기 해제", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
        val offStar = onNodeWithTag(TEAM_DETAIL_FAVORITE_OUTLINE_TAG)
        offStar.assertIsDisplayed()
        val offBounds = offStar.fetchSemanticsNode().boundsInRoot
        assertTrue(offBounds.width >= 48f * density)
        assertTrue(offBounds.height >= 48f * density)
        onNodeWithContentDescription("즐겨찾기 추가").performClick()
        assertEquals(1, toggles)

        setContent {
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(populatedTeam),
                    favorite = TeamFavoriteUiState(isFavorite = true, isRestored = true),
                ),
            )
        }
        assertEquals(
            0,
            onAllNodesWithContentDescription("즐겨찾기 추가", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
        val onStar = onNodeWithTag(TEAM_DETAIL_FAVORITE_FILLED_TAG)
        onStar.assertIsDisplayed()
        onNodeWithContentDescription("즐겨찾기 해제").assertIsDisplayed()
        val onBounds = onStar.fetchSemanticsNode().boundsInRoot
        assertTrue(onBounds.width >= 48f * density)
        assertTrue(onBounds.height >= 48f * density)

        setContent {
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(populatedTeam),
                    favorite = TeamFavoriteUiState(
                        isFavorite = true,
                        isRestored = true,
                        isMutationInProgress = true,
                    ),
                ),
            )
        }
        onNodeWithTag(TEAM_DETAIL_FAVORITE_FILLED_TAG).assertIsNotEnabled()
    }

    @Test
    fun longKoreanTeamNameAndFavoriteControlRemainInTheDetailShellWithoutBottomNavigation() = runComposeUiTest {
        val longKoreanName = "매우 긴 한국어 팀 이름도 상세 화면의 즐겨찾기와 함께 표시됩니다"
        setContent {
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(
                        populatedTeam.copy(name = longKoreanName),
                    ),
                    favorite = TeamFavoriteUiState(isRestored = true),
                ),
            )
        }

        onNodeWithText(longKoreanName).assertExists()
        onNodeWithContentDescription("즐겨찾기 추가").assertExists()
        onNodeWithText("News").assertDoesNotExist()
        onNodeWithText("Matches").assertDoesNotExist()
        onNodeWithText("MyPage").assertDoesNotExist()
    }

    @Test
    fun favoriteFailureSnackbarUsesCanonicalGeometryMessageRetryAndNoCloseAction() = runComposeUiTest {
        var retries = 0
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(populatedTeam),
                    favorite = TeamFavoriteUiState(
                        failedIntent = TeamFavoriteMutationIntent.Add,
                    ),
                ),
                onFavoriteRetry = { retries += 1 },
            )
        }

        val rootBounds = onNodeWithTag(TEST_ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val snackbarBounds = onNodeWithTag(TEAM_DETAIL_FAVORITE_SNACKBAR_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val inset = 16f * density
        assertTrue(snackbarBounds.width <= 328f * density + 1f)
        assertTrue(snackbarBounds.left >= rootBounds.left + inset - 1f)
        assertTrue(snackbarBounds.right <= rootBounds.right - inset + 1f)
        assertTrue(snackbarBounds.bottom <= rootBounds.bottom - inset + 1f)
        assertTrue(snackbarBounds.height < rootBounds.height)
        onNodeWithText("즐겨찾기 추가에 실패했습니다.").assertIsDisplayed()
        val retry = onNodeWithContentDescription("재시도").assertIsDisplayed()
        val retryBounds = retry.fetchSemanticsNode().boundsInRoot
        assertTrue(retryBounds.width >= 48f * density)
        assertTrue(
            retryBounds.height >= 48f * density,
            "Retry target height=${retryBounds.height}, bounds=$retryBounds, density=$density, expected=${48f * density}",
        )
        onNodeWithContentDescription("즐겨찾기 오류 닫기").assertDoesNotExist()
        onNodeWithTag(TEAM_DETAIL_HEADER_TAG).assertExists()
        retry.performClick()
        assertEquals(1, retries)

        setContent {
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(populatedTeam),
                    favorite = TeamFavoriteUiState(
                        isFavorite = true,
                        failedIntent = TeamFavoriteMutationIntent.Remove,
                    ),
                ),
            )
        }
        onNodeWithText("즐겨찾기 해제에 실패했습니다.").assertIsDisplayed()
    }

    @Test
    fun favoriteFailureIsClearedThroughLifecycleCallbackWithoutVisibleCloseAction() = runComposeUiTest {
        var dismisses = 0
        setContent {
            Fixture(
                uiState = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(populatedTeam),
                    favorite = TeamFavoriteUiState(
                        isRestored = true,
                        failedIntent = TeamFavoriteMutationIntent.Add,
                    ),
                ),
                onFavoriteErrorDismiss = { dismisses += 1 },
            )
        }

        onNodeWithTag(TEAM_DETAIL_FAVORITE_SNACKBAR_TAG).assertIsDisplayed()
        onNodeWithContentDescription("즐겨찾기 오류 닫기").assertDoesNotExist()
        assertEquals(0, dismisses)

        setContent { }
        assertEquals(1, dismisses)
    }

    @Test
    fun primaryInteractiveTargetsAreAtLeast48DpAndHaveAccessibleLabels() = runComposeUiTest {
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Fixture(contentState = TeamDetailContentState.Content(populatedTeam))
        }
        val minimumTargetPx = 48f * density

        fun assertMinimumTarget(contentDescription: String) {
            val interaction = onNodeWithContentDescription(contentDescription)
            val bounds = interaction.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= minimumTargetPx, "Target width was ${bounds.width}px")
            assertTrue(bounds.height >= minimumTargetPx, "Target height was ${bounds.height}px")
        }

        assertMinimumTarget("뒤로 가기")
        assertMinimumTarget("경기 상세: KIWOOM DRX 대 Sentinels")
        scrollToTag(teamPlayerRowTag(PLAYER_ID))
        assertMinimumTarget("선수 상세: MaKo")
        scrollToTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG))
        assertMinimumTarget("뉴스 상세: KIWOOM DRX releases rookie Hermes")
    }

    private fun ComposeUiTest.scrollToText(text: String) = scrollTo(hasText(text))

    private fun ComposeUiTest.scrollToTag(tag: String) = scrollTo(hasTestTag(tag))

    private fun ComposeUiTest.assertTextNodeCount(text: String, expectedCount: Int) {
        assertEquals(
            expectedCount,
            onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size,
            "Expected $expectedCount rendered text node(s) for '$text'",
        )
    }

    private fun ComposeUiTest.assertTextInTaggedSection(text: String, tag: String) {
        onNode(
            hasText(text) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun ComposeUiTest.scrollTo(matcher: SemanticsMatcher) {
        onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
    }

    @androidx.compose.runtime.Composable
    private fun Fixture(
        uiState: TeamDetailUiState = TeamDetailUiState(),
        contentState: TeamDetailContentState = uiState.contentState,
        onBack: () -> Unit = {},
        onMatchClick: (String) -> Unit = {},
        onPlayerClick: (String) -> Unit = {},
        onNewsClick: (String, String) -> Unit = { _, _ -> },
        onRetry: () -> Unit = {},
        onFavoriteToggle: () -> Unit = {},
        onFavoriteRetry: () -> Unit = {},
        onFavoriteErrorDismiss: () -> Unit = {},
    ) {
        VlrTheme {
            Box(Modifier.fillMaxSize().testTag(TEST_ROOT_TAG)) {
                TeamDetailContent(
                    uiState = TeamDetailUiState(contentState, uiState.favorite),
                    listState = rememberLazyListState(),
                    onBack = onBack,
                    onMatchClick = onMatchClick,
                    onPlayerClick = onPlayerClick,
                    onNewsClick = onNewsClick,
                    onRetry = onRetry,
                    onFavoriteToggle = onFavoriteToggle,
                    onFavoriteRetry = onFavoriteRetry,
                    onFavoriteErrorDismiss = onFavoriteErrorDismiss,
                )
            }
        }
    }

    private companion object {
        const val TEAM_ID = "8185"
        const val TEAM_NAME = "KIWOOM DRX"
        const val UPCOMING_MATCH_ID = "698887"
        const val RECENT_MATCH_ID = "698100"
        const val PLAYER_ID = "4462"
        const val STAFF_ID = "775"
        const val ARTICLE_ID = "700755"
        const val ARTICLE_SLUG = "kiwoom-drx-releases-rookie-hermes"
        const val TEST_ROOT_TAG = "team-detail-test-root"

        val populatedTeam = TeamDetail(
            id = TEAM_ID,
            name = TEAM_NAME,
            tag = "KRX",
            country = "South Korea",
            upcomingMatches = listOf(
                TeamMatch(
                    id = UPCOMING_MATCH_ID,
                    eventName = "VCT Pacific",
                    eventStage = "Stage 2",
                    teamName = TEAM_NAME,
                    opponentName = "Sentinels",
                    statusText = "in 2d",
                    scheduledAtText = "2026-08-28 17:00",
                ),
            ),
            recentMatches = listOf(
                TeamMatch(
                    id = RECENT_MATCH_ID,
                    eventName = "VCT Pacific",
                    eventStage = null,
                    teamName = TEAM_NAME,
                    opponentName = "Gen.G",
                    statusText = "final",
                    scheduledAtText = "2026-08-25",
                ),
            ),
            players = listOf(
                TeamRosterMember(PLAYER_ID, "MaKo", "Kim Myeong-kwan", listOf("player")),
            ),
            staff = listOf(
                TeamRosterMember(STAFF_ID, "termi", null, listOf("head coach")),
            ),
            news = listOf(
                TeamNews(
                    articleId = ARTICLE_ID,
                    slug = ARTICLE_SLUG,
                    title = "KIWOOM DRX releases rookie Hermes",
                    publishedDateText = "2026-08-25",
                ),
            ),
        )
    }
}
