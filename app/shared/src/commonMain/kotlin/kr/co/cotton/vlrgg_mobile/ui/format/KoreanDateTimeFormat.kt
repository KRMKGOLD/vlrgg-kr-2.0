package kr.co.cotton.vlrgg_mobile.ui.format

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * 화면에 보이는 날짜·시각 문자열을 한 곳에서 만든다.
 *
 * 요일과 오전/오후는 플랫폼 locale 대신 직접 만들어 Android·iOS 결과를 맞춘다.
 * 시간대 변환은 instant 입력에만 적용하고, local date/time 값은 받은 그대로 표시한다.
 */
internal object KoreanDateTimeFormat {

    /** `10월 27일 (화)` */
    fun monthDay(date: LocalDate): String =
        "${date.month.number}월 ${date.day}일 (${date.dayOfWeek.koreanShortName()})"

    /** `오전 3:00`, `오후 7:05`. 00시는 `오전 12:00`, 12시는 `오후 12:00`이다. */
    fun time(time: LocalTime): String {
        val period = if (time.hour < 12) "오전" else "오후"
        val hour = (time.hour % 12).takeIf { it != 0 } ?: 12
        return "$period $hour:${time.minute.toString().padStart(2, '0')}"
    }

    /** `2026년 10월 27일 (화) 오전 3:00`. [timeZone] 기준으로 변환한다. */
    fun dateTimeWithWeekday(instant: Instant, timeZone: TimeZone): String {
        val local = instant.toLocalDateTime(timeZone)
        return "${local.date.year}년 ${monthDay(local.date)} ${time(local.time)}"
    }

    /** `2026년 10월 26일 오전 12:09`. [timeZone] 기준으로 변환한다. */
    fun dateTime(instant: Instant, timeZone: TimeZone): String {
        val local = instant.toLocalDateTime(timeZone)
        return "${local.date.year}년 ${local.date.month.number}월 ${local.date.day}일 ${time(local.time)}"
    }

    /**
     * vlr.gg 예정 경기 ETA(`22m`, `10h 32m`, `1d 1h`)를 `22분 후`처럼 바꾼다.
     * 단위는 d→h→m 순서로 한 번씩만 올 수 있으며, 형식을 알 수 없으면 null이다.
     */
    fun remainingTime(label: String): String? {
        val tokens = label.trim().split(' ').takeIf { it.isNotEmpty() && it.size <= RELATIVE_UNITS.length } ?: return null
        var previousUnitIndex = -1
        val parts = tokens.map { token ->
            val match = RELATIVE_TOKEN.matchEntire(token) ?: return null
            val unitIndex = RELATIVE_UNITS.indexOf(match.groupValues[2].single())
            if (unitIndex <= previousUnitIndex) return null
            previousUnitIndex = unitIndex
            "${match.groupValues[1].toInt()}${RELATIVE_UNIT_NAMES[unitIndex]}"
        }
        return parts.joinToString(" ") + " 후"
    }

    private fun DayOfWeek.koreanShortName(): String = when (this) {
        DayOfWeek.MONDAY -> "월"
        DayOfWeek.TUESDAY -> "화"
        DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"
        DayOfWeek.FRIDAY -> "금"
        DayOfWeek.SATURDAY -> "토"
        DayOfWeek.SUNDAY -> "일"
    }

    private val RELATIVE_TOKEN = Regex("""(\d{1,4})([dhm])""")
    private const val RELATIVE_UNITS = "dhm"
    private val RELATIVE_UNIT_NAMES = listOf("일", "시간", "분")
}
