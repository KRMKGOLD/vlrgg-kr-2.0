package kr.co.cotton.vlrgg_mobile.ui.navigation

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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kr.co.cotton.vlrgg_mobile.di.AppViewModelFactory
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleBlock
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsLinkKind
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsPage
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResults
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail as TeamIdentity
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.SearchRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.TeamRepository
import kr.co.cotton.vlrgg_mobile.ui.feature.news.detail.NewsDetailViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.search.SearchViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.TEAM_DETAIL_HEADER_TAG
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.teamMatchCardTag
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.teamNewsRowTag
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.teamPlayerRowTag
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.TeamDetailViewModel
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TeamDetailNavigationRuntimeUiTest {

    @Test
    fun teamDetailUsesTheLiveScreenAndPreservesLoadedScrollAcrossNestedAndRootRoundTrips() = runComposeUiTest {
        var navigationState: AppNavigationState? = null
        val teamRepository = FakeTeamRepository()
        val viewModelFactory = teamViewModelFactory(teamRepository)

        setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides viewModelFactory,
                LocalViewModelStoreOwner provides TestHostViewModelStoreOwner(),
            ) {
                VlrTheme {
                    AppNavigationRuntime(
                        onNavigationStateAvailable = { navigationState = it },
                        entryContent = { destination, onSearch, onPush, onBack ->
                            if (destination is TeamDetail || destination is MatchDetail || destination is PlayerDetail) {
                                NavigationContent(
                                    destination = destination,
                                    onSearch = onSearch,
                                    onPush = onPush,
                                    onBack = onBack,
                                )
                            } else {
                                Text("fixture:${destination.destinationDescriptor.marker}")
                            }
                        },
                    )
                }
            }
        }

        runOnIdle { requireNotNull(navigationState).push(TeamDetail(TEAM_ID)) }

        onNodeWithTag(TEAM_DETAIL_HEADER_TAG).assertExists()
        onNodeWithText("team_detail").assertDoesNotExist()
        scrollToTag(teamPlayerRowTag(LAST_PLAYER_ID))
        onNodeWithTag(teamPlayerRowTag(LAST_PLAYER_ID)).assertIsDisplayed()

        runOnIdle { requireNotNull(navigationState).selectRoot(NewsRoot) }
        onNodeWithText("fixture:news").assertExists()
        runOnIdle { requireNotNull(navigationState).selectRoot(MyPageRoot) }
        onNodeWithTag(teamPlayerRowTag(LAST_PLAYER_ID)).assertIsDisplayed()

        scrollToTag(teamMatchCardTag(MATCH_ID))
        onNodeWithTag(teamMatchCardTag(MATCH_ID)).performClick()
        assertTopDestination(navigationState, MatchDetail(MATCH_ID))
        onNodeWithText("match_detail").assertExists()
        onNodeWithText("Back").performClick()
        onNodeWithTag(teamMatchCardTag(MATCH_ID)).assertIsDisplayed()
        scrollToTag(teamPlayerRowTag(LAST_PLAYER_ID))
        onNodeWithTag(teamPlayerRowTag(LAST_PLAYER_ID)).assertIsDisplayed()

        onNodeWithTag(teamPlayerRowTag(LAST_PLAYER_ID)).performClick()
        assertTopDestination(navigationState, PlayerDetail(LAST_PLAYER_ID))
        onNodeWithText("player_detail").assertExists()
        onNodeWithText("Back").performClick()
        onNodeWithTag(teamPlayerRowTag(LAST_PLAYER_ID)).assertIsDisplayed()

        scrollToTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG))
        onNodeWithTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG)).performClick()
        assertTopDestination(navigationState, NewsDetail(ARTICLE_ID, ARTICLE_SLUG))
        onNodeWithText("fixture:news_detail").assertExists()
        runOnIdle { requireNotNull(navigationState).popOverlay() }
        onNodeWithTag(teamNewsRowTag(ARTICLE_ID, ARTICLE_SLUG)).assertIsDisplayed()

        kotlin.test.assertEquals(1, teamRepository.requestedIds.count { it == TEAM_ID })
    }

    @Test
    fun searchAndNewsTeamLinksOpenTheLiveTeamScreenAndBackPreservesTheirInitiatingEntries() = runComposeUiTest {
        val teamRepository = FakeTeamRepository()
        val hostOwner = TestHostViewModelStoreOwner()
        val viewModelFactory = AppViewModelFactory(
            viewModelProviders = mapOf(
                SearchViewModel::class to { SearchViewModel(FakeSearchRepository()) },
            ),
            assistedFactoryProviders = emptyMap(),
            manualAssistedFactoryProviders = mapOf(
                TeamDetailViewModel.Factory::class to {
                    TeamDetailViewModel.Factory { teamId -> TeamDetailViewModel(teamRepository, teamId) }
                },
                NewsDetailViewModel.Factory::class to {
                    NewsDetailViewModel.Factory { articleId, slug ->
                        NewsDetailViewModel(FakeNewsRepository(), articleId, slug)
                    }
                },
            ),
        )
        var navigationState: AppNavigationState? = null

        setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides viewModelFactory,
                LocalViewModelStoreOwner provides hostOwner,
            ) {
                VlrTheme {
                    AppNavigationRuntime(
                        onNavigationStateAvailable = { navigationState = it },
                        entryContent = { destination, onSearch, onPush, onBack ->
                            if (destination is Search || destination is TeamDetail || destination is NewsDetail) {
                                NavigationContent(
                                    destination = destination,
                                    onSearch = onSearch,
                                    onPush = onPush,
                                    onBack = onBack,
                                )
                            } else {
                                Text("fixture:${destination.destinationDescriptor.marker}")
                            }
                        },
                    )
                }
            }
        }

        runOnIdle { requireNotNull(navigationState).push(Search) }
        onNodeWithContentDescription("검색어", useUnmergedTree = true).performTextInput(TEAM_NAME)
        onNodeWithContentDescription("검색").performClick()
        onNodeWithTag("search-row-Team:$TEAM_ID").performClick()
        assertTopDestination(navigationState, TeamDetail(TEAM_ID))
        onNodeWithTag(TEAM_DETAIL_HEADER_TAG).assertExists()
        onNodeWithContentDescription("뒤로 가기").performClick()
        onNodeWithTag("search-row-Team:$TEAM_ID").assertExists()
        runOnIdle { requireNotNull(navigationState).popOverlay() }

        runOnIdle { requireNotNull(navigationState).push(NewsDetail(SOURCE_ARTICLE_ID, SOURCE_ARTICLE_SLUG)) }
        onNodeWithText(SOURCE_ARTICLE_TITLE).assertExists()
        onNodeWithText("Open $NEWS_TEAM_LINK_LABEL").performClick()
        assertTopDestination(navigationState, TeamDetail(TEAM_ID))
        onNodeWithTag(TEAM_DETAIL_HEADER_TAG).assertExists()
        onNodeWithContentDescription("뒤로 가기").performClick()
        onNodeWithText(SOURCE_ARTICLE_TITLE).assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollToTag(tag: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun assertTopDestination(
        navigationState: AppNavigationState?,
        expected: AppNavKey,
    ) {
        kotlin.test.assertEquals(
            expected,
            (requireNotNull(navigationState).currentBackStack.last() as OverlayNavEntry).destination,
        )
    }

    private fun teamViewModelFactory(repository: TeamRepository) = AppViewModelFactory(
        viewModelProviders = emptyMap(),
        assistedFactoryProviders = emptyMap(),
        manualAssistedFactoryProviders = mapOf(
            TeamDetailViewModel.Factory::class to {
                TeamDetailViewModel.Factory { teamId -> TeamDetailViewModel(repository, teamId) }
            },
        ),
    )

    private class FakeTeamRepository : TeamRepository {
        val requestedIds = mutableListOf<String>()

        override suspend fun getTeamDetail(teamId: String): AppResult<TeamIdentity> {
            requestedIds += teamId
            return AppResult.Success(
                TeamIdentity(
                    id = teamId,
                    name = TEAM_NAME,
                    tag = "T1",
                    country = "Korea",
                    upcomingMatches = listOf(
                        TeamMatch(
                            id = MATCH_ID,
                            eventName = "VCT Pacific",
                            eventStage = "Playoffs",
                            teamName = TEAM_NAME,
                            opponentName = "GEN",
                            statusText = "예정",
                            scheduledAtText = "2026-08-28",
                        ),
                    ),
                    recentMatches = emptyList(),
                    players = (1..24).map { index ->
                        TeamRosterMember(
                            id = "player-$index",
                            handle = "Player $index",
                            realName = null,
                            roleLabels = emptyList(),
                        )
                    },
                    staff = emptyList(),
                    news = listOf(TeamNews(ARTICLE_ID, ARTICLE_SLUG, "T1 news", "2026-08-27")),
                ),
            )
        }
    }

    private class FakeSearchRepository : SearchRepository {
        override suspend fun getSearch(query: String): AppResult<SearchResults> = AppResult.Success(
            SearchResults(
                query = query,
                items = listOf(TeamSearchResult(TEAM_ID, TEAM_NAME, "Pacific")),
            ),
        )
    }

    private class FakeNewsRepository : NewsRepository {
        override suspend fun getNewsPage(page: Int): AppResult<NewsPage> = error("News list is not used")

        override suspend fun getNewsArticle(articleId: String, slug: String): AppResult<NewsArticle> =
            AppResult.Success(
                NewsArticle(
                    articleId = articleId,
                    slug = slug,
                    title = SOURCE_ARTICLE_TITLE,
                    author = "VLR.GG",
                    publishedAt = "2026-08-27",
                    blocks = listOf(
                        NewsArticleBlock.Paragraph(
                            listOf(
                                NewsArticleInline.Text("Open "),
                                NewsArticleInline.Link(
                                    NEWS_TEAM_LINK_LABEL,
                                    NewsLinkKind.TEAM,
                                    "$TEAM_ID/t1",
                                ),
                            ),
                        ),
                    ),
                ),
            )
    }

    private class TestHostViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private companion object {
        const val TEAM_ID = "1001"
        const val TEAM_NAME = "T1"
        const val MATCH_ID = "match-1001"
        const val LAST_PLAYER_ID = "player-24"
        const val ARTICLE_ID = "700755"
        const val ARTICLE_SLUG = "t1-news"
        const val SOURCE_ARTICLE_ID = "400"
        const val SOURCE_ARTICLE_SLUG = "team-source"
        const val SOURCE_ARTICLE_TITLE = "T1 source article"
        const val NEWS_TEAM_LINK_LABEL = "T1 link"
    }
}
