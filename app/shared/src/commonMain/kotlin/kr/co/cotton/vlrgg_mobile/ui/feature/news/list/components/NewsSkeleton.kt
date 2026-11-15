package kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonRow
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
        repeat(6) { SkeletonRow() }
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
