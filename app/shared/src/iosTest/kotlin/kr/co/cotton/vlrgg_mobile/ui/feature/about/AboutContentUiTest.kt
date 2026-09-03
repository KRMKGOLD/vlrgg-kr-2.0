package kr.co.cotton.vlrgg_mobile.ui.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AboutContentUiTest {
    @Test
    fun populatedContentShowsRequiredSectionsInContractOrderWithoutFeedback() = runComposeUiTest {
        setContent { setAboutContent(aboutUiState("2.0.4")) }

        val appIdentityTop = onNodeWithText("VLR.GG Mobile 2.0").fetchSemanticsNode().boundsInRoot.top
        val sourceTop = onNodeWithText("Source Code").fetchSemanticsNode().boundsInRoot.top
        val themeTop = onNodeWithText("Current Theme · Light").fetchSemanticsNode().boundsInRoot.top
        val attributionTop = onNodeWithText("Data Source: VLR.GG").fetchSemanticsNode().boundsInRoot.top
        assertTrue(appIdentityTop < sourceTop && sourceTop < themeTop && themeTop < attributionTop)
        onNodeWithText("v2.0.4").assertExists()
        onNodeWithText("다크 모드는 추후 업데이트될 예정입니다.").assertExists()
        onNodeWithText("이 앱은 비공식 개인 프로젝트로 운영됩니다.").assertExists()
        onNodeWithText("Feedback").assertDoesNotExist()
    }

    @Test
    fun missingVersionMetadataOmitsOnlyTheVersionChip() = runComposeUiTest {
        setContent { setAboutContent(aboutUiState(null)) }

        onNodeWithText("VLR.GG Mobile 2.0").assertIsDisplayed()
        onNodeWithText("발로란트 e스포츠의 모든 정보와 경기 결과를 가장 빠르고 정확하게 전달합니다.").assertIsDisplayed()
        onNodeWithText("버전 정보를 사용할 수 없습니다").assertDoesNotExist()
        onNodeWithText("Source Code").assertIsDisplayed()
    }

    @Test
    fun sourceOpenFailureIsActionlessAutoDismissesAndAllowsAnotherAttempt() = runComposeUiTest {
        val platform = FakeAboutPlatform()
        val accessibilityManager = RecordingAccessibilityManager(recommendedTimeoutMillis = 100)
        setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides accessibilityManager) {
                VlrTheme {
                    AboutScreen(
                        platform = platform,
                        onSearch = {},
                        viewModel = AboutViewModel(),
                    )
                }
            }
        }

        onNodeWithText("github.com/KRMKGOLD/vlrgg-kr-2.0").assertExists()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Source Code 외부 링크 열기")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(listOf(ABOUT_SOURCE_URL), platform.openedUrls)
        onNodeWithText("VLR.GG Mobile 2.0").assertExists()
        onNodeWithText("소스 코드를 열 수 없습니다.").assertIsDisplayed()
        onNodeWithText("링크 복사").assertDoesNotExist()
        onNodeWithText("재시도").assertDoesNotExist()
        onNodeWithContentDescription("안내 닫기").assertDoesNotExist()
        assertEquals(
            TimeoutRequest(
                originalTimeoutMillis = AboutSourceLinkErrorBaseDurationMillis,
                containsIcons = false,
                containsText = true,
                containsControls = false,
            ),
            accessibilityManager.requests.last(),
        )

        mainClock.advanceTimeBy(accessibilityManager.recommendedTimeoutMillis + 100)
        onNodeWithText("소스 코드를 열 수 없습니다.").assertDoesNotExist()

        onNodeWithContentDescription("Source Code 외부 링크 열기").performClick()
        assertEquals(listOf(ABOUT_SOURCE_URL, ABOUT_SOURCE_URL), platform.openedUrls)
    }

    @Test
    fun sourceOpenSuccessUsesExactRepositoryUrlWithoutFeedback() = runComposeUiTest {
        val platform = FakeAboutPlatform(openResult = true)
        setContent {
            VlrTheme {
                AboutScreen(
                    platform = platform,
                    onSearch = {},
                    viewModel = AboutViewModel(),
                )
            }
        }

        onNodeWithContentDescription("Source Code 외부 링크 열기")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf(ABOUT_SOURCE_URL), platform.openedUrls)
        onNodeWithText("소스 코드를 열 수 없습니다.").assertDoesNotExist()
    }

    @Test
    fun delayedSourceOpenCallbackAfterAboutDisposalIsIgnoredButLaterAttemptsWork() = runComposeUiTest {
        val platform = DelayedAboutPlatform()
        val showAbout = mutableStateOf(true)
        val viewModel = AboutViewModel()
        setContent {
            VlrTheme {
                if (showAbout.value) {
                    AboutScreen(
                        platform = platform,
                        onSearch = {},
                        viewModel = viewModel,
                    )
                }
            }
        }

        onNodeWithContentDescription("Source Code 외부 링크 열기").performClick()
        assertEquals(1, platform.pendingCallbackCount)
        runOnIdle { showAbout.value = false }
        mainClock.advanceTimeByFrame()
        runOnIdle { platform.completeNext(opened = false) }

        runOnIdle { showAbout.value = true }
        mainClock.advanceTimeByFrame()
        onNodeWithText("소스 코드를 열 수 없습니다.").assertDoesNotExist()

        onNodeWithContentDescription("Source Code 외부 링크 열기").performClick()
        assertEquals(1, platform.pendingCallbackCount)
        runOnIdle { platform.completeNext(opened = false) }
        onNodeWithText("소스 코드를 열 수 없습니다.").assertIsDisplayed()
    }

    @Composable
    private fun setAboutContent(
        uiState: AboutUiState,
        onSourceClick: () -> Unit = {},
    ) {
        VlrTheme {
            AboutContent(
                uiState = uiState,
                onSearch = {},
                onSourceClick = onSourceClick,
            )
        }
    }

    private class FakeAboutPlatform(
        private val openResult: Boolean = false,
    ) : AboutPlatform {
        override val buildVersion: String? = "2.0.4"
        val openedUrls = mutableListOf<String>()

        override fun openUrl(url: String, onResult: (Boolean) -> Unit) {
            openedUrls += url
            onResult(openResult)
        }
    }

    private class DelayedAboutPlatform : AboutPlatform {
        override val buildVersion: String? = "2.0.4"
        private val pendingCallbacks = ArrayDeque<(Boolean) -> Unit>()

        val pendingCallbackCount: Int
            get() = pendingCallbacks.size

        override fun openUrl(url: String, onResult: (Boolean) -> Unit) {
            pendingCallbacks.addLast(onResult)
        }

        fun completeNext(opened: Boolean) {
            pendingCallbacks.removeFirst()(opened)
        }
    }

    private class RecordingAccessibilityManager(
        val recommendedTimeoutMillis: Long,
    ) : AccessibilityManager {
        val requests = mutableListOf<TimeoutRequest>()

        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long {
            requests += TimeoutRequest(
                originalTimeoutMillis = originalTimeoutMillis,
                containsIcons = containsIcons,
                containsText = containsText,
                containsControls = containsControls,
            )
            return recommendedTimeoutMillis
        }
    }

    private data class TimeoutRequest(
        val originalTimeoutMillis: Long,
        val containsIcons: Boolean,
        val containsText: Boolean,
        val containsControls: Boolean,
    )
}
