package kr.co.cotton.vlrgg_mobile.domain.model.events

/**
 * 연도 없는 대회 기간 날짜. vlr.gg 목록 기간(`Nov 5—16`)에는 연도가 없어 서버가 ISO month-day
 * `--11-05`로 보낸다. 달력에 있는 월·일만 담는다(2월 29일 포함).
 */
data class EventMonthDay(
    val month: Int,
    val day: Int,
)
