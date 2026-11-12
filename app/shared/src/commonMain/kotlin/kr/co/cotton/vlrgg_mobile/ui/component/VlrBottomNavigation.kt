package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_info
import vlrggmobile.app.shared.generated.resources.ic_newspaper
import vlrggmobile.app.shared.generated.resources.ic_person
import vlrggmobile.app.shared.generated.resources.ic_scoreboard
import vlrggmobile.app.shared.generated.resources.ic_trophy

/**
 * Root bottom navigation (`DESIGN.md` 8.1): `surface` with a 1dp top `line` and equal-width items.
 * The system navigation inset is added below the items instead of a fixed gesture area.
 */
@Composable
fun VlrBottomNavigation(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    ) {
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            content = content,
        )
    }
}

/**
 * One bottom-navigation item. The label is always visible and never ellipsized. When the one-line
 * label does not fit the item width (large font scale), [wrappedLabel] breaks it at a meaning unit.
 */
@Composable
fun RowScope.VlrBottomNavigationItem(
    label: String,
    icon: DrawableResource,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    wrappedLabel: String? = null,
) {
    val colors = VlrTheme.colors
    val contentColor = if (selected) colors.ink else colors.muted
    val labelStyle = VlrTheme.typography.label.copy(
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
    )

    Column(
        modifier = modifier
            .weight(1f)
            .testTag(testTag)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .padding(bottom = VlrDimensions.Space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 2.dp)
                .background(if (selected) colors.accent else Color.Transparent),
        )
        Icon(
            imageVector = vectorResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
        BottomNavigationLabel(
            label = label,
            wrappedLabel = wrappedLabel,
            style = labelStyle,
            color = contentColor,
        )
    }
}

@Composable
private fun BottomNavigationLabel(
    label: String,
    wrappedLabel: String?,
    style: TextStyle,
    color: Color,
) {
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        contentAlignment = Alignment.TopCenter,
    ) {
        val fitsOneLine = textMeasurer.measure(
            text = label,
            style = style,
            softWrap = false,
            maxLines = 1,
        ).size.width <= constraints.maxWidth
        Text(
            text = if (fitsOneLine || wrappedLabel == null) label else wrappedLabel,
            style = style,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VlrBottomNavigationPreview() {
    VlrTheme {
        VlrBottomNavigation {
            VlrBottomNavigationItem("뉴스", Res.drawable.ic_newspaper, false, {}, "news")
            VlrBottomNavigationItem("경기", Res.drawable.ic_scoreboard, true, {}, "matches")
            VlrBottomNavigationItem("마이페이지", Res.drawable.ic_person, false, {}, "my-page", wrappedLabel = "마이\n페이지")
            VlrBottomNavigationItem("대회", Res.drawable.ic_trophy, false, {}, "events")
            VlrBottomNavigationItem("정보", Res.drawable.ic_info, false, {}, "about")
        }
    }
}
