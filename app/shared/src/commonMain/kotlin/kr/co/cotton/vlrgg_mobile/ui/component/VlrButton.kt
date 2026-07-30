package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrTypography
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions

enum class VlrButtonVariant {
    Primary,
    Secondary,
    Text,
    Destructive,
}

enum class VlrButtonSize(
    val visualHeight: Dp,
    val horizontalPadding: Dp,
) {
    Standard(visualHeight = 40.dp, horizontalPadding = 16.dp),
    Compact(visualHeight = 32.dp, horizontalPadding = 12.dp),
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
}

/** A labelled action button. Loading replaces its visible content and prevents repeat activation. */
@Composable
fun VlrButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VlrButtonVariant = VlrButtonVariant.Primary,
    size: VlrButtonSize = VlrButtonSize.Standard,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalVlrColors.current
    val typography = LocalVlrTypography.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState().value
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val visual = buttonVisuals(variant, isPressed, colors)
    val shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    val state = VlrButtonState(enabled = enabled, isLoading = isLoading)

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = VlrDimensions.MinimumTouchTarget,
                minHeight = VlrDimensions.MinimumTouchTarget,
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
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FocusOutline(
            isFocused = isFocused,
            shape = shape,
        ) {
            Box(
                modifier = Modifier
                    .height(size.visualHeight)
                    .clip(shape)
                    .background(visual.container)
                    .then(
                        if (visual.border != null) {
                            Modifier.border(VlrDimensions.OutlineWidth, visual.border, shape)
                        } else {
                            Modifier
                        },
                    )
                    .then(if (enabled) Modifier else Modifier.alpha(0.5f))
                    .padding(PaddingValues(horizontal = size.horizontalPadding))
                    .wrapContentWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides visual.content) {
                    Row(
                        modifier = Modifier.alpha(state.contentPresentation.alpha),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leadingIcon?.let {
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { it() }
                            androidx.compose.foundation.layout.Spacer(Modifier.size(VlrDimensions.Space2))
                        }
                        Text(text = text, style = typography.label)
                    }
                    if (state.showsProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = visual.content,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

/** Circular icon-only action. [contentDescription] is required because no text label is visible. */
@Composable
fun VlrIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val colors = LocalVlrColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState().value
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val container = if (isPressed) colors.surfaceSubtle else Color.Transparent
    val state = VlrButtonState(enabled = enabled, isLoading = isLoading)

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = VlrDimensions.MinimumTouchTarget,
                minHeight = VlrDimensions.MinimumTouchTarget,
            )
            .semantics {
                this.contentDescription = contentDescription
                state.stateDescription?.let { stateDescription = it }
                if (!state.isInteractive) disabled()
            }
            .clickable(
                enabled = state.isInteractive,
                role = state.role,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FocusOutline(isFocused = isFocused, shape = CircleShape) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(container)
                    .then(if (enabled) Modifier else Modifier.alpha(0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.actionPrimary) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.actionPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusOutline(
    isFocused: Boolean,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    val colors = LocalVlrColors.current
    Box(
        modifier = Modifier
            .then(
                if (isFocused) {
                    Modifier
                        .border(VlrDimensions.FocusOutlineWidth, colors.focusOutline, shape)
                        .padding(VlrDimensions.FocusOutlineWidth)
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

private data class ButtonVisuals(
    val container: Color,
    val content: Color,
    val border: Color?,
)

private fun buttonVisuals(
    variant: VlrButtonVariant,
    isPressed: Boolean,
    colors: kr.co.cotton.vlrgg_mobile.ui.theme.VlrColors,
): ButtonVisuals = when (variant) {
    VlrButtonVariant.Primary,
    VlrButtonVariant.Destructive,
    -> ButtonVisuals(
        container = if (isPressed) colors.actionPrimaryPressed else colors.actionPrimary,
        content = colors.onActionPrimary,
        border = null,
    )

    VlrButtonVariant.Secondary -> ButtonVisuals(
        container = if (isPressed) colors.surfaceSubtle else colors.surface,
        content = colors.actionPrimary,
        border = colors.outline,
    )

    VlrButtonVariant.Text -> ButtonVisuals(
        container = if (isPressed) colors.surfaceSubtle else Color.Transparent,
        content = colors.actionPrimary,
        border = null,
    )
}
