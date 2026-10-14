package kr.co.cotton.vlrgg_mobile.ui.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AboutContentUiTest {
    private companion object {
        const val ABOUT_INTRODUCTION =
            "VLR.GG에 공개된 발로란트 e스포츠 경기·대회·팀·선수 정보를 모바일에서 보기 편하게 정리한 비공식 앱입니다."
    }

    @Test
    fun populatedContentShowsRequiredSectionsInContractOrderWithoutFeedback() = runComposeUiTest {
        setContent { setAboutContent(aboutUiState("2.0.4")) }

        val orderedTops = listOf(
            "VLR.GG Mobile (Unofficial)",
            "소스 코드",
            "개인정보처리방침",
            "문의",
            "현재 테마 · 라이트",
            "데이터 출처: VLR.GG",
            "비공식 앱 고지",
            "상표 고지",
        ).map { text -> onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(orderedTops.sorted(), orderedTops)
        assertEquals(orderedTops.size, orderedTops.toSet().size)
        onNodeWithText("v2.0.4").assertExists()
        onNodeWithText(ABOUT_INTRODUCTION).assertExists()
        onNodeWithText("github.com/KRMKGOLD/vlrgg-kr-2.0").assertExists()
        onNodeWithText("GitHub · docs/privacy-policy.md").assertExists()
        onNodeWithText("GitHub Issues").assertExists()
        onNodeWithText("다크 모드는 추후 업데이트될 예정입니다.").assertExists()
        onNodeWithText("경기·대회·팀·선수 정보는 VLR.GG에 공개된 데이터를 가져와 정리합니다.").assertExists()
        onNodeWithText(ABOUT_UNAFFILIATED_NOTICE).assertExists()
        onNodeWithText(ABOUT_RIOT_TRADEMARK_NOTICE).assertExists()
        onNodeWithText("VLR.GG Mobile 2.0").assertDoesNotExist()
        onNodeWithText("가장 빠르고 정확하게", substring = true).assertDoesNotExist()
        onNodeWithText("Feedback").assertDoesNotExist()
        onNodeWithText("오픈소스 라이선스").assertDoesNotExist()
    }

    @Test
    fun noticesStateNonAffiliationDataSourceAndRiotTrademark() {
        assertTrue("VLR.GG 및 Riot Games와 제휴하지 않았으며" in ABOUT_UNAFFILIATED_NOTICE)
        assertTrue("승인이나 후원을 받지 않았습니다" in ABOUT_UNAFFILIATED_NOTICE)
        assertTrue("Riot Games의 보증을 받지 않았으며" in ABOUT_RIOT_TRADEMARK_NOTICE)
        assertTrue(
            "VALORANT(발로란트) 및 모든 관련 자산은 Riot Games, Inc.의 상표 또는 등록 상표입니다." in
                ABOUT_RIOT_TRADEMARK_NOTICE,
        )
    }

    @Test
    fun everyExternalLinkRowAnnouncesItsDestinationAsAnExternalOpenButton() = runComposeUiTest {
        setContent { setAboutContent(aboutUiState("2.0.4")) }

        listOf(
            "소스 코드 외부 링크 열기",
            "개인정보처리방침 외부 링크 열기",
            "문의 외부 링크 열기",
        ).forEach { label ->
            onNodeWithContentDescription(label)
                .performScrollTo()
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(
                    SemanticsMatcher("onClick label is $label") { node ->
                        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
                    },
                )
        }
    }

    @Test
    fun missingVersionMetadataOmitsOnlyTheVersionChip() = runComposeUiTest {
        setContent { setAboutContent(aboutUiState(null)) }

        onNodeWithText("VLR.GG Mobile (Unofficial)").assertIsDisplayed()
        onNodeWithText(ABOUT_INTRODUCTION).assertIsDisplayed()
        onNodeWithText("버전 정보를 사용할 수 없습니다").assertDoesNotExist()
        onNodeWithText("소스 코드").assertIsDisplayed()
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
        onNodeWithContentDescription("소스 코드 외부 링크 열기")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(listOf(ABOUT_SOURCE_URL), platform.openedUrls)
        onNodeWithText("VLR.GG Mobile (Unofficial)").assertExists()
        onNodeWithText("소스 코드를 열 수 없습니다.").assertIsDisplayed()
        onNodeWithText("링크 복사").assertDoesNotExist()
        onNodeWithText("재시도").assertDoesNotExist()
        onNodeWithContentDescription("안내 닫기").assertDoesNotExist()
        assertEquals(
            TimeoutRequest(
                originalTimeoutMillis = AboutLinkErrorBaseDurationMillis,
                containsIcons = false,
                containsText = true,
                containsControls = false,
            ),
            accessibilityManager.requests.last(),
        )

        mainClock.advanceTimeBy(accessibilityManager.recommendedTimeoutMillis + 100)
        onNodeWithText("소스 코드를 열 수 없습니다.").assertDoesNotExist()

        onNodeWithContentDescription("소스 코드 외부 링크 열기").performClick()
        assertEquals(listOf(ABOUT_SOURCE_URL, ABOUT_SOURCE_URL), platform.openedUrls)
    }

    @Test
    fun eachLinkOpenFailureShowsOnlyItsOwnActionlessSnackbarAndKeepsTheRow() = runComposeUiTest {
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

        val cases = listOf(
            Triple("개인정보처리방침 외부 링크 열기", ABOUT_PRIVACY_POLICY_URL, "개인정보처리방침을 열 수 없습니다."),
            Triple("문의 외부 링크 열기", ABOUT_CONTACT_URL, "문의 페이지를 열 수 없습니다."),
        )
        val allMessages = listOf("소스 코드를 열 수 없습니다.") + cases.map { it.third }
        mainClock.autoAdvance = false
        cases.forEachIndexed { index, (label, url, message) ->
            onNodeWithContentDescription(label).performClick()
            mainClock.advanceTimeByFrame()

            assertEquals(url, platform.openedUrls.last())
            onNodeWithText(message).assertIsDisplayed()
            (allMessages - message).forEach { other -> onNodeWithText(other).assertDoesNotExist() }
            onNodeWithText("재시도").assertDoesNotExist()
            onNodeWithContentDescription("안내 닫기").assertDoesNotExist()
            onNodeWithContentDescription(label).assertExists()
            assertEquals(
                TimeoutRequest(
                    originalTimeoutMillis = AboutLinkErrorBaseDurationMillis,
                    containsIcons = false,
                    containsText = true,
                    containsControls = false,
                ),
                accessibilityManager.requests.last(),
            )

            mainClock.advanceTimeBy(accessibilityManager.recommendedTimeoutMillis + 100)
            onNodeWithText(message).assertDoesNotExist()
            assertEquals(index + 1, platform.openedUrls.size)
        }
    }

    @Test
    fun repeatedSourceOpenFailureRestartsTheVisibleSnackbarTimeout() = runComposeUiTest {
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

        mainClock.autoAdvance = false
        val sourceLink = onNodeWithContentDescription("소스 코드 외부 링크 열기")
        sourceLink.performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(50)

        sourceLink.performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(50)
        onNodeWithText("소스 코드를 열 수 없습니다.").assertIsDisplayed()

        mainClock.advanceTimeBy(51)
        onNodeWithText("소스 코드를 열 수 없습니다.").assertDoesNotExist()
        assertEquals(2, accessibilityManager.requests.size)
    }

    @Test
    fun linkOpenSuccessUsesExactDestinationUrlsWithoutFeedback() = runComposeUiTest {
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

        listOf(
            "소스 코드 외부 링크 열기",
            "개인정보처리방침 외부 링크 열기",
            "문의 외부 링크 열기",
        ).forEach { label ->
            onNodeWithContentDescription(label)
                .performScrollTo()
                .assertHeightIsAtLeast(48.dp)
                .performClick()
        }
        assertEquals(listOf(ABOUT_SOURCE_URL, ABOUT_PRIVACY_POLICY_URL, ABOUT_CONTACT_URL), platform.openedUrls)
        onNodeWithText("열 수 없습니다.", substring = true).assertDoesNotExist()
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

        onNodeWithContentDescription("소스 코드 외부 링크 열기").performClick()
        assertEquals(1, platform.pendingCallbackCount)
        runOnIdle { showAbout.value = false }
        mainClock.advanceTimeByFrame()
        runOnIdle { platform.completeNext(opened = false) }

        runOnIdle { showAbout.value = true }
        mainClock.advanceTimeByFrame()
        onNodeWithText("소스 코드를 열 수 없습니다.").assertDoesNotExist()

        onNodeWithContentDescription("소스 코드 외부 링크 열기").performClick()
        assertEquals(1, platform.pendingCallbackCount)
        runOnIdle { platform.completeNext(opened = false) }
        onNodeWithText("소스 코드를 열 수 없습니다.").assertIsDisplayed()
    }

    @Test
    fun supersededLinkCallbackIsIgnoredInFavorOfTheLatestAttempt() = runComposeUiTest {
        val platform = DelayedAboutPlatform()
        setContent {
            VlrTheme {
                AboutScreen(
                    platform = platform,
                    onSearch = {},
                    viewModel = AboutViewModel(),
                )
            }
        }

        onNodeWithContentDescription("개인정보처리방침 외부 링크 열기").performScrollTo().performClick()
        onNodeWithContentDescription("문의 외부 링크 열기").performScrollTo().performClick()
        assertEquals(2, platform.pendingCallbackCount)

        runOnIdle { platform.completeNext(opened = false) }
        onNodeWithText("개인정보처리방침을 열 수 없습니다.").assertDoesNotExist()

        runOnIdle { platform.completeNext(opened = false) }
        onNodeWithText("문의 페이지를 열 수 없습니다.").assertIsDisplayed()
    }

    @Composable
    private fun setAboutContent(
        uiState: AboutUiState,
        onLinkClick: (AboutLink) -> Unit = {},
    ) {
        VlrTheme {
            AboutContent(
                uiState = uiState,
                onSearch = {},
                onLinkClick = onLinkClick,
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
