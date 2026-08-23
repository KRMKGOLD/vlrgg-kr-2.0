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
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppNavigationRuntimeUiTest {

    @Test
    fun rootRoundTripRetainsEntryViewModelAndSaveableStateAndPoppingDetailClearsOnlyDetail() {
        val tracker = TestViewModelTracker()
        val factory = TestViewModelFactory(tracker)
        val hostOwner = TestHostViewModelStoreOwner()

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides hostOwner) {
                    AppNavigationRuntime(
                        entryContent = { destination, _, onPush, onBack ->
                            TestNavigationEntry(
                                destination = destination,
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
}

@Composable
private fun TestNavigationEntry(
    destination: AppNavKey,
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
    factory: TestViewModelFactory,
) {
    when (destination) {
        is RootNavKey -> TestRootEntry(destination, onPush, factory)
        is MatchDetail -> TestDetailEntry(destination, onBack, factory)
        else -> error("Unexpected navigation fixture destination: $destination")
    }
}

@Composable
private fun TestRootEntry(
    root: RootNavKey,
    onPush: (AppNavKey) -> Unit,
    factory: TestViewModelFactory,
) {
    val owner = requireNotNull(LocalViewModelStoreOwner.current)
    val viewModel = remember(owner) {
        ViewModelProvider.create(owner, factory)[TestRootViewModel::class]
    }
    factory.tracker.recordRoot(root, viewModel)
    var counter by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    factory.tracker.recordListState(root, listState)

    Column {
        Text("root:${root.destinationDescriptor.title}")
        Text("loaded-page:${viewModel.loadedPage}")
        Text("selected-tab:${viewModel.selectedTab}")
        Text("saveable-counter:$counter")
        Text("first-visible-item:${listState.firstVisibleItemIndex}")
        Button(onClick = viewModel::loadPage) { Text("set-loaded-page") }
        Button(onClick = viewModel::selectResultsTab) { Text("set-selected-tab") }
        Button(onClick = { counter += 1 }) { Text("increment-counter") }
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

        else -> error("Unknown test ViewModel: $modelClass")
    }
}

private class TestViewModelTracker {
    private val rootViewModels = mutableMapOf<RootNavKey, TestRootViewModel>()
    private val listStates = mutableMapOf<RootNavKey, LazyListState>()
    lateinit var detailViewModel: TestDetailViewModel
    var clearedDetailCount = 0

    fun rootFor(root: RootNavKey): TestRootViewModel = rootViewModels.getValue(root)

    fun recordRoot(root: RootNavKey, viewModel: TestRootViewModel) {
        rootViewModels[root] = viewModel
    }

    fun listStateFor(root: RootNavKey): LazyListState = listStates.getValue(root)

    fun recordListState(root: RootNavKey, listState: LazyListState) {
        listStates[root] = listState
    }
}

private class TestHostViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
