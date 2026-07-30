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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrTypography
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions

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

fun statusChipColors(
    status: StatusChipStatus,
    colors: VlrColors,
): StatusChipColors = when (status) {
    StatusChipStatus.Live -> StatusChipColors(
        container = colors.surfaceSelected,
        content = colors.actionPrimary,
        border = colors.actionPrimary,
    )

    StatusChipStatus.Upcoming -> StatusChipColors(
        container = colors.statusUpcomingContainer,
        content = colors.statusUpcoming,
        border = colors.statusUpcoming,
    )

    StatusChipStatus.Completed -> StatusChipColors(
        container = colors.statusCompletedContainer,
        content = colors.statusCompleted,
        border = colors.statusCompleted,
    )

    StatusChipStatus.Postponed -> StatusChipColors(
        container = colors.statusPostponedContainer,
        content = colors.statusPostponed,
        border = colors.statusPostponed,
    )

    StatusChipStatus.Cancelled -> StatusChipColors(
        container = colors.surfaceSubtle,
        content = colors.statusCancelled,
        border = colors.statusCancelled,
    )

    StatusChipStatus.Partial,
    StatusChipStatus.Stale,
    -> StatusChipColors(
        container = colors.surfaceSubtle,
        content = colors.textSecondary,
        border = colors.textSecondary,
    )

    StatusChipStatus.Unavailable -> StatusChipColors(
        container = colors.surface,
        content = colors.textSecondary,
        border = colors.outline,
        usesDashedBorder = true,
    )
}

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
