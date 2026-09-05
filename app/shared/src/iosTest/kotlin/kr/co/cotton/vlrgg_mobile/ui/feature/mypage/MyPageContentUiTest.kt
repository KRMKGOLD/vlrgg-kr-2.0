package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(DelicateCoilApi::class, ExperimentalTestApi::class)
class MyPageContentUiTest {
    @Test
    fun teamImageUsesStoredUrlAndKeepsTheExistingPlaceholderForFailureNullAndBlankUrls() {
        val imageUrl = "https://cdn.example.com/drx.png"
        val requestedUrls = mutableListOf<Any?>()
        var imageLoader: ImageLoader? = null
        val fakeEngine = FakeImageLoaderEngine.Builder()
            .default(
                Interceptor { chain ->
                    requestedUrls += chain.request.data
                    ErrorResult(null, chain.request, IllegalStateException("fixture failure"))
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
                val favorite = team("team-image").copy(imageUrl = imageUrl)
                var selectedTeamId: String? = null
                setContent {
                    TestContent(
                        uiState = MyPageUiState(
                            favoriteTeams = FavoriteSectionState.Content(listOf(favorite)),
                            favoritePlayers = FavoriteSectionState.Empty,
                        ),
                        onTeamClick = { selectedTeamId = it },
                    )
                }
                assertEquals(imageUrl, requestedUrls.single())
                onNodeWithTag(myPageTeamImagePlaceholderTag(favorite.id), useUnmergedTree = true).assertExists()
                onNodeWithTag(myPageTeamRowTag(favorite.id)).performClick()
                assertEquals(favorite.id, selectedTeamId)

                listOf<String?>(null, " ").forEach { missingUrl ->
                    setContent {
                        TestContent(
                            uiState = MyPageUiState(
                                favoriteTeams = FavoriteSectionState.Content(
                                    listOf(favorite.copy(imageUrl = missingUrl)),
                                ),
                                favoritePlayers = FavoriteSectionState.Empty,
                            ),
                        )
                    }
                    onNodeWithTag(myPageTeamImagePlaceholderTag(favorite.id), useUnmergedTree = true).assertExists()
                }
                assertEquals(1, requestedUrls.size)
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
    fun playerImageUsesStoredUrlAndKeepsTheExistingPlaceholderForFailureNullAndBlankUrls() {
        val imageUrl = "https://cdn.example.com/stax.png"
        val requestedUrls = mutableListOf<Any?>()
        var imageLoader: ImageLoader? = null
        val fakeEngine = FakeImageLoaderEngine.Builder()
            .default(
                Interceptor { chain ->
                    requestedUrls += chain.request.data
                    ErrorResult(null, chain.request, IllegalStateException("fixture failure"))
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
                val favorite = player("player-image").copy(imageUrl = imageUrl)
                var selectedPlayerId: String? = null
                setContent {
                    TestContent(
                        uiState = MyPageUiState(
                            favoriteTeams = FavoriteSectionState.Error,
                            favoritePlayers = FavoriteSectionState.Content(listOf(favorite)),
                        ),
                        onPlayerClick = { selectedPlayerId = it },
                    )
                }
                assertEquals(imageUrl, requestedUrls.single())
                onNodeWithTag(myPagePlayerImagePlaceholderTag(favorite.id), useUnmergedTree = true).assertExists()
                onNodeWithTag(myPagePlayerRowTag(favorite.id)).performClick()
                assertEquals(favorite.id, selectedPlayerId)
                onNodeWithText("즐겨찾기한 팀을 불러오지 못했습니다.").assertExists()

                listOf<String?>(null, " ").forEach { missingUrl ->
                    setContent {
                        TestContent(
                            uiState = MyPageUiState(
                                favoriteTeams = FavoriteSectionState.Error,
                                favoritePlayers = FavoriteSectionState.Content(
                                    listOf(favorite.copy(imageUrl = missingUrl)),
                                ),
                            ),
                        )
                    }
                    onNodeWithTag(myPagePlayerImagePlaceholderTag(favorite.id), useUnmergedTree = true).assertExists()
                }
                assertEquals(1, requestedUrls.size)
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
    fun sectionsStayOrderedAndIndependentAndEmitExactStoredIds() = runComposeUiTest {
        val teams = listOf(team("team-2"), team("team-1"))
        var selectedTeam: String? = null
        var removedTeam: String? = null
        var playerRetries = 0

        setContent {
            TestContent(
                uiState = MyPageUiState(
                    favoriteTeams = FavoriteSectionState.Content(teams),
                    favoritePlayers = FavoriteSectionState.Error,
                ),
                onTeamClick = { selectedTeam = it },
                onRemoveTeam = { removedTeam = it },
                onRetryPlayers = { playerRetries++ },
            )
        }

        val teamsTop = onNodeWithTag(MY_PAGE_TEAM_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        val playersTop = onNodeWithTag(MY_PAGE_PLAYER_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue(teamsTop < playersTop)
        onNodeWithText("Team team-2").assertExists()
        onNodeWithText("TAG-team-2 · Korea").assertExists()
        onNodeWithText("즐겨찾기한 선수를 불러오지 못했습니다.").assertExists()

        onNodeWithTag(myPageTeamRowTag("team-1")).performClick()
        assertEquals("team-1", selectedTeam)
        onNodeWithContentDescription("Team team-2 즐겨찾기 해제").performClick()
        assertEquals("team-2", removedTeam)
        onNodeWithText("재시도").performClick()
        assertEquals(1, playerRetries)
    }

    @Test
    fun favoritesOnlyLoadingShowsBothSectionProgressWithoutDeferredRegions() = runComposeUiTest {
        setContent {
            TestContent(
                uiState = MyPageUiState(
                    favoriteTeams = FavoriteSectionState.Loading,
                    favoritePlayers = FavoriteSectionState.Loading,
                ),
            )
        }

        onNodeWithContentDescription("즐겨찾기한 팀을 불러오는 중").assertExists()
        onNodeWithContentDescription("즐겨찾기한 선수를 불러오는 중").assertExists()
        onNodeWithText("Next Matches").assertDoesNotExist()
        onNodeWithText("Notifications").assertDoesNotExist()
    }

    @Test
    fun emptySectionsStayIndependentWithoutBecomingAFullError() = runComposeUiTest {
        setContent {
            TestContent(
                uiState = MyPageUiState(
                    favoriteTeams = FavoriteSectionState.Empty,
                    favoritePlayers = FavoriteSectionState.Empty,
                ),
            )
        }

        onNodeWithText("즐겨찾기한 팀이 없습니다.").assertExists()
        onNodeWithText("즐겨찾기한 선수가 없습니다.").assertExists()
        onNodeWithTag(MY_PAGE_FULL_ERROR_TAG).assertDoesNotExist()
    }

    @Test
    fun compact360DpLayoutKeepsLongKoreanFavoritesInsideInsetsAndTargetsReachable() =
        runSkikoComposeUiTest(size = Size(360f, 800f)) {
            val longTeamName = "대한민국 발로란트 챔피언십을 대표하는 아주 긴 팀 이름"
            val longPlayerHandle = "아주긴한국어선수닉네임과영문HandleTogether"
            val favoriteTeam = FavoriteTeam(
                id = "long-team",
                name = longTeamName,
                tag = "대한민국대표팀태그",
                country = "대한민국",
            )
            val favoritePlayer = FavoritePlayer(
                id = "long-player",
                handle = longPlayerHandle,
                realName = "김선수라는매우긴실명표시문자열",
                countryCode = "KR",
                countryName = "대한민국",
            )

            setContent {
                TestContent(
                    uiState = MyPageUiState(
                        favoriteTeams = FavoriteSectionState.Content(listOf(favoriteTeam)),
                        favoritePlayers = FavoriteSectionState.Content(listOf(favoritePlayer)),
                    ),
                )
            }

            onNodeWithTag(MY_PAGE_TOP_APP_BAR_TAG).assertHeightIsEqualTo(56.dp)
            onNodeWithTag(myPageTeamRowTag(favoriteTeam.id))
                .assertLeftPositionInRootIsEqualTo(16.dp)
                .assertWidthIsEqualTo(328.dp)
                .assertHeightIsAtLeast(48.dp)
            onNodeWithContentDescription("$longTeamName 즐겨찾기 해제")
                .assertHeightIsAtLeast(48.dp)
            onNodeWithText(longTeamName).assertExists()

            onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(myPagePlayerRowTag(favoritePlayer.id)))
            onNodeWithTag(myPagePlayerRowTag(favoritePlayer.id))
                .assertLeftPositionInRootIsEqualTo(16.dp)
                .assertWidthIsEqualTo(328.dp)
                .assertHeightIsAtLeast(48.dp)
            onNodeWithContentDescription("$longPlayerHandle 즐겨찾기 해제")
                .assertHeightIsAtLeast(48.dp)
            onNodeWithText(longPlayerHandle).assertExists()
            onNodeWithContentDescription("검색").assertHeightIsAtLeast(48.dp)
        }

    @Test
    fun fullErrorHidesSectionsAndUsesOnlyTheFullRetry() = runComposeUiTest {
        var fullRetries = 0
        setContent {
            TestContent(
                uiState = MyPageUiState(
                    favoriteTeams = FavoriteSectionState.Error,
                    favoritePlayers = FavoriteSectionState.Error,
                    isFullError = true,
                ),
                onRetry = { fullRetries++ },
            )
        }

        onNodeWithTag(MY_PAGE_FULL_ERROR_TAG).assertExists()
        onNodeWithTag(MY_PAGE_TEAM_SECTION_TAG).assertDoesNotExist()
        onNodeWithTag(MY_PAGE_PLAYER_SECTION_TAG).assertDoesNotExist()
        onNodeWithText("즐겨찾기를 불러오지 못했습니다.").assertExists()
        onNodeWithText("재시도").performClick()
        assertEquals(1, fullRetries)
    }

    @Test
    fun playerRowRemovalFailureAndSearchRemainAccessibleWithoutDeferredRegions() = runComposeUiTest {
        val favorite = player("player-44")
        var selectedPlayer: String? = null
        var removedPlayer: String? = null
        var removalRetries = 0
        var searches = 0

        setContent {
            TestContent(
                uiState = MyPageUiState(
                    favoriteTeams = FavoriteSectionState.Empty,
                    favoritePlayers = FavoriteSectionState.Content(listOf(favorite)),
                    failedRemoval = FavoriteRemovalTarget.Player(favorite.id),
                ),
                onSearch = { searches++ },
                onPlayerClick = { selectedPlayer = it },
                onRemovePlayer = { removedPlayer = it },
                onRemovalRetry = { removalRetries++ },
            )
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(myPagePlayerRowTag(favorite.id)))
        onNodeWithTag(myPagePlayerRowTag(favorite.id))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(favorite.id, selectedPlayer)
        onNodeWithContentDescription("Player player-44 즐겨찾기 해제")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(favorite.id, removedPlayer)
        onNodeWithTag(MY_PAGE_REMOVAL_SNACKBAR_TAG).assertExists()
        onNodeWithText("즐겨찾기 해제에 실패했습니다.").assertExists()
        onNodeWithText("재시도").performClick()
        assertEquals(1, removalRetries)
        onNodeWithContentDescription("검색")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, searches)
        onNodeWithText("Next Matches").assertDoesNotExist()
        onNodeWithText("Notifications").assertDoesNotExist()
        onNodeWithText("로그인").assertDoesNotExist()
    }

    @Composable
    private fun TestContent(
        uiState: MyPageUiState,
        onSearch: () -> Unit = {},
        onTeamClick: (String) -> Unit = {},
        onPlayerClick: (String) -> Unit = {},
        onRemoveTeam: (String) -> Unit = {},
        onRemovePlayer: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onRetryTeams: () -> Unit = {},
        onRetryPlayers: () -> Unit = {},
        onRemovalRetry: () -> Unit = {},
    ) {
        VlrTheme {
            MyPageContent(
                uiState = uiState,
                listState = rememberLazyListState(),
                onSearch = onSearch,
                onTeamClick = onTeamClick,
                onPlayerClick = onPlayerClick,
                onRemoveTeam = onRemoveTeam,
                onRemovePlayer = onRemovePlayer,
                onRetry = onRetry,
                onRetryTeams = onRetryTeams,
                onRetryPlayers = onRetryPlayers,
                onRemovalRetry = onRemovalRetry,
            )
        }
    }

    private fun team(id: String) = FavoriteTeam(
        id = id,
        name = "Team $id",
        tag = "TAG-$id",
        country = "Korea",
    )

    private fun player(id: String) = FavoritePlayer(
        id = id,
        handle = "Player $id",
        realName = "Very Long Real Name That Must Stay Available",
        countryCode = "KR",
        countryName = "Korea",
    )
}
