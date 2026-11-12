package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_chevron_right
import vlrggmobile.app.shared.generated.resources.ic_star_fill

/** Trailing slot of an [IdentityRow]: 48dp wide, or absent for rows that do not navigate. */
sealed interface IdentityRowTrailing {
    /** Decorative `chevron_right`; the whole row opens the detail. */
    data object Chevron : IdentityRowTrailing

    /** No trailing slot (staff rows). */
    data object None : IdentityRowTrailing

    /** Filled `accent` star that removes the favorite, separate from the row target. */
    data class FavoriteRemove(
        val contentDescription: String,
        val onClick: () -> Unit,
        val testTag: String? = null,
    ) : IdentityRowTrailing
}

/**
 * Team, player or event row: 40dp logo slot, `body` 700 title, 13 `muted` meta and a 48dp
 * trailing slot (`DESIGN.md` 8.2). [modifier] lands on the row target, so a test tag finds the
 * clickable node. [badge] is an optional status chip before the meta, for a list that mixes
 * statuses under one title (series `진행 중` among upcoming events).
 */
@Composable
fun IdentityRow(
    title: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    badge: (@Composable () -> Unit)? = null,
    trailing: IdentityRowTrailing = if (onClick != null) IdentityRowTrailing.Chevron else IdentityRowTrailing.None,
) {
    val interaction = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .background(VlrTheme.colors.surface)
            .rowDivider(VlrTheme.colors.line)
            .then(interaction)
            .padding(
                start = VlrDimensions.Space4,
                end = if (trailing == IdentityRowTrailing.None) VlrDimensions.Space4 else VlrDimensions.Space1,
                top = 10.dp,
                bottom = 10.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = VlrTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = VlrTheme.colors.ink,
            )
            val visibleMeta = meta?.takeIf(String::isNotBlank)
            if (badge != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    badge()
                    visibleMeta?.let {
                        Text(text = it, style = VlrTheme.typography.meta, color = VlrTheme.colors.muted)
                    }
                }
            } else {
                visibleMeta?.let {
                    Text(text = it, style = VlrTheme.typography.meta, color = VlrTheme.colors.muted)
                }
            }
        }
        when (trailing) {
            IdentityRowTrailing.Chevron -> Box(
                modifier = Modifier.size(VlrDimensions.MinimumTouchTarget),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = VlrTheme.colors.muted,
                )
            }

            IdentityRowTrailing.None -> Unit

            is IdentityRowTrailing.FavoriteRemove -> VlrIconButton(
                contentDescription = trailing.contentDescription,
                onClick = trailing.onClick,
                tint = VlrTheme.colors.accent,
                modifier = trailing.testTag?.let(Modifier::testTag) ?: Modifier,
                icon = { Icon(vectorResource(Res.drawable.ic_star_fill), contentDescription = null) },
            )
        }
    }
}

/** 1dp bottom line drawn inside the row, so the row stays a single semantics node. */
internal fun Modifier.rowDivider(color: Color): Modifier = drawBehind {
    val y = size.height - VlrDimensions.OutlineWidth.toPx() / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = VlrDimensions.OutlineWidth.toPx())
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun IdentityRowPreview() {
    VlrTheme {
        Column {
            IdentityRow(
                title = "KIWOOM DRX",
                meta = "DRX · 대한민국",
                leading = { LogoSlot(imageUrl = null, kind = LogoSlotKind.Team, placeholderText = "DRX") },
                onClick = {},
            )
            IdentityRow(
                title = "termi",
                meta = "Head Coach",
                leading = { LogoSlot(imageUrl = null, kind = LogoSlotKind.Player, placeholderText = "TE") },
            )
            IdentityRow(
                title = "Rb",
                meta = "Goo Sang-min · 대한민국",
                leading = { LogoSlot(imageUrl = null, kind = LogoSlotKind.Player, placeholderText = "RB") },
                onClick = {},
                trailing = IdentityRowTrailing.FavoriteRemove("Rb 즐겨찾기 해제", onClick = {}),
            )
        }
    }
}
