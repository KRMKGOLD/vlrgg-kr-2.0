package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

/** One loading placeholder bar. Size it to the final content it stands for. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    color: Color = VlrTheme.colors.skeleton,
) {
    Box(modifier = modifier.background(color, RoundedCornerShape(VlrDimensions.CornerRadius)))
}

/** Placeholder for a text list row: title, short second line, meta. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(VlrDimensions.Space4),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
            SkeletonBlock(Modifier.size(width = 180.dp, height = 14.dp))
            SkeletonBlock(Modifier.size(width = 120.dp, height = 10.dp))
        }
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.line)
    }
}

/** Placeholder for a match row (`.pen` `Skeleton match row`): 72dp time column, divider, two teams and context. */
@Composable
fun SkeletonMatchRow(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = VlrDimensions.Space4, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        ) {
            Column(
                modifier = Modifier.width(72.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonBlock(Modifier.size(width = 52.dp, height = 12.dp))
                SkeletonBlock(Modifier.size(width = 40.dp, height = 10.dp))
            }
            VerticalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.line)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(Modifier.size(width = 140.dp, height = 14.dp))
                SkeletonBlock(Modifier.size(width = 112.dp, height = 14.dp))
                SkeletonBlock(Modifier.size(width = 170.dp, height = 10.dp))
            }
        }
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.line)
    }
}

/** Placeholder for the dark detail hero: logo slot, name and meta on `panel`. */
@Composable
fun SkeletonHero(modifier: Modifier = Modifier) {
    val block = VlrTheme.colors.panel2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.panel)
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = VlrDimensions.Space2,
                bottom = VlrDimensions.Space6,
            )
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(72.dp), color = block)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(Modifier.size(width = 160.dp, height = 22.dp), color = block)
            SkeletonBlock(Modifier.size(width = 96.dp, height = 12.dp), color = block)
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SkeletonPreview() {
    VlrTheme {
        Column {
            SkeletonHero()
            SkeletonRow()
            SkeletonMatchRow()
        }
    }
}
