package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_do_not_disturb_on
import vlrggmobile.app.shared.generated.resources.ic_error

internal const val STATE_PANEL_ACTION_TAG = "state-panel-action"
internal const val INLINE_RETRY_ACTION_TAG = "inline-retry-action"

/**
 * Full-area state (`DESIGN.md` 8.5): initial error, normal empty list, or a tab-level state.
 * The action is shown only when retrying is meaningful.
 */
@Composable
fun StatePanel(
    title: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource = Res.drawable.ic_error,
    body: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    actionEnabled: Boolean = true,
    actionTestTag: String = STATE_PANEL_ACTION_TAG,
) {
    BoxWithConstraints(modifier = modifier) {
        // In a bounded area the panel scrolls, so the action stays reachable in short (landscape,
        // large font) areas. Areas shorter than the default composition also use a smaller margin.
        val isBounded = constraints.hasBoundedHeight
        val verticalPadding = if (isBounded && maxHeight < StatePanelCompactHeight) {
            VlrDimensions.Space4
        } else {
            StatePanelVerticalPadding
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isBounded) {
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = verticalPadding, horizontal = VlrDimensions.Space8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StatePanelContent(title, icon, body, actionLabel, onAction, actionEnabled, actionTestTag)
        }
    }
}

private val StatePanelVerticalPadding = 56.dp

/** Below this height the 56dp margins would crowd out the action, so the panel uses 16dp. */
private val StatePanelCompactHeight = 320.dp

@Composable
private fun StatePanelContent(
    title: String,
    icon: DrawableResource,
    body: String?,
    actionLabel: String?,
    onAction: () -> Unit,
    actionEnabled: Boolean,
    actionTestTag: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = vectorResource(icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = VlrTheme.colors.disabled,
        )
        Spacer(Modifier.height(VlrDimensions.Space4))
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            style = VlrTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = VlrTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        body?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth(),
                style = VlrTheme.typography.meta,
                color = VlrTheme.colors.muted,
                textAlign = TextAlign.Center,
            )
        }
        actionLabel?.let { label ->
            Spacer(Modifier.height(VlrDimensions.Space6))
            VlrButton(
                text = label,
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.testTag(actionTestTag),
            )
        }
    }
}

/** Retry row kept at the end of a list or inside a section, below content that already loaded. */
@Composable
fun InlineRetry(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actionTestTag: String = INLINE_RETRY_ACTION_TAG,
    showBottomDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VlrDimensions.MinimumTouchTarget)
                .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space1),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_error),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = VlrTheme.colors.muted,
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = VlrTheme.typography.meta,
                color = VlrTheme.colors.ink2,
            )
            VlrButton(
                text = "재시도",
                onClick = onRetry,
                variant = VlrButtonVariant.Text,
                enabled = enabled,
                modifier = Modifier.testTag(actionTestTag),
            )
        }
        if (showBottomDivider) {
            HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.line)
        }
    }
}

/**
 * Empty-section notice placed under a kept section title. Pass [horizontalPadding] 0 when the
 * section container already insets its content.
 */
@Composable
fun SectionMessage(
    message: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = VlrDimensions.Space4,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 6.dp,
                bottom = VlrDimensions.Space5,
            ),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.ic_do_not_disturb_on),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = VlrTheme.colors.disabled,
        )
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = VlrTheme.typography.meta,
            color = VlrTheme.colors.muted,
        )
    }
}

/** List section title with an optional Saira count and an optional trailing meta label (`DESIGN.md` 8.2). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    meta: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = VlrDimensions.Space5,
                bottom = VlrDimensions.Space2,
            ),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = VlrTheme.typography.section,
            color = VlrTheme.colors.ink,
        )
        count?.let {
            Text(
                text = it,
                style = VlrTheme.typography.tag.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = VlrTheme.colors.muted,
            )
        }
        Spacer(Modifier.weight(1f))
        meta?.let {
            Text(text = it, style = VlrTheme.typography.label, color = VlrTheme.colors.muted)
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FeedbackPreview() {
    VlrTheme {
        Column {
            SectionHeader(title = "예정 경기", count = "3", meta = "한국 시간")
            SectionMessage(message = "예정된 경기가 없습니다")
            InlineRetry(message = "뉴스 소식을 더 불러오지 못했습니다.", onRetry = {})
            StatePanel(
                title = "뉴스를 불러오지 못했습니다.",
                body = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                actionLabel = "재시도",
            )
        }
    }
}
