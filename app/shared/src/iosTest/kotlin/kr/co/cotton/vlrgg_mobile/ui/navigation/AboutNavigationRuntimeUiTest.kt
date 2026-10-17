package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kr.co.cotton.vlrgg_mobile.ui.feature.about.ABOUT_OPEN_SOURCE_LICENSES_LABEL
import kr.co.cotton.vlrgg_mobile.ui.feature.about.ABOUT_SOURCE_URL
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutPlatform
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses.OPEN_SOURCE_LICENSES_TITLE
import kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses.OpenSourceLicensesScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses.OpenSourceLicensesViewModel
import kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses.readIosLicenseDefinitions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AboutNavigationRuntimeUiTest {
    @Test
    fun aboutIsFifthRootAndDoesNotReplaySourceErrorAfterSearchAndRootRoundTrips() = runComposeUiTest {
        val platform = FailingAboutPlatform()
        val aboutViewModel = AboutViewModel()

        setContent {
            VlrTheme {
                AppNavigationRuntime(
                    initialSelectedRoot = AboutRoot,
                    entryContent = { destination, onSearch, _, onBack ->
                        when (destination) {
                            AboutRoot -> AboutScreen(
                                platform = platform,
                                onSearch = onSearch,
                                onOpenSourceLicensesClick = {},
                                viewModel = aboutViewModel,
                            )
                            Search -> Button(onClick = onBack) { Text("search overlay") }
                            is RootNavKey -> Text("root:${destination.destinationDescriptor.title}")
                            else -> error("Unexpected destination: $destination")
                        }
                    },
                )
            }
        }

        onNodeWithText("VLR.GG Mobile (Unofficial)").assertExists()
        onNodeWithContentDescription("소스 코드 외부 링크 열기").performClick()
        onNodeWithText("소스 코드를 열 수 없습니다.").assertExists()

        onNodeWithContentDescription("검색").performClick()
        onNodeWithText("search overlay").performClick()
        onNodeWithText("소스 코드를 열 수 없습니다.").assertDoesNotExist()

        onNodeWithText("News").performClick()
        onNodeWithText("root:News").assertExists()
        onNodeWithText("About").performClick()
        onNodeWithText("소스 코드를 열 수 없습니다.").assertDoesNotExist()
        assertEquals(listOf(ABOUT_SOURCE_URL), platform.openedUrls)
    }

    @Test
    fun openSourceLicensesIsPushedFromAboutAndBackReturnsToAbout() = runComposeUiTest {
        val platform = FailingAboutPlatform()

        setContent {
            VlrTheme {
                AppNavigationRuntime(
                    initialSelectedRoot = AboutRoot,
                    entryContent = { destination, onSearch, onPush, onBack ->
                        when (destination) {
                            AboutRoot -> AboutScreen(
                                platform = platform,
                                onSearch = onSearch,
                                onOpenSourceLicensesClick = { onPush(OpenSourceLicenses) },
                                viewModel = AboutViewModel(),
                            )
                            OpenSourceLicenses -> OpenSourceLicensesScreen(
                                readLicenseDefinitions = ::readIosLicenseDefinitions,
                                onBack = onBack,
                                viewModel = OpenSourceLicensesViewModel(),
                            )
                            is RootNavKey -> Text("root:${destination.destinationDescriptor.title}")
                            else -> error("Unexpected destination: $destination")
                        }
                    },
                )
            }
        }

        onNodeWithContentDescription(ABOUT_OPEN_SOURCE_LICENSES_LABEL).performScrollTo().performClick()
        onNodeWithText(OPEN_SOURCE_LICENSES_TITLE).assertExists()
        onNodeWithText("About").assertDoesNotExist()
        // Rows are sorted by name, so the AboutLibraries artifacts lead the generated list.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("AboutLibraries Core Library").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("AboutLibraries Core Library").performClick()
        onNodeWithText("TERMS AND CONDITIONS FOR USE", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("OK").performClick()
        onNodeWithText("OK").assertDoesNotExist()

        onNodeWithContentDescription("뒤로 가기").performClick()
        onNodeWithContentDescription("뒤로 가기").assertDoesNotExist()
        onNodeWithContentDescription(ABOUT_OPEN_SOURCE_LICENSES_LABEL).assertExists()
        onNodeWithText("VLR.GG Mobile (Unofficial)").assertExists()
        assertEquals(emptyList(), platform.openedUrls)
    }

    private class FailingAboutPlatform : AboutPlatform {
        override val buildVersion: String? = "2.0.4"
        val openedUrls = mutableListOf<String>()

        override fun openUrl(url: String, onResult: (Boolean) -> Unit) {
            openedUrls += url
            onResult(false)
        }
    }
}
