package kr.co.cotton.vlrgg_mobile.ui.feature.about

const val ABOUT_SOURCE_URL = "https://github.com/KRMKGOLD/vlrgg-kr-2.0"
const val ABOUT_PRIVACY_POLICY_URL = "https://github.com/KRMKGOLD/vlrgg-kr-2.0/blob/main/docs/privacy-policy.md"
const val ABOUT_CONTACT_URL = "https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues"

/** External destinations reachable from About, in display order. */
internal enum class AboutLink(val url: String) {
    SourceCode(ABOUT_SOURCE_URL),
    PrivacyPolicy(ABOUT_PRIVACY_POLICY_URL),
    Contact(ABOUT_CONTACT_URL),
}

internal data class AboutUiState(
    val versionLabel: String?,
    val failedLink: AboutLink? = null,
    val linkErrorId: Long = 0L,
)

internal fun aboutUiState(buildVersion: String?): AboutUiState = AboutUiState(
    versionLabel = buildVersion
        ?.takeIf(String::isNotBlank)
        ?.let { "v$it" }
)

internal fun AboutUiState.afterLinkOpen(link: AboutLink, opened: Boolean): AboutUiState =
    if (opened) {
        copy(failedLink = null)
    } else {
        copy(
            failedLink = link,
            linkErrorId = linkErrorId + 1,
        )
    }

internal fun AboutUiState.dismissFeedback(): AboutUiState = copy(failedLink = null)
