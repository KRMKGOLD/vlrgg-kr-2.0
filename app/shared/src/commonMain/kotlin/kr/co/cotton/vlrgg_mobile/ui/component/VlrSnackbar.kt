package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

internal const val VLR_SNACKBAR_ACTION_TAG = "vlr-snackbar-action"

internal data class VlrSnackbarAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * `panel` snackbar (`DESIGN.md` 8.5), 16dp from the screen edges and the bottom navigation.
 * The message is announced as a polite live region; an action is optional.
 */
@Composable
internal fun VlrSnackbar(
    message: String,
    action: VlrSnackbarAction? = null,
    modifier: Modifier = Modifier,
    snackbarModifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VlrDimensions.OverlayCornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(VlrDimensions.Space4),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = snackbarModifier
                .widthIn(max = VlrDimensions.SnackbarMaxWidth)
                .fillMaxWidth()
                .heightIn(min = VlrDimensions.MinimumTouchTarget)
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 16.dp,
                        color = VlrTheme.colors.scrim.copy(alpha = 0.18f),
                        offset = DpOffset(0.dp, 4.dp),
                    ),
                )
                .background(VlrTheme.colors.panel, shape)
                .padding(
                    start = VlrDimensions.Space4,
                    end = if (action == null) VlrDimensions.Space4 else VlrDimensions.Space1,
                ),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = VlrDimensions.Space3)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = VlrTheme.typography.bodySm.copy(lineHeight = 19.6.sp),
                color = VlrTheme.colors.onPanel,
            )
            action?.let {
                VlrButton(
                    text = it.label,
                    onClick = it.onClick,
                    variant = VlrButtonVariant.Text,
                    contentColor = VlrTheme.colors.onPanelAccent,
                    modifier = Modifier.testTag(VLR_SNACKBAR_ACTION_TAG),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VlrSnackbarPreview() {
    VlrTheme {
        VlrSnackbar(
            message = "즐겨찾기 추가에 실패했습니다.",
            action = VlrSnackbarAction(label = "재시도", onClick = {}),
        )
    }
}
