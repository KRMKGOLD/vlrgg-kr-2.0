package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kr.co.cotton.vlrgg_mobile.ui.component.TeamMatchOutcome
import kr.co.cotton.vlrgg_mobile.ui.component.TeamMatchRow
import kr.co.cotton.vlrgg_mobile.ui.component.TeamMatchRowStatus
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat

internal fun playerRecentMatchCardTag(matchId: String) = playerMatchCardTag(matchId)
internal fun playerMatchScoreTag(matchId: String) = "player-match-score-$matchId"

/** Player recent match row; the date is `7월 12일` when the server sends `playedDate`, otherwise the source. */
@Composable
internal fun PlayerRecentMatchCard(
    match: PlayerRecentMatch,
    currentYear: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TeamMatchRow(
        title = "${match.teamA.name} vs ${match.teamB.name}",
        onClick = onClick,
        contentDescription = match.accessibilityLabel(),
        context = listOfNotNull(match.eventName, match.eventStage?.takeIf(String::isNotBlank))
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .takeIf(String::isNotEmpty),
        status = match.scoreStatus(),
        date = match.playedDate?.let { KoreanDateTimeFormat.matchDate(it, currentYear) }
            ?: match.playedOn?.takeIf(String::isNotBlank),
        outcome = match.outcome.rowOutcome(),
        statusTestTag = playerMatchScoreTag(match.id),
        modifier = modifier.testTag(playerRecentMatchCardTag(match.id)),
    )
}

/** Missing scores stay `—`; two missing scores show a single muted `—`. */
private fun PlayerRecentMatch.scoreStatus(): TeamMatchRowStatus.Score =
    if (teamAScore == null && teamBScore == null) {
        TeamMatchRowStatus.Score("—", isMissing = true)
    } else {
        TeamMatchRowStatus.Score("${teamAScore ?: "—"} : ${teamBScore ?: "—"}")
    }

/**
 * `경기 상세: Nongshim RedForce 2, BBL Esports 0, 승리`, or `A 대 B` without both scores. The result
 * column's text ends the label, so the outcome is read and not carried by color.
 */
private fun PlayerRecentMatch.accessibilityLabel(): String {
    val scoreA = teamAScore
    val scoreB = teamBScore
    val matchup = if (scoreA != null && scoreB != null) {
        "${teamA.name} $scoreA, ${teamB.name} $scoreB"
    } else {
        "${teamA.name} 대 ${teamB.name}"
    }
    return "경기 상세: $matchup, ${outcome.rowOutcome().label}"
}

internal fun PlayerRecentMatchOutcome.rowOutcome(): TeamMatchOutcome = when (this) {
    PlayerRecentMatchOutcome.WIN -> TeamMatchOutcome.Win
    PlayerRecentMatchOutcome.LOSS -> TeamMatchOutcome.Loss
    PlayerRecentMatchOutcome.UNKNOWN -> TeamMatchOutcome.Unknown
}
