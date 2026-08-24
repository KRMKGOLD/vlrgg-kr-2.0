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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
    val shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(matchCardTag(match.id))
            .clip(shape)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = match.status.accessibilityLabel() }
            .padding(VlrDimensions.Space3),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
            ) {
                if (match.status == MatchStatus.UPCOMING) {
                    Text(
                        text = match.timeLabel,
                        style = VlrTheme.typography.label,
                        color = VlrTheme.colors.textSecondary,
                    )
                } else {
                    StatusChip(
                        status = match.status.toChipStatus(),
                        label = match.status.displayLabel(),
                    )
                }
                match.relativeTimeLabel?.let { relativeTimeLabel ->
                    Text(
                        text = relativeTimeLabel,
                        style = VlrTheme.typography.labelSmall,
                        color = VlrTheme.colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
            ) {
                Text(
                    text = match.event.name,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                match.event.series?.let { series ->
                    Text(
                        text = series,
                        style = VlrTheme.typography.labelSmall,
                        color = VlrTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasScore = match.homeScore != null && match.awayScore != null
            TeamName(
                name = match.homeTeam.name,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = match.scoreOrScheduledLabel(),
                modifier = Modifier.testTag(matchScoreTag(match.id)),
                style = if (hasScore) VlrTheme.typography.display else VlrTheme.typography.label,
                color = VlrTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            TeamName(
                name = match.awayTeam.name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun TeamName(
    name: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = name,
        modifier = modifier,
        style = VlrTheme.typography.bodyStrong,
        color = VlrTheme.colors.textPrimary,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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

private fun MatchStatus.accessibilityLabel(): String = when (this) {
    MatchStatus.POSTPONED -> "경기가 연기되었습니다"
    MatchStatus.CANCELLED -> "경기가 취소되었습니다"
    MatchStatus.UNAVAILABLE -> "경기 정보가 없습니다"
    else -> statusOrTimeDescription()
}

private fun MatchStatus.statusOrTimeDescription(): String = when (this) {
    MatchStatus.UPCOMING -> "예정 경기"
    MatchStatus.LIVE -> "라이브 경기"
    MatchStatus.COMPLETED -> "종료된 경기"
    MatchStatus.POSTPONED -> "연기된 경기"
    MatchStatus.CANCELLED -> "취소된 경기"
    MatchStatus.UNAVAILABLE -> "경기 정보 없음"
}

internal fun matchScoreTag(matchId: String): String = "match-score-$matchId"
