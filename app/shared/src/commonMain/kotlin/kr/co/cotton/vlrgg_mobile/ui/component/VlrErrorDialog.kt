package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.theme.withKoreanWordBreak

internal const val VLR_DIALOG_CONFIRM_TAG = "vlr-dialog-confirm"
internal const val VLR_DIALOG_DISMISS_TAG = "vlr-dialog-dismiss"

/**
 * Modal error (`DESIGN.md` 8.5): title, body and right-aligned text actions.
 * The platform dialog traps focus and restores it when the dialog closes.
 */
@Composable
fun VlrErrorDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onDismissRequest: () -> Unit,
    confirmEnabled: Boolean = true,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        VlrErrorDialogSurface(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            dismissLabel = dismissLabel,
            onDismiss = onDismiss,
            confirmEnabled = confirmEnabled,
            modifier = Modifier.padding(vertical = VlrDimensions.Space6),
        )
    }
}

/**
 * Dialog body. The action row is measured first so it always keeps its 48dp targets; in a short
 * window with a large font only the title and message scroll.
 */
@Composable
internal fun VlrErrorDialogSurface(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VlrDimensions.OverlayCornerRadius)
    Column(
        modifier = modifier
            .width(296.dp)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 32.dp,
                    color = VlrTheme.colors.scrim.copy(alpha = 0.25f),
                    offset = DpOffset(0.dp, 8.dp),
                ),
            )
            .background(VlrTheme.colors.surface, shape)
            .semantics { paneTitle = title }
            .padding(
                start = VlrDimensions.Space6,
                end = VlrDimensions.Space6,
                top = VlrDimensions.Space6,
                bottom = VlrDimensions.Space3,
            ),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = VlrTheme.typography.section.copy(fontSize = 18.sp, lineHeight = 25.sp).withKoreanWordBreak(),
                color = VlrTheme.colors.ink,
            )
            Text(
                text = message,
                style = VlrTheme.typography.bodySm.copy(lineHeight = 21.sp).withKoreanWordBreak(),
                color = VlrTheme.colors.ink2,
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space1, Alignment.End),
        ) {
            VlrButton(
                text = dismissLabel,
                onClick = onDismiss,
                variant = VlrButtonVariant.Text,
                contentColor = VlrTheme.colors.ink2,
                modifier = Modifier.testTag(VLR_DIALOG_DISMISS_TAG),
            )
            VlrButton(
                text = confirmLabel,
                onClick = onConfirm,
                variant = VlrButtonVariant.Text,
                enabled = confirmEnabled,
                modifier = Modifier.testTag(VLR_DIALOG_CONFIRM_TAG),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VlrErrorDialogPreview() {
    VlrTheme {
        VlrErrorDialogSurface(
            title = "정보를 불러오지 못했습니다",
            message = "네트워크 상태를 확인하고 다시 시도해 주세요.",
            confirmLabel = "재시도",
            onConfirm = {},
            dismissLabel = "뒤로가기",
            onDismiss = {},
            confirmEnabled = true,
        )
    }
}
