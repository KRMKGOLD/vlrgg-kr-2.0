package kr.co.cotton.vlrgg_mobile.ui.feature.about

const val ABOUT_SOURCE_URL = "https://github.com/KRMKGOLD/vlrgg-kr-2.0"
const val ABOUT_VERSION_UNAVAILABLE = "버전 정보를 사용할 수 없습니다"

internal data class AboutUiState(
    val versionLabel: String,
    val feedback: AboutFeedback? = null,
)

internal enum class AboutFeedback {
    SourceLinkError,
    SourceLinkCopied,
}

internal fun aboutUiState(buildVersion: String?): AboutUiState = AboutUiState(
    versionLabel = buildVersion
        ?.takeIf(String::isNotBlank)
        ?.let { "v$it" }
        ?: ABOUT_VERSION_UNAVAILABLE,
)

internal fun AboutUiState.afterSourceOpen(opened: Boolean): AboutUiState = copy(
    feedback = if (opened) null else AboutFeedback.SourceLinkError,
)

internal fun AboutUiState.afterSourceCopy(): AboutUiState = copy(
    feedback = AboutFeedback.SourceLinkCopied,
)

internal fun AboutUiState.dismissFeedback(): AboutUiState = copy(feedback = null)
