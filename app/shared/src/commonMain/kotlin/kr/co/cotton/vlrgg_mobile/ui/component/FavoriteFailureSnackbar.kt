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
import androidx.compose.ui.platform.testTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

@Composable
internal fun FavoriteFailureSnackbar(
    message: String,
    onRetry: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
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
            modifier = Modifier
                .widthIn(max = VlrDimensions.FavoriteSnackbarMaxWidth)
                .fillMaxWidth()
                .testTag(testTag),
            containerColor = VlrTheme.colors.inverseSurface,
            contentColor = VlrTheme.colors.inverseOnSurface,
            actionContentColor = VlrTheme.colors.inversePrimary,
            action = {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .widthIn(min = VlrDimensions.MinimumTouchTarget)
                        .heightIn(min = VlrDimensions.MinimumTouchTarget),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = VlrTheme.colors.inversePrimary,
                    ),
                ) { Text("재시도") }
            },
            content = { Text(message) },
        )
    }
}
