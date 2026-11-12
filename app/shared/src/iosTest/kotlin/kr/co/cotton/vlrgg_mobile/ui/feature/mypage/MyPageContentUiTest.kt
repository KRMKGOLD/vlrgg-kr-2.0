package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
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
import kotlin.test.assertFalse
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

        // Skeletons replace the spinners and keep the loading descriptions with indeterminate progress.
        listOf("즐겨찾기한 팀을 불러오는 중", "즐겨찾기한 선수를 불러오는 중").forEach { description ->
            onNodeWithContentDescription(description)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        }
        onNodeWithText("팀").assertExists()
        onNodeWithText("선수").assertExists()
        onNodeWithText("즐겨찾기는 이 기기에만 저장됩니다.").assertExists()
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
            // A single team is the first of two 8dp-apart columns inside the 16dp insets.
            onNodeWithTag(myPageTeamRowTag(favoriteTeam.id))
                .assertLeftPositionInRootIsEqualTo(16.dp)
                .assertWidthIsEqualTo(160.dp)
                .assertHeightIsAtLeast(48.dp)
            onNodeWithContentDescription("$longTeamName 즐겨찾기 해제")
                .assertHeightIsAtLeast(48.dp)
            onNodeWithText(longTeamName).assertExists()

            onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(myPagePlayerRowTag(favoritePlayer.id)))
            // Player favorites are full-width identity rows; the 48dp remove star keeps the 4dp end inset.
            onNodeWithTag(myPagePlayerRowTag(favoritePlayer.id))
                .assertLeftPositionInRootIsEqualTo(0.dp)
                .assertWidthIsEqualTo(360.dp)
                .assertHeightIsAtLeast(48.dp)
            onNodeWithContentDescription("$longPlayerHandle 즐겨찾기 해제")
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsEqualTo(48.dp)
                .assertLeftPositionInRootIsEqualTo(308.dp)
            onNodeWithText(longPlayerHandle).assertExists()
            onNodeWithContentDescription("검색").assertHeightIsAtLeast(48.dp)
        }

    @Test
    fun favoritePlayersAreKeyedLazyItemsThatComposeOnlyWhenScrolledIntoView() =
        runSkikoComposeUiTest(size = Size(360f, 600f)) {
            val players = (1..40).map { index ->
                FavoritePlayer(
                    id = "player-$index",
                    handle = "Player $index",
                    realName = null,
                    countryCode = null,
                    countryName = null,
                )
            }
            var removed: String? = null
            setContent {
                TestContent(
                    uiState = MyPageUiState(
                        favoriteTeams = FavoriteSectionState.Empty,
                        favoritePlayers = FavoriteSectionState.Content(players),
                    ),
                    onRemovePlayer = { removed = it },
                )
            }

            // Rows below the viewport are not composed until the list reaches them.
            onNodeWithTag(myPagePlayerRowTag("player-1")).assertExists()
            onNodeWithTag(myPagePlayerRowTag("player-40")).assertDoesNotExist()

            onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(myPagePlayerRowTag("player-40")))
            onNodeWithTag(myPagePlayerRowTag("player-40")).assertExists()
            onNodeWithTag(myPagePlayerRowTag("player-1")).assertDoesNotExist()
            onNodeWithContentDescription("Player 40 즐겨찾기 해제").performClick()
            assertEquals("player-40", removed)
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

    @Test
    fun sectionsUseKoreanTitlesWithVisibleCountsAndTheStorageNote() = runComposeUiTest {
        setContent {
            TestContent(
                uiState = MyPageUiState(
                    favoriteTeams = FavoriteSectionState.Content(listOf(team("team-1"), team("team-2"))),
                    favoritePlayers = FavoriteSectionState.Content(listOf(player("player-1"))),
                ),
            )
        }

        onNode(hasText("팀") and hasAnyAncestor(hasTestTag(MY_PAGE_TEAM_SECTION_TAG)), useUnmergedTree = true).assertExists()
        onNode(hasText("2") and hasAnyAncestor(hasTestTag(MY_PAGE_TEAM_SECTION_TAG)), useUnmergedTree = true).assertExists()
        onNode(hasText("선수") and hasAnyAncestor(hasTestTag(MY_PAGE_PLAYER_SECTION_TAG)), useUnmergedTree = true).assertExists()
        onNode(hasText("1") and hasAnyAncestor(hasTestTag(MY_PAGE_PLAYER_SECTION_TAG)), useUnmergedTree = true).assertExists()
        onNodeWithText("Favorite Teams").assertDoesNotExist()
        onNodeWithText("Favorite Players").assertDoesNotExist()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("즐겨찾기는 이 기기에만 저장됩니다."))
        onNodeWithText("즐겨찾기는 이 기기에만 저장됩니다.").assertExists()
    }

    @Test
    fun teamTilesFillTwoColumnsInStoredOrderWithEqualRowHeights() =
        runSkikoComposeUiTest(size = Size(360f, 1200f)) {
            val teams = listOf(team("c"), team("a"), team("b"))
            setContent {
                TestContent(
                    uiState = MyPageUiState(
                        favoriteTeams = FavoriteSectionState.Content(
                            teams.mapIndexed { index, favorite ->
                                if (index == 1) favorite.copy(name = "Second team with a much longer name that wraps") else favorite
                            },
                        ),
                        favoritePlayers = FavoriteSectionState.Empty,
                    ),
                )
            }

            val first = onNodeWithTag(myPageTeamRowTag("c")).fetchSemanticsNode().boundsInRoot
            val second = onNodeWithTag(myPageTeamRowTag("a")).fetchSemanticsNode().boundsInRoot
            val third = onNodeWithTag(myPageTeamRowTag("b")).fetchSemanticsNode().boundsInRoot
            assertEquals(16f, first.left)
            assertEquals(160f, first.width)
            assertEquals(184f, second.left)
            assertEquals(344f, second.right)
            assertEquals(first.top, second.top)
            // A longer name grows both tiles of the row together.
            assertEquals(first.height, second.height)
            assertEquals(16f, third.left)
            assertEquals(first.bottom + 8f, third.top, 0.5f)
            val header = onNodeWithTag(MY_PAGE_TEAM_SECTION_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals(header.bottom + 4f, first.top, 0.5f)
            // The team tag is the logo placeholder at the tile size.
            onNodeWithTag(myPageTeamImagePlaceholderTag("c"), useUnmergedTree = true)
                .assertTextEquals("TAG-c")
                .assert(SemanticsMatcher("16sp placeholder") { node -> node.textLayout().layoutInput.style.fontSize == 16.sp })
        }

    @Test
    fun tileStarIsASeparateNamed48DpTargetFromTheTileDetailTarget() =
        runSkikoComposeUiTest(size = Size(360f, 800f)) {
            val favorite = team("drx")
            var opened: String? = null
            var removed: String? = null
            setContent {
                TestContent(
                    uiState = MyPageUiState(
                        favoriteTeams = FavoriteSectionState.Content(listOf(favorite)),
                        favoritePlayers = FavoriteSectionState.Empty,
                    ),
                    onTeamClick = { opened = it },
                    onRemoveTeam = { removed = it },
                )
            }

            val tile = onNodeWithTag(myPageTeamRowTag(favorite.id))
            tile.assert(hasContentDescription("팀 상세: ${favorite.name}"))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val star = onNodeWithContentDescription("${favorite.name} 즐겨찾기 해제")
                .assertWidthIsEqualTo(48.dp)
                .assertHeightIsEqualTo(48.dp)
            val tileBounds = tile.fetchSemanticsNode().boundsInRoot
            val starBounds = star.fetchSemanticsNode().boundsInRoot
            assertTrue(tileBounds.contains(starBounds.topLeft) && starBounds.right <= tileBounds.right)
            // The star is not merged into the tile, so the tile does not read it out as its own name.
            assertFalse(star.fetchSemanticsNode().id == tile.fetchSemanticsNode().id)

            star.performClick()
            assertEquals(favorite.id, removed)
            assertEquals(null, opened)
            tile.performClick()
            assertEquals(favorite.id, opened)
        }

    @Test
    fun sectionStateCombinationsKeepBothTitlesAndTheOtherSection() = runComposeUiTest {
        var state by mutableStateOf(MyPageUiState())
        setContent { TestContent(uiState = state) }

        val teams = FavoriteSectionState.Content(listOf(team("team-1")))
        val players = FavoriteSectionState.Content(listOf(player("player-1")))
        data class Case(val state: MyPageUiState, val present: List<String>, val absent: List<String>)
        listOf(
            // 30-4 team empty · players shown
            Case(MyPageUiState(FavoriteSectionState.Empty, players), listOf("즐겨찾기한 팀이 없습니다."), listOf("즐겨찾기한 선수가 없습니다.")),
            // 30-5 teams shown · player empty
            Case(MyPageUiState(teams, FavoriteSectionState.Empty), listOf("즐겨찾기한 선수가 없습니다."), listOf("즐겨찾기한 팀이 없습니다.")),
            // 30-6 team error · players shown
            Case(MyPageUiState(FavoriteSectionState.Error, players), listOf("즐겨찾기한 팀을 불러오지 못했습니다."), listOf("즐겨찾기한 선수를 불러오지 못했습니다.")),
            // 30-7 teams shown · player error
            Case(MyPageUiState(teams, FavoriteSectionState.Error), listOf("즐겨찾기한 선수를 불러오지 못했습니다."), listOf("즐겨찾기한 팀을 불러오지 못했습니다.")),
        ).forEach { case ->
            state = case.state
            waitForIdle()
            onNodeWithTag(MY_PAGE_TEAM_SECTION_TAG).assertExists()
            onNodeWithTag(MY_PAGE_PLAYER_SECTION_TAG).assertExists()
            onNodeWithTag(MY_PAGE_FULL_ERROR_TAG).assertDoesNotExist()
            case.present.forEach { onNodeWithText(it).assertExists() }
            case.absent.forEach { onNodeWithText(it).assertDoesNotExist() }
            if (case.state.favoriteTeams is FavoriteSectionState.Content) {
                onNodeWithTag(myPageTeamRowTag("team-1")).assertExists()
            }
            if (case.state.favoritePlayers is FavoriteSectionState.Content) {
                onNodeWithTag(myPagePlayerRowTag("player-1")).assertExists()
            }
        }

        // 30-11 team loading · players shown
        state = MyPageUiState(FavoriteSectionState.Loading, players)
        waitForIdle()
        onNodeWithContentDescription("즐겨찾기한 팀을 불러오는 중").assertExists()
        onNodeWithContentDescription("즐겨찾기한 선수를 불러오는 중").assertDoesNotExist()
        onNodeWithTag(myPagePlayerRowTag("player-1")).assertExists()
    }

    @Test
    fun removalHidesOnlyTheTargetAndAFailureRestoresTheTileWithARetrySnackbar() = runComposeUiTest {
        val teams = listOf(team("team-1"), team("team-2"))
        val removing = FavoriteRemovalTarget.Team("team-1")
        var state by mutableStateOf(
            MyPageUiState(
                favoriteTeams = FavoriteSectionState.Content(teams),
                favoritePlayers = FavoriteSectionState.Content(listOf(player("player-1"))),
            ),
        )
        var removalRetries = 0
        setContent { TestContent(uiState = state, onRemovalRetry = { removalRetries++ }) }

        // 30-9: the ViewModel hides only the target while the removal runs.
        state = state.copy(
            favoriteTeams = FavoriteSectionState.Content(teams.drop(1)),
            removingFavorite = removing,
        )
        waitForIdle()
        onNodeWithTag(myPageTeamRowTag("team-1")).assertDoesNotExist()
        onNodeWithTag(myPageTeamRowTag("team-2")).assertExists()
        onNodeWithTag(myPagePlayerRowTag("player-1")).assertExists()
        onNode(hasText("1") and hasAnyAncestor(hasTestTag(MY_PAGE_TEAM_SECTION_TAG)), useUnmergedTree = true).assertExists()
        onNodeWithTag(MY_PAGE_REMOVAL_SNACKBAR_TAG).assertDoesNotExist()

        // 30-10: failure restores the tile in its stored order with the star and shows the retry Snackbar.
        state = state.copy(
            favoriteTeams = FavoriteSectionState.Content(teams),
            removingFavorite = null,
            failedRemoval = removing,
        )
        waitForIdle()
        val restored = onNodeWithTag(myPageTeamRowTag("team-1")).fetchSemanticsNode().boundsInRoot
        val other = onNodeWithTag(myPageTeamRowTag("team-2")).fetchSemanticsNode().boundsInRoot
        assertTrue(restored.left < other.left)
        onNodeWithContentDescription("Team team-1 즐겨찾기 해제").assertExists()
        onNode(hasText("2") and hasAnyAncestor(hasTestTag(MY_PAGE_TEAM_SECTION_TAG)), useUnmergedTree = true).assertExists()
        onNodeWithTag(MY_PAGE_REMOVAL_SNACKBAR_TAG).assertExists()
        onNodeWithText("즐겨찾기 해제에 실패했습니다.").assertExists()
        onNodeWithText("재시도").performClick()
        assertEquals(1, removalRetries)
    }

    @Test
    fun tileTextWrapsWithoutClippingOnNarrowScreensAndDoubleFonts() {
        val longName = "xXxMasterProMegaTeamxXx Frankfurt Bahnhofsviertel Esports"
        val longMeta = "아주긴대한민국대표팀태그 · 대한민국"
        val favorites = listOf(
            FavoriteTeam(id = "long", name = longName, tag = "아주긴대한민국대표팀태그", country = "대한민국"),
            FavoriteTeam(id = "short", name = "T1", tag = "T1", country = "대한민국"),
        )
        listOf(320f, 360f, 411f).forEach { width ->
            runSkikoComposeUiTest(size = Size(width, 2400f)) {
                var fontScale by mutableStateOf(1f)
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        TestContent(
                            uiState = MyPageUiState(
                                favoriteTeams = FavoriteSectionState.Content(favorites),
                                favoritePlayers = FavoriteSectionState.Empty,
                            ),
                        )
                    }
                }

                var defaultHeight = 0f
                listOf(1f, 2f).forEach { scale ->
                    fontScale = scale
                    waitForIdle()
                    val label = "width=$width fontScale=$scale"
                    val tile = onNodeWithTag(myPageTeamRowTag("long")).fetchSemanticsNode().boundsInRoot
                    val pair = onNodeWithTag(myPageTeamRowTag("short")).fetchSemanticsNode().boundsInRoot
                    assertTrue(tile.left >= 0f && pair.right <= width, "$label tiles leave the screen")
                    assertEquals(tile.height, pair.height, "$label tiles of a row differ in height")
                    if (scale == 1f) defaultHeight = tile.height else assertTrue(tile.height > defaultHeight, "$label tile did not grow")
                    listOf(longName, longMeta).forEach { text ->
                        val node = onNode(hasText(text) and hasAnyAncestor(hasTestTag(myPageTeamRowTag("long"))), useUnmergedTree = true)
                        val layout = node.fetchSemanticsNode().textLayout()
                        val bounds = node.fetchSemanticsNode().boundsInRoot
                        // Every character reaches a laid-out line (no maxLines cut), and nothing is ellipsized.
                        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
                        assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
                        assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
                        assertTrue(
                            bounds.left >= tile.left && bounds.right <= tile.right + 0.5f && bounds.bottom <= tile.bottom + 0.5f,
                            "$label '$text' leaves its tile: $bounds / $tile",
                        )
                    }
                    val star = onNodeWithContentDescription("$longName 즐겨찾기 해제").fetchSemanticsNode().boundsInRoot
                    assertEquals(48f, star.width, "$label star target")
                    assertTrue(star.right <= tile.right, "$label star leaves its tile")
                }
            }
        }
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

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }
}
