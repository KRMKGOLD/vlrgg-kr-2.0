package kr.co.cotton.vlrgg_mobile.ui.feature.matches.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonMatchRow
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions

/** First-load placeholder (`.pen` 20-3): five match rows under a 12dp top inset, without a date header. */
@Composable
fun MatchesSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = VlrDimensions.Space3)
            .clearAndSetSemantics { stateDescription = "경기 목록 불러오는 중" },
    ) {
        repeat(5) {
            SkeletonMatchRow()
        }
    }
}
