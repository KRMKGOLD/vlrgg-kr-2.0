package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrTypography
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kotlin.math.pow

enum class StatusChipStatus {
    Live,
    Upcoming,
    Completed,
    Postponed,
    Cancelled,
    Partial,
    Stale,
    Unavailable,
}

data class StatusChipColors(
    val container: Color,
    val content: Color,
    val border: Color,
    val usesDashedBorder: Boolean = false,
)

/** Status chips intentionally have no interaction role or click action. */
internal object StatusChipSemantics {
    val role: Role? = null
    const val isInteractive: Boolean = false
}

internal const val MinimumStatusTextContrast = 4.5

/** Returns the WCAG contrast ratio for opaque status-label colors. */
fun contrastRatio(foreground: Color, background: Color): Double {
    val compositedForeground = if (foreground.alpha < 1f) foreground.compositeOver(background) else foreground
    val foregroundLuminance = relativeLuminance(compositedForeground)
    val backgroundLuminance = relativeLuminance(background)
    return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05) /
        (minOf(foregroundLuminance, backgroundLuminance) + 0.05)
}

private fun relativeLuminance(color: Color): Double =
    (0.2126 * color.red.linearized()) +
        (0.7152 * color.green.linearized()) +
        (0.0722 * color.blue.linearized())

private fun Float.linearized(): Double =
    if (this <= 0.04045f) {
        (this / 12.92f).toDouble()
    } else {
        ((this + 0.055f) / 1.055f).toDouble().pow(2.4)
    }

private fun accessibleStatusContent(
    preferred: Color,
    container: Color,
    colors: VlrColors,
): Color = if (contrastRatio(preferred, container) >= MinimumStatusTextContrast) {
    preferred
} else {
    colors.textPrimary
}

fun statusChipColors(
    status: StatusChipStatus,
    colors: VlrColors,
): StatusChipColors = when (status) {
    StatusChipStatus.Live -> statusChipColors(
        container = colors.surfaceSelected,
        preferredContent = colors.actionPrimary,
        border = colors.actionPrimary,
        colors = colors,
    )

    StatusChipStatus.Upcoming -> statusChipColors(
        container = colors.statusUpcomingContainer,
        preferredContent = colors.statusUpcoming,
        border = colors.statusUpcoming,
        colors = colors,
    )

    StatusChipStatus.Completed -> statusChipColors(
        container = colors.statusCompletedContainer,
        preferredContent = colors.statusCompleted,
        border = colors.statusCompleted,
        colors = colors,
    )

    StatusChipStatus.Postponed -> statusChipColors(
        container = colors.statusPostponedContainer,
        preferredContent = colors.statusPostponed,
        border = colors.statusPostponed,
        colors = colors,
    )

    StatusChipStatus.Cancelled -> statusChipColors(
        container = colors.surfaceSubtle,
        preferredContent = colors.statusCancelled,
        border = colors.statusCancelled,
        colors = colors,
    )

    StatusChipStatus.Partial,
    StatusChipStatus.Stale,
    -> statusChipColors(
        container = colors.surfaceSubtle,
        preferredContent = colors.textSecondary,
        border = colors.textSecondary,
        colors = colors,
    )

    StatusChipStatus.Unavailable -> statusChipColors(
        container = colors.surface,
        preferredContent = colors.textSecondary,
        border = colors.outline,
        colors = colors,
        usesDashedBorder = true,
    )
}

private fun statusChipColors(
    container: Color,
    preferredContent: Color,
    border: Color,
    colors: VlrColors,
    usesDashedBorder: Boolean = false,
): StatusChipColors = StatusChipColors(
    container = container,
    content = accessibleStatusContent(preferredContent, container, colors),
    border = border,
    usesDashedBorder = usesDashedBorder,
)

/** Informational-only status marker. Interactive status filters require a separately designed control. */
@Composable
fun StatusChip(
    status: StatusChipStatus,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = statusChipColors(status, LocalVlrColors.current)
    val typography = LocalVlrTypography.current
    val borderModifier = if (colors.usesDashedBorder) {
        Modifier.dashedPillBorder(colors.border)
    } else {
        Modifier.border(VlrDimensions.OutlineWidth, colors.border, CircleShape)
    }

    Row(
        modifier = modifier
            .height(24.dp)
            .background(colors.container, CircleShape)
            .then(borderModifier)
            .padding(horizontal = VlrDimensions.Space3),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            CompositionLocalProvider(LocalContentColor provides colors.content) {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { it() }
            }
        }
        Text(
            text = label,
            color = colors.content,
            style = typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        )
    }
}

private fun Modifier.dashedPillBorder(color: Color): Modifier = drawBehind {
    val strokeWidth = VlrDimensions.OutlineWidth.toPx()
    val inset = strokeWidth / 2f
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius((size.height - strokeWidth) / 2f),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(4.dp.toPx(), 2.dp.toPx()),
            ),
        ),
    )
}
