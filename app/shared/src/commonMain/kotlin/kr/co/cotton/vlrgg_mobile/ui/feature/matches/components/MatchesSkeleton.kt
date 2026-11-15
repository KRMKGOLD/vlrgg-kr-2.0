package kr.co.cotton.vlrgg_mobile.ui.feature.matches.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import androidx.compose.foundation.layout.size
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonBlock
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonMatchRow

@Composable
fun MatchesSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { stateDescription = "경기 목록 불러오는 중" },
    ) {
        SkeletonBlock(
            Modifier
                .padding(
                    start = VlrDimensions.Space4,
                    top = VlrDimensions.Space5,
                    bottom = VlrDimensions.Space2,
                )
                .size(width = 96.dp, height = 20.dp),
        )
        repeat(4) {
            SkeletonMatchRow()
        }
    }
}
