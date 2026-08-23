package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
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
    AppNavigationRuntime(
        modifier = modifier,
        entryContent = { destination, onSearch, onPush, onBack ->
            NavigationEntryContent(
                destination = destination,
                onSearch = onSearch,
                onPush = onPush,
                onBack = onBack,
            )
        },
    )
}

/**
 * Owns the app's Navigation 3 root stacks and entry decorators.
 *
 * Destination rendering is supplied separately so every app entry uses this runtime owner.
 */
@Composable
internal fun AppNavigationRuntime(
    modifier: Modifier = Modifier,
    initialSelectedRoot: RootNavKey = MyPageRoot,
    onNavigationStateAvailable: (AppNavigationState) -> Unit = {},
    entryContent: @Composable (
        destination: AppNavKey,
        onSearch: () -> Unit,
        onPush: (AppNavKey) -> Unit,
        onBack: () -> Unit,
    ) -> Unit,
) {
    // Navigation 3 has no multiple-stack holder: every root stack and its decorator graph must
    // stay in composition, even though NavDisplay receives only the selected root's entries.
    val rootBackStacks: Map<RootNavKey, MutableList<NavKey>> = rootNavKeys.associateWith { root ->
        rememberNavBackStack(appNavKeySavedStateConfiguration, root)
    }
    val selectedRootState = rememberSaveable(stateSaver = selectedRootSaver) {
        mutableStateOf(initialSelectedRoot)
    }
    val navigationState = remember(
        selectedRootState,
        *rootBackStacks.values.toTypedArray(),
    ) {
        AppNavigationState(rootBackStacks, selectedRootState)
    }
    val selectedRoot = navigationState.selectedRoot
    SideEffect { onNavigationStateAvailable(navigationState) }

    val push: (AppNavKey) -> Unit = navigationState::push
    val popOverlay: () -> Unit = navigationState::popOverlay
    val entryProvider = appNavigationEntryProvider(
        onSearch = { push(Search) },
        onPush = push,
        onBack = popOverlay,
        entryContent = entryContent,
    )

    // Navigation 3 requires a separate rememberDecoratedNavEntries call and decorator instances
    // per back stack. The content keys include their owner root to keep overlays' saveable and
    // ViewModel state independent even when two roots contain the same destination key.
    val rootEntries: Map<RootNavKey, List<NavEntry<NavKey>>> = rootBackStacks.mapValues { (root, backStack) ->
        rememberRootDecoratedEntries(root, backStack, entryProvider)
    }
    val currentDestination = navigationState.currentBackStack.last() as AppNavKey

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentDestination.destinationDescriptor.showBottomBar) {
                RootNavigationBar(
                    selectedRoot = selectedRoot,
                    onSelectRoot = navigationState::selectRoot,
                )
            }
        },
    ) { contentPadding ->
        NavDisplay(
            entries = rootEntries.getValue(selectedRoot),
            modifier = Modifier.padding(contentPadding),
            onBack = popOverlay,
        )
    }
}

@Composable
private fun rememberRootDecoratedEntries(
    root: RootNavKey,
    backStack: List<NavKey>,
    entryProvider: (RootNavKey, NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> = rememberDecoratedNavEntries(
    backStack = backStack,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = { destination -> entryProvider(root, destination) },
)

private fun appNavigationEntryProvider(
    onSearch: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
    entryContent: @Composable (
        destination: AppNavKey,
        onSearch: () -> Unit,
        onPush: (AppNavKey) -> Unit,
        onBack: () -> Unit,
    ) -> Unit,
): (RootNavKey, NavKey) -> NavEntry<NavKey> {
    val entries = entryProvider {
        entry(NewsRoot) { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry(MatchesRoot) { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry(MyPageRoot) { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry(EventsRoot) { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry(AboutRoot) { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry(Search) { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry<NewsDetail> { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry<MatchDetail> { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry<EventDetail> { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry<TeamDetail> { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry<PlayerDetail> { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
        entry<SeriesDetail> { destination ->
            entryContent(destination, onSearch, onPush, onBack)
        }
    }
    return { root, destination ->
        val appDestination = destination as AppNavKey
        val entry = entries(appDestination)
        NavEntry(
            key = destination,
            contentKey = "$root:$destination",
        ) {
            entry.Content()
        }
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

private val selectedRootSaver = Saver<RootNavKey, String>(
    save = { selectedRoot -> rootNavKeys.indexOf(selectedRoot).toString() },
    restore = { index ->
        rootNavKeys.getOrNull(index.toIntOrNull() ?: -1)
    },
)

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
