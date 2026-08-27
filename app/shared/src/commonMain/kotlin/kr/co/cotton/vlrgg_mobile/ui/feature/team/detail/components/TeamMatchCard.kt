package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.teamMatchCardTag
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

internal enum class TeamMatchSection {
    Upcoming,
    Recent,
}

@Composable
internal fun TeamMatchCard(
    match: TeamMatch,
    section: TeamMatchSection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VlrDimensions.CardCornerRadius)
    val fallbackStatus = when (section) {
        TeamMatchSection.Upcoming -> "예정"
        TeamMatchSection.Recent -> "종료"
    }
    val status = when (section) {
        TeamMatchSection.Upcoming -> StatusChipStatus.Upcoming
        TeamMatchSection.Recent -> StatusChipStatus.Completed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(teamMatchCardTag(match.id))
            .clip(shape)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
            .semantics {
                contentDescription = "경기 상세: ${match.teamName} 대 ${match.opponentName}"
            }
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
                status = status,
                label = match.statusText?.takeIf(String::isNotBlank) ?: fallbackStatus,
            )
            match.scheduledAtText?.takeIf(String::isNotBlank)?.let { scheduledAtText ->
                Text(
                    text = scheduledAtText,
                    modifier = Modifier.weight(1f),
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
            ) {
                match.eventName?.takeIf(String::isNotBlank)?.let { eventName ->
                    Text(
                        text = eventName,
                        style = VlrTheme.typography.labelSmall,
                        color = VlrTheme.colors.textSecondary,
                        textAlign = TextAlign.End,
                    )
                }
                match.eventStage?.takeIf(String::isNotBlank)?.let { eventStage ->
                    Text(
                        text = eventStage,
                        style = VlrTheme.typography.labelSmall,
                        color = VlrTheme.colors.textSecondary,
                        textAlign = TextAlign.End,
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
                text = match.teamName,
                modifier = Modifier.weight(1f),
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
            )
            Text(
                text = "VS",
                style = VlrTheme.typography.label,
                color = VlrTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = match.opponentName,
                modifier = Modifier.weight(1f),
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }
    }
}
