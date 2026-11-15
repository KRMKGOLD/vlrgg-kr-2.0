package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrTypography
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions

/** v3 button kinds (`DESIGN.md` 8.4). Every kind keeps a 48dp visual height. */
enum class VlrButtonVariant(
    val visualHeight: Dp,
    val horizontalPadding: Dp,
) {
    Primary(visualHeight = 48.dp, horizontalPadding = 24.dp),
    Secondary(visualHeight = 48.dp, horizontalPadding = 24.dp),
    Text(visualHeight = 48.dp, horizontalPadding = 12.dp),
}

internal enum class VlrButtonContentPresentation(val alpha: Float) {
    Visible(alpha = 1f),
    InvisibleButMeasured(alpha = 0f),
}

internal data class VlrButtonState(
    val enabled: Boolean,
    val isLoading: Boolean,
) {
    val isInteractive: Boolean = enabled && !isLoading
    val contentPresentation: VlrButtonContentPresentation = if (isLoading) {
        VlrButtonContentPresentation.InvisibleButMeasured
    } else {
        VlrButtonContentPresentation.Visible
    }
    val showsProgress: Boolean = isLoading
    val role: Role = Role.Button
    val stateDescription: String? = if (isLoading) "로딩 중" else null
    val minimumTouchTarget: Dp = VlrDimensions.MinimumTouchTarget

    /** Disabled buttons are dimmed to 50%; a loading button keeps full opacity with its progress. */
    val containerAlpha: Float = if (enabled) 1f else 0.5f
}

/**
 * A labelled action button. Press and focus use Compose's default ripple indication.
 * Loading keeps the label measured so the width does not change, and prevents repeat activation.
 */
@Composable
fun VlrButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VlrButtonVariant = VlrButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentColor: Color? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalVlrColors.current
    val typography = LocalVlrTypography.current
    val visual = buttonVisuals(variant, colors)
    val content = contentColor ?: visual.content
    val labelStyle = when (variant) {
        VlrButtonVariant.Text -> typography.bodySm
        VlrButtonVariant.Primary, VlrButtonVariant.Secondary -> typography.body
    }.copy(fontWeight = FontWeight.Bold)
    val shape = RoundedCornerShape(VlrDimensions.ButtonCornerRadius)
    val state = VlrButtonState(enabled = enabled, isLoading = isLoading)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = state.minimumTouchTarget,
                minHeight = maxOf(state.minimumTouchTarget, variant.visualHeight),
            )
            .alpha(state.containerAlpha)
            .clip(shape)
            .background(visual.container)
            .then(
                if (visual.border != null) {
                    Modifier.border(VlrDimensions.OutlineWidth, visual.border, shape)
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = text
                state.stateDescription?.let { stateDescription = it }
                if (!state.isInteractive) disabled()
            }
            .clickable(
                enabled = state.isInteractive,
                role = state.role,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = variant.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Row(
                modifier = Modifier.alpha(state.contentPresentation.alpha),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.let {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { it() }
                    Spacer(Modifier.size(VlrDimensions.Space2))
                }
                Text(text = text, style = labelStyle, color = content)
            }
            if (state.showsProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = content,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/**
 * Icon-only action with a 48dp target, as used by headers and rows.
 * [contentDescription] is required because no text label is visible.
 */
@Composable
fun VlrIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    tint: Color = LocalVlrColors.current.ink,
) {
    val state = VlrButtonState(enabled = enabled, isLoading = isLoading)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(state.minimumTouchTarget)
            .semantics {
                this.contentDescription = contentDescription
                state.stateDescription?.let { stateDescription = it }
                if (!state.isInteractive) disabled()
            }
            .clickable(
                enabled = state.isInteractive,
                role = state.role,
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = state.minimumTouchTarget / 2),
                onClick = onClick,
            )
            .alpha(state.containerAlpha),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            } else {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            }
        }
    }
}

private data class ButtonVisuals(
    val container: Color,
    val content: Color,
    val border: Color?,
)

private fun buttonVisuals(
    variant: VlrButtonVariant,
    colors: VlrColors,
): ButtonVisuals = when (variant) {
    VlrButtonVariant.Primary -> ButtonVisuals(
        container = colors.accent,
        content = colors.onPanel,
        border = null,
    )

    VlrButtonVariant.Secondary -> ButtonVisuals(
        container = colors.surface,
        content = colors.ink,
        border = colors.lineStrong,
    )

    VlrButtonVariant.Text -> ButtonVisuals(
        container = Color.Transparent,
        content = colors.accent,
        border = null,
    )
}
