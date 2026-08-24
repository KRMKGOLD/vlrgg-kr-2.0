package kr.co.cotton.vlrgg_mobile.ui.feature.matches.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

@Composable
fun MatchesSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { stateDescription = "경기 목록 불러오는 중" },
    ) {
        repeat(4) {
            SkeletonCard()
        }
    }
}

@Composable
private fun SkeletonCard() {
    val shape = RoundedCornerShape(VlrDimensions.Space1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space2)
            .background(VlrTheme.colors.surfaceSubtle, RoundedCornerShape(VlrDimensions.CardCornerRadius))
            .padding(VlrDimensions.Space3),
    ) {
        Row {
            Box(
                Modifier
                    .width(56.dp)
                    .height(24.dp)
                    .background(VlrTheme.colors.skeleton, shape),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(72.dp)
                    .height(16.dp)
                    .background(VlrTheme.colors.skeleton, shape),
            )
        }
        Spacer(Modifier.height(VlrDimensions.Space4))
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(VlrTheme.colors.skeleton, shape),
        )
        Spacer(Modifier.height(VlrDimensions.Space3))
        Box(
            Modifier
                .fillMaxWidth(0.65f)
                .height(16.dp)
                .background(VlrTheme.colors.skeleton, shape),
        )
    }
}
