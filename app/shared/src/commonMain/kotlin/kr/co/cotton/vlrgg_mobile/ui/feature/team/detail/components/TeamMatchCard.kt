package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.teamMatchCardTag
import kr.co.cotton.vlrgg_mobile.ui.component.TeamMatchRow
import kr.co.cotton.vlrgg_mobile.ui.component.TeamMatchRowStatus

internal enum class TeamMatchSection {
    Upcoming,
    Recent,
}

/**
 * Team upcoming and recent match row. The source status text is the score of a recent match
 * and the remaining time of an upcoming one; both stay as the source wrote them.
 */
@Composable
internal fun TeamMatchCard(
    match: TeamMatch,
    section: TeamMatchSection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = match.statusText?.takeIf(String::isNotBlank)
    val status = when {
        section == TeamMatchSection.Recent && statusText != null -> TeamMatchRowStatus.Score(statusText)
        section == TeamMatchSection.Recent -> TeamMatchRowStatus.Label("종료")
        else -> TeamMatchRowStatus.Label(statusText ?: "예정")
    }
    TeamMatchRow(
        title = "vs ${match.opponentName}",
        onClick = onClick,
        contentDescription = "경기 상세: ${match.teamName} 대 ${match.opponentName}",
        context = listOfNotNull(
            match.eventName?.takeIf(String::isNotBlank),
            match.eventStage?.takeIf(String::isNotBlank),
        ).joinToString(" · ").takeIf(String::isNotEmpty),
        status = status,
        date = match.scheduledAtText?.takeIf(String::isNotBlank),
        modifier = modifier.testTag(teamMatchCardTag(match.id)),
    )
}
