package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail as SeriesIdentity
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResults
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.repository.SeriesRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.SearchRepository
import kr.co.cotton.vlrgg_mobile.ui.feature.search.SearchViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.series.detail.SeriesDetailViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.series.detail.seriesEventRowTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SeriesDetailNavigationRuntimeUiTest {

    @Test
    fun seriesEntryRetainsItsLoadedStateAndScrollAcrossEventAndRootRoundTrips() = runComposeUiTest {
        val repository = FakeSeriesRepository()
        val searchRepository = FakeSearchRepository()
        val hostOwner = TestHostViewModelStoreOwner()
        var navigationState: AppNavigationState? = null
        val factory = AppViewModelFactory(
            viewModelProviders = mapOf(
                SearchViewModel::class to { SearchViewModel(searchRepository) },
            ),
            assistedFactoryProviders = emptyMap(),
            manualAssistedFactoryProviders = mapOf(
                SeriesDetailViewModel.Factory::class to {
                    SeriesDetailViewModel.Factory { seriesId -> SeriesDetailViewModel(repository, seriesId) }
                },
            ),
        )

        setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides factory,
                LocalViewModelStoreOwner provides hostOwner,
            ) {
                VlrTheme {
                    AppNavigationRuntime(
                        initialSelectedRoot = EventsRoot,
                        onNavigationStateAvailable = { navigationState = it },
                        entryContent = { destination, onSearch, onPush, onBack ->
                            if (destination is Search || destination is SeriesDetail) {
                                NavigationContent(destination, onSearch, onPush, onBack)
                            } else {
                                Text("fixture:${destination.destinationDescriptor.marker}")
                            }
                        },
                    )
                }
            }
        }

        runOnIdle { requireNotNull(navigationState).push(Search) }
        onNodeWithContentDescription("검색어", useUnmergedTree = true).performTextInput("Champions")
        onNodeWithContentDescription("검색").performClick()
        scrollToTag("search-row-Series:$SERIES_ID")
        onNodeWithTag("search-row-Series:$SERIES_ID").performClick()
        onNodeWithTag(seriesEventRowTag(FIRST_EVENT_ID)).performClick()
        assertEquals(EventDetail(FIRST_EVENT_ID), (runOnIdle {
            (requireNotNull(navigationState).currentBackStack.last() as OverlayNavEntry).destination
        }))
        runOnIdle { requireNotNull(navigationState).popOverlay() }
        onNodeWithTag(seriesEventRowTag(FIRST_EVENT_ID)).assertExists()
        scrollToTag(seriesEventRowTag(LAST_EVENT_ID))
        onNodeWithTag(seriesEventRowTag(LAST_EVENT_ID)).performClick()
        assertEquals(EventDetail(LAST_EVENT_ID), (runOnIdle {
            (requireNotNull(navigationState).currentBackStack.last() as OverlayNavEntry).destination
        }))
        onNodeWithText("fixture:event_detail").assertExists()

        runOnIdle { requireNotNull(navigationState).popOverlay() }
        onNodeWithTag(seriesEventRowTag(LAST_EVENT_ID)).assertExists()
        runOnIdle { requireNotNull(navigationState).selectRoot(NewsRoot) }
        onNodeWithText("fixture:news").assertExists()
        runOnIdle { requireNotNull(navigationState).selectRoot(EventsRoot) }
        onNodeWithTag(seriesEventRowTag(LAST_EVENT_ID)).assertExists()
        assertEquals(1, repository.requestedIds.count { it == SERIES_ID })
        onNodeWithContentDescription("뒤로 가기").performClick()
        onNodeWithContentDescription("검색어", useUnmergedTree = true).assertTextContains("Champions")
        onNodeWithTag("search-row-Series:$SERIES_ID").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollToTag(tag: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }

    private class FakeSeriesRepository : SeriesRepository {
        val requestedIds = mutableListOf<String>()

        override suspend fun getSeriesDetail(seriesId: String): AppResult<SeriesIdentity> {
            requestedIds += seriesId
            return AppResult.Success(
                SeriesIdentity(
                    id = seriesId,
                    name = "Champions Tour",
                    description = null,
                    upcomingEvents = (1..23).map { index ->
                        EventSummary("event-$index", "Event $index", EventStatus.UPCOMING)
                    },
                    completedEvents = listOf(
                        EventSummary(LAST_EVENT_ID, "Event 24", EventStatus.COMPLETED),
                    ),
                ),
            )
        }
    }

    private class FakeSearchRepository : SearchRepository {
        override suspend fun getSearch(query: String): AppResult<SearchResults> = AppResult.Success(
            SearchResults(
                query = query,
                items = (1..24).map { index ->
                    SeriesSearchResult(index.toString(), "Champions Tour $index", null)
                },
            ),
        )
    }

    private class TestHostViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private companion object {
        const val SERIES_ID = "24"
        const val FIRST_EVENT_ID = "event-1"
        const val LAST_EVENT_ID = "event-24"
    }
}
