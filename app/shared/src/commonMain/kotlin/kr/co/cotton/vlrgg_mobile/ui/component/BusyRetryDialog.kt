package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.busy_dialog_close
import vlrggmobile.app.shared.generated.resources.busy_dialog_message
import vlrggmobile.app.shared.generated.resources.busy_dialog_retry
import vlrggmobile.app.shared.generated.resources.busy_dialog_title
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** UI-owned identity, cooldown deadline, and visibility for one remote operation. */
data class BusyRetryState(
    val operationId: String,
    val retryDeadline: TimeMark,
    val isDialogVisible: Boolean = true,
) {
    fun canRetry(): Boolean = retryDeadline.elapsedNow() >= ZERO

    fun remainingDelay(): Duration = (-retryDeadline.elapsedNow()).coerceAtLeast(ZERO)

    fun dismiss(): BusyRetryState = copy(isDialogVisible = false)

    companion object {
        fun create(
            operationId: String,
            retryDelay: Duration,
            timeSource: TimeSource = TimeSource.Monotonic,
        ): BusyRetryState = BusyRetryState(
            operationId = operationId,
            retryDeadline = timeSource.markNow() + retryDelay.coerceAtLeast(ZERO),
        )
    }
}

/** Injectable production clock with an explicit, non-mutating test seam. */
class BusyRetryStateFactory private constructor(
    private val timeSource: TimeSource,
) {
    @Inject
    constructor() : this(TimeSource.Monotonic)

    fun create(operationId: String, retryDelay: Duration): BusyRetryState =
        BusyRetryState.create(operationId, retryDelay, timeSource)

    companion object {
        fun forTest(timeSource: TimeSource): BusyRetryStateFactory = BusyRetryStateFactory(timeSource)
    }
}

@Composable
fun BusyRetryDialog(
    busy: BusyRetryState?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val visibleBusy = busy?.takeIf(BusyRetryState::isDialogVisible) ?: return
    var retryEnabled by remember(visibleBusy) { mutableStateOf(visibleBusy.canRetry()) }

    LaunchedEffect(visibleBusy) {
        val remaining = visibleBusy.remainingDelay()
        if (remaining > ZERO) delay(remaining)
        retryEnabled = visibleBusy.canRetry()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.busy_dialog_title)) },
        text = { Text(stringResource(Res.string.busy_dialog_message)) },
        confirmButton = {
            VlrButton(
                text = stringResource(Res.string.busy_dialog_retry),
                enabled = retryEnabled,
                onClick = onRetry,
            )
        },
        dismissButton = {
            VlrButton(
                text = stringResource(Res.string.busy_dialog_close),
                variant = VlrButtonVariant.Secondary,
                onClick = onDismiss,
            )
        },
    )
}

/** Keeps inline retry controls hidden until a dismissed busy cooldown expires. */
@Composable
fun rememberBusyRetryCooldown(busy: BusyRetryState?): Boolean {
    var isActive by remember(busy) { mutableStateOf(busy?.canRetry() == false) }

    LaunchedEffect(busy) {
        val remaining = busy?.remainingDelay() ?: return@LaunchedEffect
        if (remaining > ZERO) delay(remaining)
        isActive = false
    }

    return isActive
}
