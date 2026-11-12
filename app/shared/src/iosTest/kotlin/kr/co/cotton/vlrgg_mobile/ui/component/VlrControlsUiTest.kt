package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class VlrControlsUiTest {
    @Test
    fun buttonVariantsAreNamedFortyEightDpTargetsThatClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            VlrTheme {
                androidx.compose.foundation.layout.Column {
                    VlrButtonVariant.entries.forEach { variant ->
                        VlrButton(
                            text = variant.name,
                            onClick = { clicks += 1 },
                            variant = variant,
                            modifier = Modifier.testTag("button-${variant.name}"),
                        )
                    }
                }
            }
        }

        VlrButtonVariant.entries.forEach { variant ->
            onNodeWithTag("button-${variant.name}")
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(variant.name)))
                .performClick()
        }
        assertEquals(VlrButtonVariant.entries.size, clicks)
    }

    @Test
    fun disabledButtonIgnoresClicks() = runComposeUiTest {
        var clicks = 0
        setContent {
            VlrTheme {
                VlrButton(
                    text = "재시도",
                    onClick = { clicks += 1 },
                    enabled = false,
                    modifier = Modifier.testTag(BUTTON_TAG),
                )
            }
        }

        onNodeWithTag(BUTTON_TAG).assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun loadingButtonKeepsWidthExposesBusyStateAndBlocksRepeatClicks() = runComposeUiTest {
        var clicks = 0
        var loading by mutableStateOf(false)
        setContent {
            VlrTheme {
                VlrButton(
                    text = "다시 시도",
                    onClick = { clicks += 1 },
                    isLoading = loading,
                    modifier = Modifier.testTag(BUTTON_TAG),
                )
            }
        }
        val idleWidth = onNodeWithTag(BUTTON_TAG).fetchSemanticsNode().boundsInRoot.width

        loading = true
        onNodeWithTag(BUTTON_TAG)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "로딩 중"))
            .performClick()
        assertEquals(idleWidth, onNodeWithTag(BUTTON_TAG).fetchSemanticsNode().boundsInRoot.width)
        assertEquals(0, clicks)
    }

    @Test
    fun iconButtonHasNameAndFortyEightDpTarget() = runComposeUiTest {
        var clicks = 0
        setContent {
            VlrTheme {
                VlrIconButton(contentDescription = "검색", onClick = { clicks += 1 }, icon = {})
            }
        }

        onNodeWithContentDescription("검색")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun statusChipIsTextOnlyAndNotInteractive() = runComposeUiTest {
        setContent {
            VlrTheme {
                androidx.compose.foundation.layout.Column {
                    StatusChip(status = StatusChipStatus.Live, label = "LIVE")
                    StatusChip(status = StatusChipStatus.InProgress, label = "진행 중")
                    StatusChip(status = StatusChipStatus.Postponed, label = "연기됨")
                }
            }
        }

        listOf("LIVE", "진행 중", "연기됨").forEach { label ->
            onNodeWithText(label).assertIsDisplayed().assertHasNoClickAction()
        }
    }

    @Test
    fun searchFieldShowsClearOnlyWithValueAndSubmitsThroughIme() = runComposeUiTest {
        var query by mutableStateOf("")
        var submitted: String? = null
        setContent {
            VlrTheme {
                VlrSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = "검색어",
                    onSearch = { submitted = it },
                )
            }
        }

        onNodeWithText("팀, 선수, 대회 검색…").assertIsDisplayed()
        onNodeWithTag(VLR_SEARCH_FIELD_CLEAR_TAG).assertDoesNotExist()

        query = "DRX"
        onNodeWithContentDescription("검색어").assertHeightIsAtLeast(48.dp).performImeAction()
        assertEquals("DRX", submitted)

        onNodeWithContentDescription("검색어 지우기")
            .assertIsEnabled()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals("", query)
        onNodeWithTag(VLR_SEARCH_FIELD_CLEAR_TAG).assertDoesNotExist()
    }

    private companion object {
        const val BUTTON_TAG = "vlr-button"
    }
}
