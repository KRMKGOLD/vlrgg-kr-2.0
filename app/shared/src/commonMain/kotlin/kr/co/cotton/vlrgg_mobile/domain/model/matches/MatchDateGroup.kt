package kr.co.cotton.vlrgg_mobile.domain.model.matches

import kotlinx.datetime.LocalDate

data class MatchDateGroup(
    val dateLabel: String,
    val matches: List<MatchSummary>,
    /** [dateLabel]의 달력 날짜. vlr.gg 표시 시간대(KST) 기준이며 instant가 아니므로 시간대 변환에 쓰지 않는다. */
    val date: LocalDate?,
)
