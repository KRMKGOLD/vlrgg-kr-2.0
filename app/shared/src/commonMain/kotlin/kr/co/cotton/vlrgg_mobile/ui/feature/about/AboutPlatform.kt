package kr.co.cotton.vlrgg_mobile.ui.feature.about

/** Platform boundary for build metadata and user-initiated external About actions. */
interface AboutPlatform {
    val buildVersion: String?

    fun openUrl(url: String, onResult: (Boolean) -> Unit)

    fun copyText(text: String): Boolean
}
