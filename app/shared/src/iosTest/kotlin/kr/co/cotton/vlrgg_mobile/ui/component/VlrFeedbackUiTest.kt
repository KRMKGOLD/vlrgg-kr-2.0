package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class VlrFeedbackUiTest {
    @Test
    fun statePanelShowsCopyAndRetriesThroughStableTag() = runComposeUiTest {
        var retries = 0
        setContent {
            VlrTheme {
                StatePanel(
                    title = "뉴스를 불러오지 못했습니다.",
                    body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                    actionLabel = "재시도",
                    onAction = { retries += 1 },
                )
            }
        }

        onNodeWithText("뉴스를 불러오지 못했습니다.").assertIsDisplayed()
        onNodeWithText("네트워크 상태를 확인하고 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithTag(STATE_PANEL_ACTION_TAG)
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("재시도")))
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun statePanelWithoutActionShowsNoButtonAndDisabledActionIgnoresClicks() = runComposeUiTest {
        var retries = 0
        var showAction by mutableStateOf(false)
        setContent {
            VlrTheme {
                StatePanel(
                    title = "표시할 뉴스가 없어요.",
                    actionLabel = if (showAction) "재시도" else null,
                    actionEnabled = false,
                    onAction = { retries += 1 },
                    actionTestTag = "custom-retry",
                )
            }
        }

        onNodeWithText("표시할 뉴스가 없어요.").assertIsDisplayed()
        onNodeWithTag("custom-retry").assertDoesNotExist()

        showAction = true
        onNodeWithTag("custom-retry").assertIsNotEnabled().performClick()
        assertEquals(0, retries)
    }

    @Test
    fun inlineRetryKeepsMessageAndInvokesRetry() = runComposeUiTest {
        var retries = 0
        setContent {
            VlrTheme {
                InlineRetry(message = "경기를 더 불러오지 못했습니다.", onRetry = { retries += 1 })
            }
        }

        onNodeWithText("경기를 더 불러오지 못했습니다.").assertIsDisplayed()
        onNodeWithTag(INLINE_RETRY_ACTION_TAG)
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun sectionHeaderAndMessageShowTitleCountMetaAndNotice() = runComposeUiTest {
        setContent {
            VlrTheme {
                Column {
                    SectionHeader(title = "예정 경기", count = "3", meta = "한국 시간")
                    SectionMessage(message = "예정된 경기가 없습니다")
                }
            }
        }

        onNodeWithText("예정 경기").assert(isHeading())
        onNodeWithText("3").assertIsDisplayed()
        onNodeWithText("한국 시간").assertIsDisplayed()
        onNodeWithText("예정된 경기가 없습니다").assertIsDisplayed()
    }

    @Test
    fun errorDialogShowsCopyAndRoutesBothActions() = runComposeUiTest {
        var retries = 0
        var backs = 0
        setContent {
            VlrTheme {
                VlrErrorDialog(
                    title = "정보를 불러오지 못했습니다",
                    message = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                    confirmLabel = "재시도",
                    onConfirm = { retries += 1 },
                    dismissLabel = "뒤로가기",
                    onDismiss = { backs += 1 },
                    onDismissRequest = {},
                )
            }
        }

        onNodeWithText("정보를 불러오지 못했습니다").assertIsDisplayed().assert(isHeading())
        onNodeWithText("네트워크 상태를 확인하고 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithTag(VLR_DIALOG_CONFIRM_TAG).assertHeightIsAtLeast(48.dp).performClick()
        onNodeWithTag(VLR_DIALOG_DISMISS_TAG).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun busyRetryDialogDisablesRetryDuringCooldownAndCloses() = runComposeUiTest {
        var dismissals = 0
        setContent {
            VlrTheme {
                BusyRetryDialog(
                    busy = BusyRetryState.create("news:page:1", 60.seconds),
                    onRetry = {},
                    onDismiss = { dismissals += 1 },
                )
            }
        }

        onNodeWithText("잠시 후 다시 시도해 주세요").assertIsDisplayed()
        onNodeWithTag(VLR_DIALOG_CONFIRM_TAG).assertIsNotEnabled()
        onNodeWithTag(VLR_DIALOG_DISMISS_TAG).performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun busyRetryDialogRetriesOnceCooldownHasPassed() = runComposeUiTest {
        var retries = 0
        setContent {
            VlrTheme {
                BusyRetryDialog(
                    busy = BusyRetryState.create("news:page:1", ZERO),
                    onRetry = { retries += 1 },
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag(VLR_DIALOG_CONFIRM_TAG).assertIsEnabled().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun statePanelRetryStaysReachableInShortAreaWithLargeFont() = runComposeUiTest {
        var retries = 0
        setContent {
            ShortLargeFont {
                StatePanel(
                    title = "경기 목록을 불러오지 못했습니다.",
                    body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                    actionLabel = "재시도",
                    onAction = { retries += 1 },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        onNodeWithTag(STATE_PANEL_ACTION_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun statePanelRetryStaysReachableAsFullListItemInShortList() = runComposeUiTest {
        var retries = 0
        setContent {
            ShortLargeFont {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        StatePanel(
                            title = "뉴스를 불러오지 못했습니다.",
                            body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                            actionLabel = "재시도",
                            onAction = { retries += 1 },
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
            }
        }

        onNodeWithTag(STATE_PANEL_ACTION_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun statePanelKeepsDefaultFiftySixDpMarginWhenUnconstrained() = runComposeUiTest {
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            VlrTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StatePanel(title = "표시할 뉴스가 없어요.", modifier = Modifier.testTag(PANEL_TAG))
                }
            }
        }

        val panelTop = onNodeWithTag(PANEL_TAG).fetchSemanticsNode().boundsInRoot.top
        val titleTop = onNodeWithText("표시할 뉴스가 없어요.").fetchSemanticsNode().boundsInRoot.top
        // 56dp margin + 40dp icon + 16dp gap
        assertEquals(112f * density, titleTop - panelTop, absoluteTolerance = 1f)
    }

    @Test
    fun errorDialogKeepsActionsWhenShortWindowAndLargeFontOverflow() = runComposeUiTest {
        var retries = 0
        var backs = 0
        setContent {
            ShortLargeFont {
                VlrErrorDialogSurface(
                    title = "정보를 불러오지 못했습니다",
                    message = "네트워크 상태를 확인하고 다시 시도해 주세요. ".repeat(6),
                    confirmLabel = "재시도",
                    onConfirm = { retries += 1 },
                    dismissLabel = "뒤로가기",
                    onDismiss = { backs += 1 },
                    confirmEnabled = true,
                )
            }
        }

        onNodeWithText("정보를 불러오지 못했습니다").assertExists()
        onNodeWithTag(VLR_DIALOG_DISMISS_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        onNodeWithTag(VLR_DIALOG_CONFIRM_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    /** 200dp-tall landscape-like area with a 2x font scale. */
    @Composable
    private fun ShortLargeFont(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
            VlrTheme {
                Box(Modifier.size(width = 640.dp, height = 200.dp)) { content() }
            }
        }
    }

    private companion object {
        const val PANEL_TAG = "state-panel"
    }
}
