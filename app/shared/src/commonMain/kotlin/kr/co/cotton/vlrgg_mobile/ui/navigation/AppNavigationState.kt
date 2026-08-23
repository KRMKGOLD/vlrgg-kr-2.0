package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey

/**
 * Applies the app's root-plus-transient-overlay policy to independent Navigation 3 back stacks.
 *
 * Navigation 3 owns each supplied stack, including its save/restore lifecycle. This class only
 * selects one of those stacks and applies app navigation transitions to that exact instance.
 */
class AppNavigationState(
    private val rootBackStacks: Map<RootNavKey, MutableList<NavKey>>,
    private val selectedRootState: MutableState<RootNavKey>,
) {
    constructor(
        rootBackStacks: Map<RootNavKey, MutableList<NavKey>>,
        initialSelectedRoot: RootNavKey = MyPageRoot,
    ) : this(
        rootBackStacks = rootBackStacks,
        selectedRootState = mutableStateOf(initialSelectedRoot),
    )

    init {
        require(rootBackStacks.keys == rootNavKeys.toSet()) {
            "Root navigation stacks must contain exactly every app root."
        }
        require(selectedRootState.value in rootBackStacks) {
            "The selected root must have a navigation back stack."
        }
        rootBackStacks.forEach { (root, backStack) ->
            require(backStack.isNotEmpty()) {
                "The $root navigation back stack must always contain its root destination."
            }
            require(backStack.first() == root) {
                "The $root navigation back stack must start with its owning root destination."
            }
            require(backStack.drop(1).none { it is RootNavKey }) {
                "The $root navigation overlay cannot contain a root destination."
            }
            require(backStack.all { it is AppNavKey }) {
                "The $root navigation back stack must only contain app destinations."
            }
        }
    }

    val selectedRoot: RootNavKey
        get() = selectedRootState.value

    val currentBackStack: MutableList<NavKey>
        get() = backStackFor(selectedRoot)

    val overlay: List<AppNavKey>
        get() = currentBackStack.drop(1).map { it as AppNavKey }

    fun backStackFor(root: RootNavKey): MutableList<NavKey> = rootBackStacks.getValue(root)

    fun push(destination: AppNavKey) {
        require(destination !is RootNavKey) {
            "Root destinations must be selected with selectRoot()."
        }
        currentBackStack += destination
    }

    fun popOverlay(): Boolean {
        if (currentBackStack.size == 1) return false

        currentBackStack.removeAt(currentBackStack.lastIndex)
        return true
    }

    /**
     * Re-selecting the current root returns it to its root entry. Switching roots leaves both
     * stacks intact, so an overlay remains attached to the root that initiated it.
     */
    fun selectRoot(root: RootNavKey) {
        if (root == selectedRoot) {
            currentBackStack.subList(1, currentBackStack.size).clear()
        }
        selectedRootState.value = root
    }
}
