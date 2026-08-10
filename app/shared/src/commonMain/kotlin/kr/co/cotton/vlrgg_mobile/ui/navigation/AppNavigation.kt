package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_event
import vlrggmobile.app.shared.generated.resources.ic_info
import vlrggmobile.app.shared.generated.resources.ic_match
import vlrggmobile.app.shared.generated.resources.ic_news
import vlrggmobile.app.shared.generated.resources.ic_person

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(
        appNavKeySavedStateConfiguration,
        MyPageRoot,
    )
    val navigationState = remember(backStack) { AppNavigationState(backStack) }

    val push: (AppNavKey) -> Unit = { destination ->
        navigationState.push(destination)
    }
    val popOverlay: () -> Unit = {
        navigationState.popOverlay()
    }
    val selectRoot: (RootNavKey) -> Unit = { root ->
        navigationState.selectRoot(root)
    }
    val currentDestination = backStack.last() as AppNavKey

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
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(MatchesRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(MyPageRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(EventsRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(AboutRoot) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry(Search) { destination ->
                    NavigationEntryContent(
                        destination = destination,
                        onSearch = { push(Search) },
                        onPush = push,
                        onBack = popOverlay,
                    )
                }
                entry<NewsDetail> { destination ->
                    NavigationEntryContent(destination, { push(Search) }, push, popOverlay)
                }
                entry<MatchDetail> { destination ->
                    NavigationEntryContent(destination, { push(Search) }, push, popOverlay)
                }
                entry<EventDetail> { destination ->
                    NavigationEntryContent(destination, { push(Search) }, push, popOverlay)
                }
                entry<TeamDetail> { destination ->
                    NavigationEntryContent(destination, { push(Search) }, push, popOverlay)
                }
                entry<PlayerDetail> { destination ->
                    NavigationEntryContent(destination, { push(Search) }, push, popOverlay)
                }
                entry<SeriesDetail> { destination ->
                    NavigationEntryContent(destination, { push(Search) }, push, popOverlay)
                }
            },
        )
    }
}

@Composable
private fun NavigationEntryContent(
    destination: AppNavKey,
    onSearch: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
) {

    NavigationContent(
        destination = destination,
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
                icon = {
                    val icon = when (root) {
                        EventsRoot -> vectorResource(Res.drawable.ic_event)
                        MatchesRoot -> vectorResource(Res.drawable.ic_match)
                        MyPageRoot -> vectorResource(Res.drawable.ic_person)
                        NewsRoot -> vectorResource(Res.drawable.ic_news)
                        AboutRoot -> vectorResource(Res.drawable.ic_info)
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                    )
                },
                label = { Text(descriptor.title) },
            )
        }
    }
}
