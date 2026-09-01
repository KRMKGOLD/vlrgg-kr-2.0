package kr.co.cotton.vlrgg_mobile.ui.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
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
    fun sourceOpenFailureOffersCopyRecoveryAndDismissesStaleFeedback() = runComposeUiTest {
        val platform = FakeAboutPlatform()
        setContent {
            VlrTheme {
                AboutScreen(
                    platform = platform,
                    onSearch = {},
                    viewModel = AboutViewModel(),
                )
            }
        }

        onNodeWithText("github.com/KRMKGOLD/vlrgg-kr-2.0").assertExists()
        onNodeWithContentDescription("Source Code 외부 링크 열기")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf(ABOUT_SOURCE_URL), platform.openedUrls)
        onNodeWithText("VLR.GG Mobile 2.0").assertExists()
        onNodeWithText("소스 코드를 열 수 없습니다.").assertExists()
        onNodeWithText("링크 복사")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf(ABOUT_SOURCE_URL), platform.copiedTexts)
        onNodeWithText("링크를 복사했습니다.").performClick()
        onNodeWithText("링크를 복사했습니다.").assertDoesNotExist()
    }

    @Composable
    private fun setAboutContent(
        uiState: AboutUiState,
        onSourceClick: () -> Unit = {},
        onCopySourceClick: () -> Unit = {},
    ) {
        VlrTheme {
            AboutContent(
                uiState = uiState,
                onSearch = {},
                onSourceClick = onSourceClick,
                onCopySourceClick = onCopySourceClick,
                onDismissFeedback = {},
            )
        }
    }

    private class FakeAboutPlatform : AboutPlatform {
        override val buildVersion: String? = "2.0.4"
        val openedUrls = mutableListOf<String>()
        val copiedTexts = mutableListOf<String>()

        override fun openUrl(url: String, onResult: (Boolean) -> Unit) {
            openedUrls += url
            onResult(false)
        }

        override fun copyText(text: String): Boolean {
            copiedTexts += text
            return true
        }
    }
}
