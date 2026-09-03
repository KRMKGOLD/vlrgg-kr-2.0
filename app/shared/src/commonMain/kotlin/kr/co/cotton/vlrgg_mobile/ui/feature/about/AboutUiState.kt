package kr.co.cotton.vlrgg_mobile.ui.feature.about

const val ABOUT_SOURCE_URL = "https://github.com/KRMKGOLD/vlrgg-kr-2.0"

internal data class AboutUiState(
    val versionLabel: String?,
    val feedback: AboutFeedback? = null,
    val sourceLinkErrorId: Long = 0L,
)

internal enum class AboutFeedback {
    SourceLinkError,
}

internal fun aboutUiState(buildVersion: String?): AboutUiState = AboutUiState(
    versionLabel = buildVersion
        ?.takeIf(String::isNotBlank)
        ?.let { "v$it" }
)

internal fun AboutUiState.afterSourceOpen(opened: Boolean): AboutUiState =
    if (opened) {
        copy(feedback = null)
    } else {
        copy(
            feedback = AboutFeedback.SourceLinkError,
            sourceLinkErrorId = sourceLinkErrorId + 1,
        )
    }

internal fun AboutUiState.dismissFeedback(): AboutUiState = copy(feedback = null)
