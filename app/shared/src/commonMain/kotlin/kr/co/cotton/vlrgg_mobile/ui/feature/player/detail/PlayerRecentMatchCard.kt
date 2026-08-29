package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchTeam
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

internal fun playerRecentMatchCardTag(matchId: String) = playerMatchCardTag(matchId)
internal fun playerMatchScoreTag(matchId: String) = "player-match-score-$matchId"

@Composable
internal fun PlayerRecentMatchCard(
    match: PlayerRecentMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .testTag(playerRecentMatchCardTag(match.id))
            .clip(shape)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
            .semantics { contentDescription = "경기 상세: ${match.eventName}" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(VlrDimensions.Space3),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            verticalAlignment = Alignment.Top,
        ) {
            StatusChip(
                status = match.outcome.chipStatus(),
                label = match.outcome.displayLabel(),
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = match.eventName,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                match.metadataLabel()?.let { metadata ->
                    Text(
                        text = metadata,
                        style = VlrTheme.typography.labelSmall,
                        color = VlrTheme.colors.textSecondary,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamNameAndTag(
                team = match.teamA,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.width(VlrDimensions.Space2))
            Text(
                text = "${match.teamAScore ?: "—"} - ${match.teamBScore ?: "—"}",
                modifier = Modifier.testTag(playerMatchScoreTag(match.id)),
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(VlrDimensions.Space2))
            TeamNameAndTag(
                team = match.teamB,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun TeamNameAndTag(
    team: PlayerRecentMatchTeam,
    modifier: Modifier,
    textAlign: TextAlign,
) {
    val label = team.tag?.takeIf(String::isNotBlank)?.let { "${team.name} ($it)" } ?: team.name
    Text(
        text = label,
        modifier = modifier,
        style = VlrTheme.typography.bodyStrong,
        color = VlrTheme.colors.textPrimary,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun PlayerRecentMatch.metadataLabel(): String? = listOfNotNull(
    eventStage?.takeIf(String::isNotBlank),
    playedOn?.takeIf(String::isNotBlank),
).joinToString(" · ").takeIf(String::isNotEmpty)

private fun PlayerRecentMatchOutcome.chipStatus(): StatusChipStatus = when (this) {
    PlayerRecentMatchOutcome.WIN,
    PlayerRecentMatchOutcome.LOSS,
    -> StatusChipStatus.Completed
    PlayerRecentMatchOutcome.UNKNOWN -> StatusChipStatus.Partial
}

private fun PlayerRecentMatchOutcome.displayLabel(): String = when (this) {
    PlayerRecentMatchOutcome.WIN -> "승리"
    PlayerRecentMatchOutcome.LOSS -> "패배"
    PlayerRecentMatchOutcome.UNKNOWN -> "결과 미정"
}
