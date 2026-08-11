package kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

@Composable
fun NewsSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
    ) {
        repeat(6) { index ->
            NewsSkeletonRow(
                titleWidth = if (index % 3 == 2) 0.7f else 0.8f,
            )
            HorizontalDivider(
                thickness = VlrDimensions.OutlineWidth,
                color = VlrTheme.colors.outline,
            )
        }
    }
}

@Composable
private fun NewsSkeletonRow(
    titleWidth: Float,
) {
    val placeholderShape = RoundedCornerShape(VlrDimensions.Space1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space3,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(titleWidth)
                .height(20.dp)
                .background(VlrTheme.colors.skeleton, placeholderShape),
        )
        Spacer(Modifier.height(VlrDimensions.Space1))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(VlrDimensions.Space3)
                .background(VlrTheme.colors.skeleton, placeholderShape),
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
)
@Composable
private fun NewsSkeletonPreview() {
    VlrTheme {
        NewsSkeleton()
    }
}
