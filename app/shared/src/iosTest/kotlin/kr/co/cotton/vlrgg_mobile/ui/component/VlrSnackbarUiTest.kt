package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.theme.initializeVlrMaterial3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class VlrSnackbarUiTest {
    @Test
    fun actionlessVariantKeepsCanonicalGeometryWithoutAnInteractiveAction() {
        initializeVlrMaterial3()
        runComposeUiTest {
            var density = 1f
            setContent {
                density = LocalDensity.current.density
                Fixture {
                    VlrSnackbar(
                        message = "일시적인 안내입니다.",
                        snackbarModifier = Modifier.testTag(SNACKBAR_TAG),
                    )
                }
            }

            val rootBounds = onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot
            val snackbarBounds = onNodeWithTag(SNACKBAR_TAG)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(snackbarBounds.width <= 328f * density + 1f)
            assertTrue(snackbarBounds.left >= rootBounds.left + 16f * density - 1f)
            assertTrue(snackbarBounds.right <= rootBounds.right - 16f * density + 1f)
            onNodeWithText("일시적인 안내입니다.").assertIsDisplayed()
            onNodeWithText("재시도").assertDoesNotExist()
        }
    }

    @Test
    fun actionVariantInvokesItsCoupledCallbackWithMinimumTouchTarget() {
        initializeVlrMaterial3()
        runComposeUiTest {
            var clicks = 0
            setContent {
                Fixture {
                    VlrSnackbar(
                        message = "다시 시도해 주세요.",
                        action = VlrSnackbarAction("재시도") { clicks += 1 },
                        snackbarModifier = Modifier.testTag(SNACKBAR_TAG),
                    )
                }
            }

            val action = onNodeWithText("재시도")
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            action.performClick()
            assertEquals(1, clicks)
        }
    }

    @Composable
    private fun Fixture(content: @Composable () -> Unit) = VlrTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(ROOT_TAG),
        ) {
            content()
        }
    }

    private companion object {
        const val ROOT_TAG = "vlr-snackbar-root"
        const val SNACKBAR_TAG = "vlr-snackbar"
    }
}
