package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

internal data class VlrSnackbarAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
internal fun VlrSnackbar(
    message: String,
    action: VlrSnackbarAction? = null,
    modifier: Modifier = Modifier,
    snackbarModifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space4,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Snackbar(
            modifier = snackbarModifier
                .widthIn(max = VlrDimensions.SnackbarMaxWidth)
                .fillMaxWidth(),
            containerColor = VlrTheme.colors.inverseSurface,
            contentColor = VlrTheme.colors.inverseOnSurface,
            actionContentColor = VlrTheme.colors.inversePrimary,
            action = action?.let { snackbarAction ->
                {
                    TextButton(
                        onClick = snackbarAction.onClick,
                        modifier = Modifier
                            .widthIn(min = VlrDimensions.MinimumTouchTarget)
                            .heightIn(min = VlrDimensions.MinimumTouchTarget),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = VlrTheme.colors.inversePrimary,
                        ),
                    ) {
                        Text(snackbarAction.label)
                    }
                }
            },
            content = { Text(message) },
        )
    }
}
