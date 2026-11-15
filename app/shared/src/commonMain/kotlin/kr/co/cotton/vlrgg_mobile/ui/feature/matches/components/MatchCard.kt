package kr.co.cotton.vlrgg_mobile.ui.feature.matches.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.matchCardTag
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import androidx.compose.ui.platform.testTag
import kr.co.cotton.vlrgg_mobile.ui.component.MatchRow
import kr.co.cotton.vlrgg_mobile.ui.component.MatchRowModel
import kr.co.cotton.vlrgg_mobile.ui.component.MatchRowStatus
import kr.co.cotton.vlrgg_mobile.ui.component.MatchRowTeam

/** Match list and event-detail match row. [showEventName] adds the event name to the context. */
@Composable
fun MatchCard(
    match: MatchSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showEventName: Boolean = true,
) {
    MatchRow(
        model = match.toMatchRowModel(showEventName),
        onClick = onClick,
        modifier = modifier.testTag(matchCardTag(match.id)),
    )
}

internal fun MatchSummary.toMatchRowModel(showEventName: Boolean): MatchRowModel =
    MatchRowModel(
        home = MatchRowTeam(homeTeam.name, homeScore),
        away = MatchRowTeam(awayTeam.name, awayScore),
        status = rowStatus(),
        context = listOfNotNull(event.name.takeIf { showEventName }, event.series)
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .takeIf(String::isNotEmpty),
        stateDescription = status.accessibilityLabel(),
        scoreTestTag = matchScoreTag(id),
    )

private fun MatchSummary.displayTime(): String = time?.let(KoreanDateTimeFormat::time) ?: timeLabel

private fun MatchSummary.rowStatus(): MatchRowStatus = when (status) {
    MatchStatus.UPCOMING -> MatchRowStatus.Scheduled(
        time = displayTime(),
        remaining = relativeTimeLabel?.let(KoreanDateTimeFormat::remainingTime),
    )
    MatchStatus.LIVE -> MatchRowStatus.Live
    // 예정 경기가 아니면 ETA를 표시하지 않는다. 종료 경기 ETA는 남은 시간이 아니라 시작 후 지난 시간이다.
    MatchStatus.COMPLETED -> MatchRowStatus.Finished(displayTime().takeIf(String::isNotBlank))
    MatchStatus.POSTPONED -> MatchRowStatus.Other(displayTime().takeIf(String::isNotBlank), "연기", isWarning = true)
    MatchStatus.CANCELLED -> MatchRowStatus.Other(displayTime().takeIf(String::isNotBlank), "취소")
    MatchStatus.UNAVAILABLE -> MatchRowStatus.Other(displayTime().takeIf(String::isNotBlank), "정보 없음")
}

private fun MatchStatus.accessibilityLabel(): String = when (this) {
    MatchStatus.UPCOMING -> "예정 경기"
    MatchStatus.LIVE -> "라이브 경기"
    MatchStatus.COMPLETED -> "종료된 경기"
    MatchStatus.POSTPONED -> "경기가 연기되었습니다"
    MatchStatus.CANCELLED -> "경기가 취소되었습니다"
    MatchStatus.UNAVAILABLE -> "경기 정보가 없습니다"
}

internal fun matchScoreTag(matchId: String): String = "match-score-$matchId"
