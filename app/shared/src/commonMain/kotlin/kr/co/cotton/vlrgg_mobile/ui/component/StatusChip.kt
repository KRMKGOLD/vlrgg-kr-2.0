package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrTypography
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTypeScale
import kotlin.math.pow

/** Status chip variants from `DESIGN.md` 8.4. The label text always carries the status. */
enum class StatusChipStatus {
    /** `LIVE`: Saira `tag` on `accent`. */
    Live,

    /** `진행 중`: system 12 on `accent`. */
    InProgress,

    /** `예정` on a dark `panel` background. */
    Upcoming,

    /** `완료됨` on a dark `panel` background. */
    Completed,

    /** `연기됨`. */
    Postponed,

    /** `취소됨` on a dark `panel` background. */
    Cancelled,

    /** `정보 없음` on a dark `panel` background. */
    Unavailable,

    /** Version label on light surfaces. */
    Version,

    /** v2 player result chips. Removed when the team match row replaces them (stage 2b). */
    Loss,
    Partial,
}

data class StatusChipColors(
    val container: Color,
    val content: Color,
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

fun statusChipColors(
    status: StatusChipStatus,
    colors: VlrColors,
): StatusChipColors = when (status) {
    StatusChipStatus.Live,
    StatusChipStatus.InProgress,
    -> StatusChipColors(container = colors.accent, content = colors.onPanel)

    StatusChipStatus.Upcoming -> StatusChipColors(container = colors.panel2, content = colors.onPanel)

    StatusChipStatus.Completed,
    StatusChipStatus.Cancelled,
    StatusChipStatus.Unavailable,
    -> StatusChipColors(container = colors.panel2, content = colors.onPanelMuted)

    StatusChipStatus.Postponed -> StatusChipColors(container = colors.warnSoft, content = colors.warn)
    StatusChipStatus.Version -> StatusChipColors(container = colors.surfaceSunken, content = colors.ink2)
    StatusChipStatus.Loss -> StatusChipColors(container = colors.accentSoft, content = colors.accentPressed)
    StatusChipStatus.Partial -> StatusChipColors(container = colors.canvas, content = colors.muted)
}

internal fun statusChipTextStyle(status: StatusChipStatus, typography: VlrTypeScale): TextStyle = when (status) {
    StatusChipStatus.Live -> typography.tag.copy(letterSpacing = 0.6.sp)
    StatusChipStatus.Version -> typography.label.copy(fontWeight = FontWeight.SemiBold)
    else -> typography.label.copy(fontWeight = FontWeight.Bold)
}

/** Informational-only status marker. Interactive status filters require a separately designed control. */
@Composable
fun StatusChip(
    status: StatusChipStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = statusChipColors(status, LocalVlrColors.current)
    Text(
        text = label,
        modifier = modifier
            .background(colors.container, RoundedCornerShape(VlrDimensions.CornerRadius))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = colors.content,
        style = statusChipTextStyle(status, LocalVlrTypography.current),
    )
}
