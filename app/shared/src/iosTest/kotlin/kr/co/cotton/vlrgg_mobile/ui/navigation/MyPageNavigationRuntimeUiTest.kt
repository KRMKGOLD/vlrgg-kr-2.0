package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kr.co.cotton.vlrgg_mobile.di.AppViewModelFactory
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.MyPageViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.myPagePlayerRowTag
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.myPageTeamRowTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class MyPageNavigationRuntimeUiTest {
    @Test
    fun teamPlayerSearchAndRootRoundTripsPreserveMyPageScrollAndExactDestinations() = runComposeUiTest {
        val repository = FakeFavoriteRepository()
        val viewModelFactory = AppViewModelFactory(
            viewModelProviders = mapOf(
                MyPageViewModel::class to { MyPageViewModel(repository) },
            ),
            assistedFactoryProviders = emptyMap(),
            manualAssistedFactoryProviders = emptyMap(),
        )
        val hostOwner = TestHostViewModelStoreOwner()
        var navigationState: AppNavigationState? = null

        setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides viewModelFactory,
                LocalViewModelStoreOwner provides hostOwner,
            ) {
                VlrTheme {
                    AppNavigationRuntime(
                        initialSelectedRoot = MyPageRoot,
                        onNavigationStateAvailable = { navigationState = it },
                        entryContent = { destination, onSearch, onPush, onBack ->
                            when (destination) {
                                MyPageRoot -> NavigationContent(
                                    destination = destination,
                                    onSearch = onSearch,
                                    onPush = onPush,
                                    onBack = onBack,
                                )

                                Search -> Button(onClick = onBack) { Text("fixture:search") }
                                is TeamDetail -> Button(onClick = onBack) { Text("fixture:team:${destination.teamId}") }
                                is PlayerDetail -> Button(onClick = onBack) { Text("fixture:player:${destination.playerId}") }
                                is RootNavKey -> Text("fixture:root:${destination.destinationDescriptor.title}")
                                else -> error("Unexpected destination: $destination")
                            }
                        },
                    )
                }
            }
        }

        onNodeWithTag(myPageTeamRowTag("team-1")).performClick()
        assertTopDestination(navigationState, TeamDetail("team-1"))
        onNodeWithText("fixture:team:team-1").performClick()
        onNodeWithTag(myPageTeamRowTag("team-1")).assertExists()

        val playerTag = myPagePlayerRowTag("player-44")
        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(playerTag))
        onNodeWithTag(playerTag).assertIsDisplayed().performClick()
        assertTopDestination(navigationState, PlayerDetail("player-44"))
        onNodeWithText("fixture:player:player-44").performClick()
        onNodeWithTag(playerTag).assertIsDisplayed()

        onNodeWithContentDescription("검색").performClick()
        assertTopDestination(navigationState, Search)
        onNodeWithText("fixture:search").performClick()
        onNodeWithTag(playerTag).assertIsDisplayed()

        onNodeWithText("News").performClick()
        onNodeWithText("fixture:root:News").assertExists()
        onNodeWithText("My Page").performClick()
        onNodeWithTag(playerTag).assertIsDisplayed()
        assertEquals(1, repository.teamSubscriptions)
        assertEquals(1, repository.playerSubscriptions)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertTopDestination(
        navigationState: AppNavigationState?,
        expected: AppNavKey,
    ) {
        val entry = assertIs<OverlayNavEntry>(runOnIdle {
            requireNotNull(navigationState).currentBackStack.last()
        })
        assertEquals(expected, entry.destination)
    }

    private class FakeFavoriteRepository : FavoriteRepository {
        var teamSubscriptions = 0
            private set
        var playerSubscriptions = 0
            private set

        override fun observeFavoriteTeams(): Flow<AppResult<List<FavoriteTeam>>> {
            teamSubscriptions++
            return flowOf(
                AppResult.Success(
                    (1..12).map { index ->
                        FavoriteTeam(
                            id = "team-$index",
                            name = "Team $index",
                            tag = "T$index",
                            country = "Korea",
                        )
                    },
                ),
            )
        }

        override fun observeFavoritePlayers(): Flow<AppResult<List<FavoritePlayer>>> {
            playerSubscriptions++
            return flowOf(
                AppResult.Success(
                    listOf(
                        FavoritePlayer(
                            id = "player-44",
                            handle = "Player 44",
                            realName = "Player Forty Four",
                            countryCode = "KR",
                            countryName = "Korea",
                        ),
                    ),
                ),
            )
        }

        override suspend fun getFavoriteTeams() = error("MyPage observes teams")

        override suspend fun getFavoritePlayers() = error("MyPage observes players")

        override suspend fun addFavoriteTeam(favorite: FavoriteTeam) = error("MyPage never adds teams")

        override suspend fun addFavoritePlayer(favorite: FavoritePlayer) = error("MyPage never adds players")

        override suspend fun removeFavoriteTeam(teamId: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun removeFavoritePlayer(playerId: String): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class TestHostViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
