package kr.co.cotton.vlrgg_mobile.ui.feature.matches.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.matchCardTag

@Composable
fun MatchCard(
    match: MatchSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showEventName: Boolean = true,
) {
    MatchContentItem(
        item = match.toMatchContentItem(showEventName),
        onClick = onClick,
        testTag = matchCardTag(match.id),
        modifier = modifier,
    )
}

internal fun MatchSummary.toMatchContentItem(showEventName: Boolean): MatchContentItemModel =
    MatchContentItemModel(
        homeTeamName = homeTeam.name,
        awayTeamName = awayTeam.name,
        scoreLabel = scoreOrScheduledLabel(),
        scoreStyle = if (homeScore != null && awayScore != null) {
            MatchContentItemScoreStyle.DISPLAY
        } else {
            MatchContentItemScoreStyle.LABEL
        },
        scoreTestTag = matchScoreTag(id),
        leadingContent = if (status == MatchStatus.UPCOMING) {
            MatchContentItemLeading.Time(timeLabel, relativeTimeLabel)
        } else {
            MatchContentItemLeading.Status(
                status = status.toChipStatus(),
                label = status.displayLabel(),
                relativeTimeLabel = relativeTimeLabel,
            )
        },
        eventName = event.name.takeIf { showEventName },
        eventSeries = event.series,
        stateDescription = status.accessibilityLabel(),
    )

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
