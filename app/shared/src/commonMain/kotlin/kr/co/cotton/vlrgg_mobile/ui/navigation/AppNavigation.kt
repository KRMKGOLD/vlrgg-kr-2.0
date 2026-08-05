package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kr.co.cotton.vlrgg_mobile.di.AppGraph

@Composable
fun AppNavigation(
    graph: AppGraph,
    modifier: Modifier = Modifier,
) {
    val navigationState = remember { AppNavigationState() }
    var backStack by remember { mutableStateOf(navigationState.backStack) }

    val push: (AppNavKey) -> Unit = { destination ->
        navigationState.push(destination)
        backStack = navigationState.backStack
    }
    val popOverlay: () -> Unit = {
        if (navigationState.overlay.isNotEmpty()) {
            navigationState.popOverlay()
            backStack = navigationState.backStack
        }
    }
    val selectRoot: (RootNavKey) -> Unit = { root ->
        navigationState.selectRoot(root)
        backStack = navigationState.backStack
    }
    val currentDestination = backStack.last()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentDestination.destinationDescriptor.showBottomBar) {
                RootNavigationBar(
                    selectedRoot = navigationState.selectedRoot,
                    onSelectRoot = selectRoot,
                )
            }
        },
    ) { contentPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(contentPadding),
            onBack = {
                if (navigationState.overlay.isNotEmpty()) {
                    popOverlay()
                }
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry(NewsRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        graph = graph,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(MatchesRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        graph = graph,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(MyPageRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        graph = graph,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(EventsRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        graph = graph,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(AboutRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        graph = graph,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(Search) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        graph = graph,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry<NewsDetail> { destination ->
                    NavigationEntryContent(destination, graph, { push(Search) }, push, popOverlay)
                }
                entry<MatchDetail> { destination ->
                    NavigationEntryContent(destination, graph, { push(Search) }, push, popOverlay)
                }
                entry<EventDetail> { destination ->
                    NavigationEntryContent(destination, graph, { push(Search) }, push, popOverlay)
                }
                entry<TeamDetail> { destination ->
                    NavigationEntryContent(destination, graph, { push(Search) }, push, popOverlay)
                }
                entry<PlayerDetail> { destination ->
                    NavigationEntryContent(destination, graph, { push(Search) }, push, popOverlay)
                }
                entry<SeriesDetail> { destination ->
                    NavigationEntryContent(destination, graph, { push(Search) }, push, popOverlay)
                }
            },
        )
    }
}

@Composable
private fun NavigationEntryContent(
    destination: AppNavKey,
    graph: AppGraph,
    onSearch: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
) {
    val myPageOwner = if (destination === MyPageRoot) {
        checkNotNull(LocalViewModelStoreOwner.current) {
            "MyPageRoot requires a Navigation 3 ViewModelStoreOwner."
        }
    } else {
        null
    }

    NavigationContent(
        destination = destination,
        myPageOwner = myPageOwner,
        viewModelFactory = graph.appViewModelFactory,
        onSearch = onSearch,
        onPush = onPush,
        onBack = onBack,
    )
}

@Composable
private fun RootNavigationBar(
    selectedRoot: RootNavKey,
    onSelectRoot: (RootNavKey) -> Unit,
) {
    NavigationBar {
        rootNavKeys.forEach { root ->
            val descriptor = root.destinationDescriptor
            NavigationBarItem(
                selected = root == selectedRoot,
                onClick = { onSelectRoot(root) },
                icon = { Text((descriptor.rootOrder!! + 1).toString()) },
                label = { Text(descriptor.title) },
            )
        }
    }
}
