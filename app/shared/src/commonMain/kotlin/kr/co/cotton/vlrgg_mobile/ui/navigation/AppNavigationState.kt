package kr.co.cotton.vlrgg_mobile.ui.navigation

class AppNavigationState(
    selectedRoot: RootNavKey = MyPageRoot,
    overlay: List<AppNavKey> = emptyList(),
) {
    var selectedRoot: RootNavKey = selectedRoot
        private set

    private val mutableOverlay = overlay.toMutableList().apply {
        require(none { it is RootNavKey }) {
            "The navigation overlay cannot contain a root destination."
        }
    }

    val overlay: List<AppNavKey>
        get() = mutableOverlay.toList()

    val backStack: List<AppNavKey>
        get() = buildList {
            add(selectedRoot)
            addAll(mutableOverlay)
        }

    fun push(destination: AppNavKey) {
        require(destination !is RootNavKey) {
            "Root destinations must be selected with selectRoot()."
        }
        mutableOverlay += destination
    }

    fun popOverlay(): Boolean {
        if (mutableOverlay.isEmpty()) return false

        mutableOverlay.removeAt(mutableOverlay.lastIndex)
        return true
    }

    fun selectRoot(root: RootNavKey) {
        selectedRoot = root
        mutableOverlay.clear()
    }
}
