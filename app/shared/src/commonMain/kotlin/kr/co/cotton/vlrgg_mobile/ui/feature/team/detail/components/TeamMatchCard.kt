package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.teamMatchCardTag
import kr.co.cotton.vlrgg_mobile.ui.component.TeamMatchRow
import kr.co.cotton.vlrgg_mobile.ui.component.TeamMatchRowStatus
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat

internal enum class TeamMatchSection {
    Upcoming,
    Recent,
}

/**
 * Team upcoming and recent match row. The source status text is the score of a recent match
 * and the remaining time of an upcoming one; a remaining time reads in Korean (`1일 6시간 후`) and
 * any other source text stays as written. The schedule is Korean when the server sends
 * `scheduledDate`, otherwise the source `scheduledAtText`.
 */
@Composable
internal fun TeamMatchCard(
    match: TeamMatch,
    section: TeamMatchSection,
    currentYear: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = match.statusText?.takeIf(String::isNotBlank)
    val status = when {
        section == TeamMatchSection.Recent && statusText != null -> TeamMatchRowStatus.Score(statusText)
        section == TeamMatchSection.Recent -> TeamMatchRowStatus.Label("종료")
        statusText != null -> TeamMatchRowStatus.Label(KoreanDateTimeFormat.remainingTimeOrSource(statusText))
        else -> TeamMatchRowStatus.Label("예정")
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
        date = match.scheduleLabel(currentYear),
        modifier = modifier.testTag(teamMatchCardTag(match.id)),
    )
}

private fun TeamMatch.scheduleLabel(currentYear: Int): String? =
    scheduledDate?.let { KoreanDateTimeFormat.matchDateTime(it, scheduledTime, currentYear) }
        ?: scheduledAtText?.takeIf(String::isNotBlank)
