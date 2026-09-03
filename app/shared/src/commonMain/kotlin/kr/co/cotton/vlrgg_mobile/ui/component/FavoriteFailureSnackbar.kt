package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun FavoriteFailureSnackbar(
    message: String,
    onRetry: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    VlrSnackbar(
        message = message,
        action = VlrSnackbarAction(label = "재시도", onClick = onRetry),
        modifier = modifier,
        snackbarModifier = Modifier.testTag(testTag),
    )
}
