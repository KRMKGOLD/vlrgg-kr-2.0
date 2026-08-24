package kr.co.cotton.vlrgg_mobile.ui.feature.matches.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.matchCardTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

@Composable
fun MatchCard(
    match: MatchSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VlrDimensions.CardCornerRadius)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(matchCardTag(match.id))
            .clip(shape)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(VlrDimensions.Space3),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            StatusChip(
                status = match.status.toChipStatus(),
                label = match.status.displayLabel(),
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = match.timeLabel,
                    style = VlrTheme.typography.bodyStrong,
                    color = VlrTheme.colors.textPrimary,
                )
                match.relativeTimeLabel?.let { relativeTimeLabel ->
                    Text(
                        text = relativeTimeLabel,
                        style = VlrTheme.typography.labelSmall,
                        color = VlrTheme.colors.textSecondary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = match.homeTeam.name,
                modifier = Modifier.weight(1f),
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
            )
            Text(
                text = match.scoreOrScheduledLabel(),
                style = VlrTheme.typography.sectionTitle,
                color = VlrTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = match.awayTeam.name,
                modifier = Modifier.weight(1f),
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1)) {
            Text(
                text = match.event.name,
                style = VlrTheme.typography.label,
                color = VlrTheme.colors.textPrimary,
            )
            match.event.series?.let { series ->
                Text(
                    text = series,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                )
            }
        }
    }
}

private fun MatchSummary.scoreOrScheduledLabel(): String = when {
    homeScore != null && awayScore != null -> "$homeScore : $awayScore"
    status == MatchStatus.UPCOMING || status == MatchStatus.LIVE || status == MatchStatus.POSTPONED -> "VS"
    else -> "—"
}

private fun MatchStatus.displayLabel(): String = when (this) {
    MatchStatus.UPCOMING -> "예정"
    MatchStatus.LIVE -> "LIVE"
    MatchStatus.COMPLETED -> "종료"
    MatchStatus.POSTPONED -> "연기"
    MatchStatus.CANCELLED -> "취소"
    MatchStatus.UNAVAILABLE -> "정보 없음"
}

private fun MatchStatus.toChipStatus(): StatusChipStatus = when (this) {
    MatchStatus.UPCOMING -> StatusChipStatus.Upcoming
    MatchStatus.LIVE -> StatusChipStatus.Live
    MatchStatus.COMPLETED -> StatusChipStatus.Completed
    MatchStatus.POSTPONED -> StatusChipStatus.Postponed
    MatchStatus.CANCELLED -> StatusChipStatus.Cancelled
    MatchStatus.UNAVAILABLE -> StatusChipStatus.Unavailable
}
