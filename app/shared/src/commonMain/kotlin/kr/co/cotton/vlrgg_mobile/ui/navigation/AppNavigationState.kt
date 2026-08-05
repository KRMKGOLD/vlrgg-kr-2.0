package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Applies the app's root-plus-transient-overlay navigation policy to one Navigation 3 back stack.
 *
 * The stack is owned by Navigation 3 so that it can be saved and restored. This class only derives
 * state and mutates that same stack; it does not retain a second copy of the navigation state.
 */
class AppNavigationState(
    private val backStack: MutableList<NavKey>,
) {
    init {
        require(backStack.isNotEmpty()) {
            "The navigation back stack must always contain a root destination."
        }
        require(backStack.first() is RootNavKey) {
            "The first navigation destination must be a root destination."
        }
        require(backStack.drop(1).none { it is RootNavKey }) {
            "The navigation overlay cannot contain a root destination."
        }
        require(backStack.all { it is AppNavKey }) {
            "The navigation back stack must only contain app destinations."
        }
    }

    val selectedRoot: RootNavKey
        get() = backStack.first() as RootNavKey

    val overlay: List<AppNavKey>
        get() = backStack.drop(1).map { it as AppNavKey }

    fun push(destination: AppNavKey) {
        require(destination !is RootNavKey) {
            "Root destinations must be selected with selectRoot()."
        }
        backStack += destination
    }

    fun popOverlay(): Boolean {
        if (backStack.size == 1) return false

        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun selectRoot(root: RootNavKey) {
        backStack.clear()
        backStack += root
    }
}
