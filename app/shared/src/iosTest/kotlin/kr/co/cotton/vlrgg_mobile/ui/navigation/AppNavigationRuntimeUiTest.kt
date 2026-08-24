package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppNavigationRuntimeUiTest {

    @Test
    fun recompositionUsesUpdatedEntryContent() {
        var contentVersion by mutableIntStateOf(0)

        runComposeUiTest {
            setContent {
                AppNavigationRuntime(
                    entryContent = { _, _, _, _ ->
                        Text("entry-content-version:$contentVersion")
                    },
                )
            }

            onNodeWithText("entry-content-version:0").assertExists()
            runOnIdle { contentVersion = 1 }
            onNodeWithText("entry-content-version:1").assertExists()
        }
    }

    @Test
    fun rootRoundTripRetainsEntryViewModelAndSaveableStateAndPoppingDetailClearsOnlyDetail() {
        val tracker = TestViewModelTracker()
        val factory = TestViewModelFactory(tracker)
        val hostOwner = TestHostViewModelStoreOwner()

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides hostOwner) {
                    AppNavigationRuntime(
                        entryContent = { destination, onSearch, onPush, onBack ->
                            TestNavigationEntry(
                                destination = destination,
                                onSearch = onSearch,
                                onPush = onPush,
                                onBack = onBack,
                                factory = factory,
                            )
                        },
                    )
                }
            }

            onNodeWithText("root:My Page").assertExists()
            onNodeWithText("set-loaded-page").performClick()
            onNodeWithText("set-selected-tab").performClick()
            onNodeWithText("increment-counter").performClick()
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("item:20"))
            val firstVisibleItemIndex = runOnIdle {
                tracker.listStateFor(MyPageRoot).firstVisibleItemIndex
            }
            assertTrue(firstVisibleItemIndex > 0, "The fixture LazyColumn must have scrolled")
            onNodeWithText("loaded-page:loaded").assertExists()
            onNodeWithText("selected-tab:results").assertExists()
            onNodeWithText("saveable-counter:1").assertExists()
            val myPageViewModel = tracker.rootFor(MyPageRoot)

            onNodeWithText("News").performClick()
            onNodeWithText("root:News").assertExists()
            onNodeWithText("My Page").performClick()
            onNodeWithText("root:My Page").assertExists()
            onNodeWithText("loaded-page:loaded").assertExists()
            onNodeWithText("selected-tab:results").assertExists()
            onNodeWithText("saveable-counter:1").assertExists()
            assertEquals(firstVisibleItemIndex, runOnIdle {
                tracker.listStateFor(MyPageRoot).firstVisibleItemIndex
            })
            assertSame(myPageViewModel, tracker.rootFor(MyPageRoot))

            onNodeWithText("push-detail").performClick()
            onNodeWithText("detail:fixture-detail").assertExists()
            val detailViewModel = tracker.detailViewModel
            onNodeWithText("pop-detail").performClick()
            waitForIdle()

            onNodeWithText("root:My Page").assertExists()
            onNodeWithText("loaded-page:loaded").assertExists()
            onNodeWithText("selected-tab:results").assertExists()
            onNodeWithText("saveable-counter:1").assertExists()
            assertEquals(firstVisibleItemIndex, runOnIdle {
                tracker.listStateFor(MyPageRoot).firstVisibleItemIndex
            })
            assertSame(myPageViewModel, tracker.rootFor(MyPageRoot))
            assertTrue(detailViewModel.cleared, "Popping the detail entry must clear its ViewModel")
            assertEquals(1, tracker.clearedDetailCount)
        }
    }

    @Test
    fun sameSearchKeyInDifferentRootsKeepsEntryStateIsolatedAndClearsOnlyThePoppedRoot() {
        val tracker = TestViewModelTracker()
        val factory = TestViewModelFactory(tracker)
        val hostOwner = TestHostViewModelStoreOwner()
        var navigationState: AppNavigationState? = null

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides hostOwner) {
                    AppNavigationRuntime(
                        onNavigationStateAvailable = { navigationState = it },
                        entryContent = { destination, onSearch, onPush, onBack ->
                            TestNavigationEntry(
                                destination = destination,
                                onSearch = onSearch,
                                onPush = onPush,
                                onBack = onBack,
                                factory = factory,
                            )
                        },
                    )
                }
            }

            onNodeWithText("push-search").performClick()
            onNodeWithText("search-view-model:1").assertExists()
            onNodeWithText("increment-search-counter").performClick()
            onNodeWithText("search-saveable-counter:1").assertExists()
            val myPageSearchViewModel = tracker.searchFor(1)

            runOnIdle { requireNotNull(navigationState).selectRoot(NewsRoot) }
            onNodeWithText("root:News").assertExists()
            onNodeWithText("push-search").performClick()
            onNodeWithText("search-view-model:2").assertExists()
            onNodeWithText("search-saveable-counter:0").assertExists()
            onNodeWithText("increment-search-counter").performClick()
            onNodeWithText("search-saveable-counter:1").assertExists()
            val newsSearchViewModel = tracker.searchFor(2)

            runOnIdle { requireNotNull(navigationState).selectRoot(MyPageRoot) }
            onNodeWithText("search-view-model:1").assertExists()
            onNodeWithText("search-saveable-counter:1").assertExists()
            assertSame(myPageSearchViewModel, tracker.searchFor(1))
            assertFalse(myPageSearchViewModel.cleared)
            assertFalse(newsSearchViewModel.cleared)

            onNodeWithText("pop-search").performClick()
            waitForIdle()
            onNodeWithText("root:My Page").assertExists()
            assertTrue(myPageSearchViewModel.cleared)
            assertFalse(newsSearchViewModel.cleared)
            assertEquals(1, tracker.clearedSearchCount)

            runOnIdle { requireNotNull(navigationState).selectRoot(NewsRoot) }
            onNodeWithText("search-view-model:2").assertExists()
            onNodeWithText("search-saveable-counter:1").assertExists()
            assertSame(newsSearchViewModel, tracker.searchFor(2))

            onNodeWithText("pop-search").performClick()
            waitForIdle()
            onNodeWithText("root:News").assertExists()
            assertTrue(newsSearchViewModel.cleared)
            assertEquals(2, tracker.clearedSearchCount)
        }
    }

    @Test
    fun duplicateSearchEntriesKeepIndependentStateAndOnlyClearThePoppedTopEntry() {
        val tracker = TestViewModelTracker()
        val factory = TestViewModelFactory(tracker)
        val hostOwner = TestHostViewModelStoreOwner()

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides hostOwner) {
                    AppNavigationRuntime(
                        entryContent = { destination, onSearch, onPush, onBack ->
                            TestNavigationEntry(
                                destination = destination,
                                onSearch = onSearch,
                                onPush = onPush,
                                onBack = onBack,
                                factory = factory,
                            )
                        },
                    )
                }
            }

            onNodeWithText("push-search").performClick()
            onNodeWithText("search-view-model:1").assertExists()
            onNodeWithText("increment-search-counter").performClick()
            onNodeWithText("search-saveable-counter:1").assertExists()
            val lowerSearchViewModel = tracker.searchFor(1)

            onNodeWithText("push-search-again").performClick()
            onNodeWithText("search-view-model:2").assertExists()
            onNodeWithText("search-saveable-counter:0").assertExists()
            onNodeWithText("increment-search-counter").performClick()
            onNodeWithText("search-saveable-counter:1").assertExists()
            val topSearchViewModel = tracker.searchFor(2)

            onNodeWithText("pop-search").performClick()
            waitForIdle()

            onNodeWithText("search-view-model:1").assertExists()
            onNodeWithText("search-saveable-counter:1").assertExists()
            assertSame(lowerSearchViewModel, tracker.searchFor(1))
            assertFalse(lowerSearchViewModel.cleared)
            assertTrue(topSearchViewModel.cleared)
            assertEquals(1, tracker.clearedSearchCount)
        }
    }
}

@Composable
private fun TestNavigationEntry(
    destination: AppNavKey,
    onSearch: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
    factory: TestViewModelFactory,
) {
    when (destination) {
        is RootNavKey -> TestRootEntry(destination, onSearch, onPush, factory)
        Search -> TestSearchEntry(onPush, onBack, factory)
        is MatchDetail -> TestDetailEntry(destination, onBack, factory)
        else -> error("Unexpected navigation fixture destination: $destination")
    }
}

@Composable
private fun TestRootEntry(
    root: RootNavKey,
    onSearch: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    factory: TestViewModelFactory,
) {
    val owner = requireNotNull(LocalViewModelStoreOwner.current)
    val viewModel = remember(owner) {
        ViewModelProvider.create(owner, factory)[TestRootViewModel::class]
    }
    var counter by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    SideEffect {
        factory.tracker.recordRoot(root, viewModel)
        factory.tracker.recordListState(root, listState)
    }

    Column {
        Text("root:${root.destinationDescriptor.title}")
        Text("loaded-page:${viewModel.loadedPage}")
        Text("selected-tab:${viewModel.selectedTab}")
        Text("saveable-counter:$counter")
        Text("first-visible-item:${listState.firstVisibleItemIndex}")
        Button(onClick = viewModel::loadPage) { Text("set-loaded-page") }
        Button(onClick = viewModel::selectResultsTab) { Text("set-selected-tab") }
        Button(onClick = { counter += 1 }) { Text("increment-counter") }
        Button(onClick = onSearch) { Text("push-search") }
        Button(onClick = { onPush(MatchDetail(matchId = "fixture-detail")) }) {
            Text("push-detail")
        }
        LazyColumn(
            modifier = Modifier.height(96.dp),
            state = listState,
        ) {
            items(30) { index -> Text("item:$index") }
        }
    }
}

@Composable
private fun TestSearchEntry(
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
    factory: TestViewModelFactory,
) {
    val owner = requireNotNull(LocalViewModelStoreOwner.current)
    val viewModel = remember(owner) {
        ViewModelProvider.create(owner, factory)[TestSearchViewModel::class]
    }
    var counter by rememberSaveable { mutableIntStateOf(0) }

    Column {
        Text("search-view-model:${viewModel.id}")
        Text("search-saveable-counter:$counter")
        Button(onClick = { counter += 1 }) { Text("increment-search-counter") }
        Button(onClick = { onPush(Search) }) { Text("push-search-again") }
        Button(onClick = onBack) { Text("pop-search") }
    }
}

@Composable
private fun TestDetailEntry(
    destination: MatchDetail,
    onBack: () -> Unit,
    factory: TestViewModelFactory,
) {
    val owner = requireNotNull(LocalViewModelStoreOwner.current)
    remember(owner) {
        ViewModelProvider.create(owner, factory)[TestDetailViewModel::class]
    }

    Column {
        Text("detail:${destination.matchId}")
        Button(onClick = onBack) { Text("pop-detail") }
    }
}

private class TestRootViewModel : ViewModel() {
    var loadedPage by mutableStateOf("initial")
        private set
    var selectedTab by mutableStateOf("overview")
        private set

    fun loadPage() {
        loadedPage = "loaded"
    }

    fun selectResultsTab() {
        selectedTab = "results"
    }
}

private class TestDetailViewModel(
    private val tracker: TestViewModelTracker,
) : ViewModel() {
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
        tracker.clearedDetailCount += 1
    }
}

private class TestSearchViewModel(
    private val tracker: TestViewModelTracker,
) : ViewModel() {
    val id = tracker.nextSearchId()
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
        tracker.clearedSearchCount += 1
    }
}

private class TestViewModelFactory(
    val tracker: TestViewModelTracker,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras,
    ): T = when (modelClass) {
        TestRootViewModel::class -> TestRootViewModel() as T

        TestDetailViewModel::class -> TestDetailViewModel(tracker).also {
            tracker.detailViewModel = it
        } as T

        TestSearchViewModel::class -> TestSearchViewModel(tracker).also(tracker::recordSearch) as T

        else -> error("Unknown test ViewModel: $modelClass")
    }
}

private class TestViewModelTracker {
    private val rootViewModels = mutableMapOf<RootNavKey, TestRootViewModel>()
    private val listStates = mutableMapOf<RootNavKey, LazyListState>()
    lateinit var detailViewModel: TestDetailViewModel
    var clearedDetailCount = 0
    private val searchViewModels = mutableMapOf<Int, TestSearchViewModel>()
    private var searchId = 0
    var clearedSearchCount = 0

    fun rootFor(root: RootNavKey): TestRootViewModel = rootViewModels.getValue(root)

    fun recordRoot(root: RootNavKey, viewModel: TestRootViewModel) {
        rootViewModels[root] = viewModel
    }

    fun listStateFor(root: RootNavKey): LazyListState = listStates.getValue(root)

    fun recordListState(root: RootNavKey, listState: LazyListState) {
        listStates[root] = listState
    }

    fun nextSearchId(): Int = ++searchId

    fun recordSearch(viewModel: TestSearchViewModel) {
        searchViewModels[viewModel.id] = viewModel
    }

    fun searchFor(id: Int): TestSearchViewModel = searchViewModels.getValue(id)
}

private class TestHostViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
