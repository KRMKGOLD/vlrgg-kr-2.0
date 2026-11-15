package kr.co.cotton.vlrgg_mobile.ui.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.component.VLR_SNACKBAR_ACTION_TAG
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v3 About (`.pen` 70): sections, version chip, link rows, failure Snackbar, large fonts and RTL. */
@OptIn(ExperimentalTestApi::class)
class AboutV3UiTest {
    @Test
    fun versionChipReadsBuildVersionWithKoreanPrefix() = runComposeUiTest {
        setContent { Fixture(aboutUiState("0.2.0 (3)")) }

        onNodeWithTag(ABOUT_VERSION_CHIP_TAG).assert(hasText("버전 0.2.0 (3)"))
        onNodeWithText("v0.2.0 (3)").assertDoesNotExist()
        val name = onNodeWithText(ABOUT_APP_NAME).fetchSemanticsNode().boundsInRoot
        val chip = onNodeWithTag(ABOUT_VERSION_CHIP_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(chip.top >= name.bottom - 0.5f, "version chip is not under the app name")
        assertEquals(name.left, chip.left, 0.5f)
    }

    @Test
    fun sectionsUseKoreanHeadingsInPenOrder() = runComposeUiTest {
        setContent { Fixture(aboutUiState("0.2.0 (3)")) }

        val tops = listOf("프로젝트", "화면 테마", "출처와 고지").map { title ->
            onNode(hasText(title) and isHeading(), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun linkRowsAreNamedButtonsWithLeadingIconsAndDistinctTrailingIcons() = runComposeUiTest {
        setContent { Fixture(aboutUiState("0.2.0 (3)")) }

        mapOf(
            "소스 코드 외부 링크 열기" to ABOUT_EXTERNAL_LINK_ICON_TAG,
            "개인정보처리방침 외부 링크 열기" to ABOUT_EXTERNAL_LINK_ICON_TAG,
            "메일 앱에서 문의 메일 쓰기" to ABOUT_EXTERNAL_LINK_ICON_TAG,
            ABOUT_OPEN_SOURCE_LICENSES_LABEL to ABOUT_INTERNAL_LINK_ICON_TAG,
        ).forEach { (label, trailingTag) ->
            onNodeWithContentDescription(label)
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(
                    SemanticsMatcher("onClick label is $label") { node ->
                        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
                    },
                )
            onNode(hasTestTag(trailingTag) and hasAnyAncestor(hasContentDescription(label)), useUnmergedTree = true)
                .assertExists("$label has no $trailingTag")
        }
    }

    @Test
    fun linkFailureUsesTheActionlessV3SnackbarAboveTheContent() = runComposeUiTest {
        setContent { Fixture(aboutUiState("0.2.0 (3)").afterLinkOpen(AboutLink.PrivacyPolicy, opened = false)) }

        onNodeWithText("개인정보처리방침을 열 수 없습니다.", useUnmergedTree = true).assertExists()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        onNodeWithTag(VLR_SNACKBAR_ACTION_TAG).assertDoesNotExist()
        onNodeWithText("재시도").assertDoesNotExist()
        onNodeWithContentDescription("개인정보처리방침 외부 링크 열기").assertExists()
    }

    @Test
    fun largeFontsAndNarrowWidthsKeepEveryTextWhole() {
        val texts = listOf(
            ABOUT_APP_NAME,
            "버전 0.2.0 (3)",
            ABOUT_INTRODUCTION,
            "프로젝트",
            "소스 코드",
            "개인정보처리방침",
            "teamcotton124@gmail.com",
            "오픈소스 라이선스",
            "앱에 포함된 라이브러리",
            "화면 테마",
            "라이트",
            ABOUT_THEME_NOTE,
            "출처와 고지",
            "데이터 출처 · VLR.GG",
            ABOUT_UNAFFILIATED_NOTICE,
            ABOUT_RIOT_TRADEMARK_NOTICE,
            "메일 앱을 열 수 없습니다.",
        )
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 8000f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                            Fixture(aboutUiState("0.2.0 (3)").afterLinkOpen(AboutLink.Contact, opened = false))
                        }
                    }
                    val label = "width=$width fontScale=$scale"
                    texts.forEach { text -> assertWhole(text, label, width) }
                    listOf("소스 코드 외부 링크 열기", ABOUT_OPEN_SOURCE_LICENSES_LABEL).forEach { name ->
                        onNodeWithContentDescription(name).assertHeightIsAtLeast(48.dp)
                    }
                }
            }
        }
    }

    @Test
    fun rtlMirrorsLinkRowsAndSectionTitles() = runSkikoComposeUiTest(size = Size(360f, 3000f)) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                Fixture(aboutUiState("0.2.0 (3)"))
            }
        }

        val section = onNode(hasText("프로젝트") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(360f - 16f, section.right, 1f)
        val row = "소스 코드 외부 링크 열기"
        val title = onNode(hasText("소스 코드") and hasAnyAncestor(hasContentDescription(row)), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val trailing = onNode(
            hasTestTag(ABOUT_EXTERNAL_LINK_ICON_TAG) and hasAnyAncestor(hasContentDescription(row)),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(trailing.right < title.left, "trailing icon is not on the left in RTL")
        assertTrue(title.right < 360f - 16f - 22f, "leading icon is not on the right in RTL")
    }

    private fun ComposeUiTest.assertWhole(text: String, label: String, width: Float) {
        val nodes = onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
        assertTrue(nodes.isNotEmpty(), "$label '$text' is missing")
        nodes.forEach { node ->
            val layout = node.textLayout()
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
            assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
            assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
            assertTrue(node.boundsInRoot.right <= width + 0.5f, "$label '$text' leaves the screen")
        }
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    @Composable
    private fun Fixture(uiState: AboutUiState) {
        VlrTheme {
            AboutContent(
                uiState = uiState,
                onSearch = {},
                onLinkClick = {},
                onOpenSourceLicensesClick = {},
            )
        }
    }
}
