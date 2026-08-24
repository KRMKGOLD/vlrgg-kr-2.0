package kr.co.cotton.vlrgg_mobile.ui.feature.matches.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
        SkeletonDateTitle()
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
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                bottom = VlrDimensions.Space2,
            )
            .border(
                VlrDimensions.OutlineWidth,
                VlrTheme.colors.outline,
                RoundedCornerShape(VlrDimensions.DefaultCornerRadius),
            )
            .background(VlrTheme.colors.surface, RoundedCornerShape(VlrDimensions.DefaultCornerRadius))
            .padding(VlrDimensions.Space3),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(52.dp)
                    .height(16.dp)
                    .background(VlrTheme.colors.skeleton, shape),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(88.dp)
                    .height(16.dp)
                    .background(VlrTheme.colors.skeleton, shape),
            )
        }
        Spacer(Modifier.height(VlrDimensions.Space2))
        SkeletonTeamsRow(shape)
    }
}

@Composable
private fun SkeletonDateTitle() {
    Box(
        modifier = Modifier
            .padding(
                start = VlrDimensions.Space4,
                top = VlrDimensions.Space2,
                bottom = VlrDimensions.Space2,
            )
            .width(48.dp)
            .height(20.dp)
            .background(VlrTheme.colors.skeleton, RoundedCornerShape(VlrDimensions.Space1)),
    )
}

@Composable
private fun SkeletonTeamsRow(shape: RoundedCornerShape) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        SkeletonTeam(shape, Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(20.dp)
                .background(VlrTheme.colors.skeleton, shape),
        )
        SkeletonTeam(shape, Modifier.weight(1f), reversed = true)
    }
}

@Composable
private fun SkeletonTeam(
    shape: RoundedCornerShape,
    modifier: Modifier,
    reversed: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (reversed) Arrangement.End else Arrangement.Start,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (reversed) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(16.dp)
                    .background(VlrTheme.colors.skeleton, shape),
            )
            Spacer(Modifier.width(VlrDimensions.Space2))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(VlrTheme.colors.surfaceSubtle, CircleShape),
        )
        if (!reversed) {
            Spacer(Modifier.width(VlrDimensions.Space2))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(16.dp)
                    .background(VlrTheme.colors.skeleton, shape),
            )
        }
    }
}
