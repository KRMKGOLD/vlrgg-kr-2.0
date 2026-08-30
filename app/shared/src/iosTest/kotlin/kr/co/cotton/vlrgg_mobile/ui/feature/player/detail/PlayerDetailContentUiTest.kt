package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.LocalDensity
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerCurrentTeam
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerProfile
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchTeam
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(DelicateCoilApi::class, ExperimentalTestApi::class)
class PlayerDetailContentUiTest {
    @Test
    fun loadingHasCenteredCircularHeaderAndSectionSkeletonsWithoutSampleContent() = runComposeUiTest {
        setContent { Fixture() }

        onNodeWithText("Player Profile").assertIsDisplayed()
        onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
        onNodeWithTag(PLAYER_DETAIL_LOADING_TAG).assertExists()
        onNodeWithTag(PLAYER_DETAIL_LOADING_HEADER_AVATAR_TAG).assertExists()
        onNodeWithText("stax").assertDoesNotExist()
        onNodeWithText("T1").assertDoesNotExist()
        onNodeWithText("Jett").assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기 추가").assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기 해제").assertDoesNotExist()
    }

    @Test
    fun favoriteStarIsAbsentUntilLocalRestoreCompletes() = runComposeUiTest {
        setContent {
            Fixture(
                PlayerDetailContentState.Content(player),
                favorite = PlayerFavoriteUiState(),
            )
        }

        onNodeWithTag(PLAYER_DETAIL_FAVORITE_OUTLINE_TAG).assertDoesNotExist()
        onNodeWithTag(PLAYER_DETAIL_FAVORITE_FILLED_TAG).assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기 추가").assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기 해제").assertDoesNotExist()
    }

    @Test
    fun populatedCardsPreserveProvidedDataAndEmitExactTeamAndMatchIds() = runComposeUiTest {
        var teamId: String? = null
        var matchId: String? = null
        setContent { Fixture(PlayerDetailContentState.Content(player), { teamId = it }, { matchId = it }) }

        onNodeWithTag(PLAYER_DETAIL_HEADER_TAG).assertExists()
        onNodeWithText("현재 소속 팀").assertExists()
        onNodeWithText("에이전트 통계").assertExists()
        onNodeWithText("최근 경기").assertExists()
        onNodeWithTag(playerCurrentTeamCardTag(TEAM_ID)).assertExists()
        onNodeWithTag(playerRecentMatchCardTag(MATCH_ID)).assertExists()
        onNodeWithTag(playerMatchScoreTag(MATCH_ID), useUnmergedTree = true).assertExists()
        onNodeWithText("VCT Pacific").assertExists()
        onNodeWithText("0%").assertExists()
        onNodeWithTag(playerTeamRowTag(TEAM_ID)).performClick()
        onNodeWithTag(playerMatchCardTag(MATCH_ID)).performClick()

        assertEquals(TEAM_ID, teamId)
        assertEquals(MATCH_ID, matchId)
    }

    @Test
    fun currentTeamUsesProvidedImageUrlAndStablePlaceholderForNullableUrl() {
        var imageRequestCount = 0
        var requestedImageData: Any? = null
        var imageLoader: ImageLoader? = null
        val fakeEngine = FakeImageLoaderEngine.Builder()
            .default(
                Interceptor { chain ->
                    imageRequestCount += 1
                    requestedImageData = chain.request.data
                    ErrorResult(null, chain.request, IllegalStateException("Team logo fixture failure"))
                },
            )
            .build()

        SingletonImageLoader.setUnsafe(
            SingletonImageLoader.Factory { context ->
                ImageLoader.Builder(context)
                    .components { add(fakeEngine) }
                    .build()
                    .also { imageLoader = it }
            },
        )
        try {
            runComposeUiTest {
                setContent {
                    Fixture(
                        PlayerDetailContentState.Content(
                            player.copy(currentTeam = PlayerCurrentTeam(TEAM_ID, "T1", TEAM_IMAGE_URL)),
                        ),
                    )
                }
                onNodeWithTag(playerTeamLogoTag(TEAM_ID), useUnmergedTree = true).assertExists()
                assertTrue(imageRequestCount > 0, "AsyncImage did not request the provided team image URL")
                assertEquals(TEAM_IMAGE_URL, requestedImageData)

                setContent {
                    Fixture(
                        PlayerDetailContentState.Content(
                            player.copy(currentTeam = PlayerCurrentTeam(TEAM_ID, "T1", null)),
                        ),
                    )
                }
                onNodeWithTag(playerTeamLogoPlaceholderTag(TEAM_ID), useUnmergedTree = true).assertExists()
            }
        } finally {
            try {
                SingletonImageLoader.reset()
            } finally {
                imageLoader?.shutdown()
            }
        }
    }

    @Test
    fun agentTableCapitalizesOnlyTheUiLabelKeepsNullableMarkersAndRightAlignsMetrics() = runComposeUiTest {
        setContent {
            Fixture(
                PlayerDetailContentState.Content(
                    player.copy(
                        agentStats = listOf(
                            player.agentStats.single().copy(agentName = "jett", rating = null, averageDamagePerRound = null),
                        ),
                    ),
                ),
            )
        }

        onNodeWithTag(PLAYER_AGENT_STATS_TABLE_TAG).assertExists()
        onNodeWithText("Jett").assertExists()
        onNodeWithText("jett").assertDoesNotExist()
        assertEquals(3, onAllNodesWithText("—").fetchSemanticsNodes().size)
        onNodeWithContentDescription("에이전트 통계: Jett, Maps: 0, Pick Rate: 0%, Rating: —, ACS: 0.0, K/D: 0.0, KAST: —, ADR: —").assertExists()
        onNodeWithContentDescription("에이전트 이미지").assertDoesNotExist()

        val mapsHeader = onNodeWithTag(playerAgentMetricHeaderTag("Maps")).fetchSemanticsNode().boundsInRoot
        val mapsValue = onNodeWithTag(playerAgentMetricValueTag("jett", "Maps")).fetchSemanticsNode().boundsInRoot
        assertEquals(mapsHeader.right, mapsValue.right)
    }

    @Test
    fun eachOptionalSectionCanBeEmptyWithoutChangingTheOtherContent() = runComposeUiTest {
        setContent { Fixture(PlayerDetailContentState.Content(player.copy(currentTeam = null))) }
        onNodeWithText("소속 팀 정보가 없습니다").assertExists()
        onNodeWithText("Jett").assertExists()
        onNodeWithText("VCT Pacific").assertExists()

        setContent { Fixture(PlayerDetailContentState.Content(player.copy(agentStats = emptyList()))) }
        onNodeWithText("에이전트 통계 정보가 없습니다").assertExists()
        onNodeWithText("T1").assertExists()
        onNodeWithText("VCT Pacific").assertExists()

        setContent { Fixture(PlayerDetailContentState.Content(player.copy(recentMatches = emptyList()))) }
        onNodeWithText("최근 경기 기록이 없습니다").assertExists()
        onNodeWithText("T1").assertExists()
        onNodeWithText("Jett").assertExists()

        setContent {
            Fixture(
                PlayerDetailContentState.Content(player.copy(currentTeam = null, agentStats = emptyList(), recentMatches = emptyList())),
            )
        }
        onNodeWithText("소속 팀 정보가 없습니다").assertExists()
        onNodeWithText("에이전트 통계 정보가 없습니다").assertExists()
        onNodeWithText("최근 경기 기록이 없습니다").assertExists()
        onNodeWithTag(PLAYER_DETAIL_HEADER_TAG).assertExists()
        onNode(isDialog()).assertDoesNotExist()
        onNodeWithText("Agent", substring = true).assertDoesNotExist()
    }

    @Test
    fun failureDialogHasExactCopyAndOnlyRetryAndBackCallbacks() = runComposeUiTest {
        var retries = 0
        var backs = 0
        setContent { Fixture(PlayerDetailContentState.Error, onRetry = { retries++ }, onBack = { backs++ }) }

        onNode(isDialog()).assertExists()
        onNodeWithText("정보를 불러오지 못했습니다").assertIsDisplayed()
        onNodeWithText("네트워크 상태를 확인하고 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithContentDescription("재시도").performClick()
        onNodeWithContentDescription("뒤로가기").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun favoriteStarUsesKoreanStateLabelsFilledAndOutlineVariantsWith48DpTargets() = runComposeUiTest {
        var clicks = 0
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Fixture(
                PlayerDetailContentState.Content(player),
                favorite = PlayerFavoriteUiState(isRestored = true),
                onFavoriteClick = { clicks++ },
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
        val offStar = onNodeWithTag(PLAYER_DETAIL_FAVORITE_OUTLINE_TAG)
        offStar.assertIsDisplayed()
        val offBounds = offStar.fetchSemanticsNode().boundsInRoot
        assertTrue(offBounds.width >= 48f * density)
        assertTrue(offBounds.height >= 48f * density)
        onNodeWithContentDescription("즐겨찾기 추가").performClick()
        assertEquals(1, clicks)

        setContent {
            Fixture(
                PlayerDetailContentState.Content(player),
                favorite = PlayerFavoriteUiState(isFavorite = true, isRestored = true),
            )
        }
        assertEquals(
            0,
            onAllNodesWithContentDescription("즐겨찾기 추가", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
        val onStar = onNodeWithTag(PLAYER_DETAIL_FAVORITE_FILLED_TAG)
        onStar.assertIsDisplayed()
        onNodeWithContentDescription("즐겨찾기 해제").assertIsDisplayed()
        val onBounds = onStar.fetchSemanticsNode().boundsInRoot
        assertTrue(onBounds.width >= 48f * density)
        assertTrue(onBounds.height >= 48f * density)

        setContent {
            Fixture(
                PlayerDetailContentState.Content(player),
                favorite = PlayerFavoriteUiState(
                    isFavorite = true,
                    isRestored = true,
                    isMutationInProgress = true,
                ),
            )
        }
        onNodeWithTag(PLAYER_DETAIL_FAVORITE_FILLED_TAG).assertIsNotEnabled()
    }

    @Test
    fun favoriteFailureSnackbarUsesCanonicalGeometryMessageRetryAndNoCloseAction() = runComposeUiTest {
        var retries = 0
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Fixture(
                PlayerDetailContentState.Content(player),
                favorite = PlayerFavoriteUiState(
                    isRestored = true,
                    failedIntent = PlayerFavoriteMutationIntent.Add,
                ),
                onFavoriteRetry = { retries++ },
            )
        }

        val rootBounds = onNodeWithTag(TEST_ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val snackbarBounds = onNodeWithTag(PLAYER_DETAIL_FAVORITE_SNACKBAR_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val inset = 16f * density
        assertTrue(snackbarBounds.width <= 328f * density + 1f)
        assertTrue(snackbarBounds.left >= rootBounds.left + inset - 1f)
        assertTrue(snackbarBounds.right <= rootBounds.right - inset + 1f)
        assertTrue(snackbarBounds.bottom <= rootBounds.bottom - inset + 1f)
        assertTrue(snackbarBounds.height < rootBounds.height)
        onNodeWithText("즐겨찾기 추가에 실패했습니다.").assertExists()
        val retry = onNodeWithContentDescription("재시도").assertIsDisplayed()
        val retryBounds = retry.fetchSemanticsNode().boundsInRoot
        assertTrue(retryBounds.width >= 48f * density)
        assertTrue(
            retryBounds.height >= 48f * density,
            "Retry target height=${retryBounds.height}, bounds=$retryBounds, density=$density, expected=${48f * density}",
        )
        onNodeWithContentDescription("즐겨찾기 오류 닫기").assertDoesNotExist()
        onNodeWithTag(PLAYER_DETAIL_HEADER_TAG).assertExists()
        retry.performClick()
        assertEquals(1, retries)

        setContent {
            Fixture(
                PlayerDetailContentState.Content(player),
                favorite = PlayerFavoriteUiState(
                    isFavorite = true,
                    isRestored = true,
                    failedIntent = PlayerFavoriteMutationIntent.Remove,
                ),
            )
        }
        onNodeWithText("즐겨찾기 해제에 실패했습니다.").assertExists()
    }

    @Test
    fun favoriteFailureIsClearedThroughLifecycleCallbackWithoutVisibleCloseAction() = runComposeUiTest {
        var dismisses = 0
        setContent {
            Fixture(
                PlayerDetailContentState.Content(player),
                favorite = PlayerFavoriteUiState(
                    isRestored = true,
                    failedIntent = PlayerFavoriteMutationIntent.Add,
                ),
                onFavoriteErrorDismiss = { dismisses++ },
            )
        }

        onNodeWithTag(PLAYER_DETAIL_FAVORITE_SNACKBAR_TAG).assertIsDisplayed()
        onNodeWithContentDescription("즐겨찾기 오류 닫기").assertDoesNotExist()
        assertEquals(0, dismisses)

        setContent { }
        assertEquals(1, dismisses)
    }

    @Test
    fun populatedContentUsesKoreanOutcomeLongNamesAndAccessibleTargetsWithoutForbiddenUi() = runComposeUiTest {
        var density = 1f
        val longPlayer = player.copy(
            profile = player.profile.copy(
                handle = "매우긴한국어선수이름과공식표기",
                realName = "Kim Gu-taek Official International Championship Name",
            ),
            currentTeam = PlayerCurrentTeam(TEAM_ID, "Nongshim RedForce Official Esports Organization"),
        )
        setContent {
            density = LocalDensity.current.density
            Fixture(PlayerDetailContentState.Content(longPlayer))
        }

        onNodeWithText("매우긴한국어선수이름과공식표기").assertExists()
        onNodeWithText("Nongshim RedForce Official Esports Organization").assertExists()
        onNodeWithText("Kim Gu-taek Official International Championship Name · Korea · KR").assertExists()
        onNodeWithText("승리").assertExists()
        onNodeWithText("WIN").assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기").assertDoesNotExist()
        onNodeWithContentDescription("선수 이미지").assertDoesNotExist()
        onNodeWithContentDescription("팀 로고").assertDoesNotExist()
        onNodeWithContentDescription("에이전트 이미지").assertDoesNotExist()
        onNodeWithContentDescription("이벤트 상세").assertDoesNotExist()
        onNodeWithText("스낵바", substring = true).assertDoesNotExist()
        onNodeWithText("player_detail").assertDoesNotExist()
        onNodeWithText("event_detail").assertDoesNotExist()
        onNodeWithText("agent icon", substring = true, ignoreCase = true).assertDoesNotExist()

        fun assertMinimumTarget(description: String) {
            val bounds = onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
            val minimumTargetPx = 48f * density
            assertTrue(bounds.width >= minimumTargetPx, "Target width was ${bounds.width}px")
            assertTrue(bounds.height >= minimumTargetPx, "Target height was ${bounds.height}px")
        }

        assertMinimumTarget("뒤로 가기")
        assertMinimumTarget("팀 상세: Nongshim RedForce Official Esports Organization")
        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(playerMatchCardTag(MATCH_ID)))
        assertMinimumTarget("경기 상세: VCT Pacific")
    }

    @Composable
    private fun Fixture(
        state: PlayerDetailContentState = PlayerDetailContentState.Loading,
        onTeamClick: (String) -> Unit = {},
        onMatchClick: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
        favorite: PlayerFavoriteUiState = PlayerFavoriteUiState(),
        onFavoriteClick: () -> Unit = {},
        onFavoriteRetry: () -> Unit = {},
        onFavoriteErrorDismiss: () -> Unit = {},
    ) = VlrTheme {
        Box(Modifier.fillMaxSize().testTag(TEST_ROOT_TAG)) {
            PlayerDetailContent(
                uiState = PlayerDetailUiState(state, favorite), listState = rememberLazyListState(),
                onBack = onBack, onTeamClick = onTeamClick, onMatchClick = onMatchClick, onRetry = onRetry,
                onFavoriteClick = onFavoriteClick,
                onFavoriteRetry = onFavoriteRetry,
                onFavoriteErrorDismiss = onFavoriteErrorDismiss,
            )
        }
    }

    private companion object {
        const val TEAM_ID = "1001"
        const val MATCH_ID = "3001"
        const val TEAM_IMAGE_URL = "https://example.test/t1-logo.png"
        const val TEST_ROOT_TAG = "player-detail-test-root"
        val player = PlayerDetail(
            id = "123", profile = PlayerProfile("stax", "Kim Gu-taek", listOf("alias"), "kr", "Korea"),
            currentTeam = PlayerCurrentTeam(TEAM_ID, "T1"),
            agentStats = listOf(PlayerAgentStat("Jett", 0, 0, null, null, 0.0, 0.0, null, null, null, null, null, null, null, null, null, null)),
            recentMatches = listOf(PlayerRecentMatch(MATCH_ID, "VCT Pacific", null, PlayerRecentMatchTeam("T1", "T1"), PlayerRecentMatchTeam("GEN", null), null, 0, PlayerRecentMatchOutcome.WIN, null)),
        )
    }
}
