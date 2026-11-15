package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

/** Equal-width tab strip with a 1dp `line` underneath (`DESIGN.md` 8.1). Put [VlrTab]s in [content]. */
@Composable
fun VlrTabs(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val lineColor = VlrTheme.colors.line
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .drawBehind {
                val stroke = VlrDimensions.OutlineWidth.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke,
                )
            }
            .selectableGroup(),
        content = content,
    )
}

/** One tab: selected is `ink` 700 with a 2dp `ink` underline, unselected is `muted` 500. */
@Composable
fun RowScope.VlrTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = VlrTheme.colors
    Box(
        modifier = modifier
            .weight(1f)
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .testTag(testTag)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = VlrDimensions.Space2, vertical = VlrDimensions.Space3),
            style = VlrTheme.typography.body.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) colors.ink else colors.muted,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) colors.ink else Color.Transparent),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VlrTabsPreview() {
    VlrTheme {
        VlrTabs {
            VlrTab(label = "경기", selected = true, onClick = {}, testTag = "matches")
            VlrTab(label = "뉴스", selected = false, onClick = {}, testTag = "news")
            VlrTab(label = "통계", selected = false, onClick = {}, testTag = "stats")
        }
    }
}
