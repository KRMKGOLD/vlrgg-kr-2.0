package kr.co.cotton.vlrgg_mobile.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class KoreanDateTimeFormatTest {

    @Test
    fun monthDayUsesKoreanWeekdayForEveryDay() {
        assertEquals(
            listOf(
                "10월 26일 (월)",
                "10월 27일 (화)",
                "10월 28일 (수)",
                "10월 29일 (목)",
                "10월 30일 (금)",
                "10월 31일 (토)",
                "11월 1일 (일)",
            ),
            (26..32).map { day ->
                val date = if (day <= 31) LocalDate(2026, 10, day) else LocalDate(2026, 11, day - 31)
                KoreanDateTimeFormat.monthDay(date)
            },
        )
    }

    @Test
    fun timeUsesKoreanPeriodAndTwelveHourClockAtBoundaries() {
        assertEquals("오전 12:00", KoreanDateTimeFormat.time(LocalTime(0, 0)))
        assertEquals("오전 3:00", KoreanDateTimeFormat.time(LocalTime(3, 0)))
        assertEquals("오전 11:59", KoreanDateTimeFormat.time(LocalTime(11, 59)))
        assertEquals("오후 12:00", KoreanDateTimeFormat.time(LocalTime(12, 0)))
        assertEquals("오후 7:05", KoreanDateTimeFormat.time(LocalTime(19, 5)))
        assertEquals("오후 11:59", KoreanDateTimeFormat.time(LocalTime(23, 59)))
    }

    @Test
    fun dateTimeWithWeekdayConvertsInstantToGivenTimeZone() {
        val scheduledAt = Instant.parse("2026-10-26T18:00:00Z")

        assertEquals("2026년 10월 27일 (화) 오전 3:00", KoreanDateTimeFormat.dateTimeWithWeekday(scheduledAt, SEOUL))
        assertEquals("2026년 10월 26일 (월) 오후 2:00", KoreanDateTimeFormat.dateTimeWithWeekday(scheduledAt, NEW_YORK))
    }

    @Test
    fun dateTimeWithWeekdayKeepsMidnightAndNoonBoundariesInSeoul() {
        assertEquals(
            "2026년 10월 27일 (화) 오전 12:00",
            KoreanDateTimeFormat.dateTimeWithWeekday(Instant.parse("2026-10-26T15:00:00Z"), SEOUL),
        )
        assertEquals(
            "2026년 10월 27일 (화) 오후 12:00",
            KoreanDateTimeFormat.dateTimeWithWeekday(Instant.parse("2026-10-27T03:00:00Z"), SEOUL),
        )
    }

    @Test
    fun dateTimeConvertsInstantToGivenTimeZoneWithoutWeekday() {
        val publishedAt = Instant.parse("2026-10-25T15:09:14Z")

        assertEquals("2026년 10월 26일 오전 12:09", KoreanDateTimeFormat.dateTime(publishedAt, SEOUL))
        assertEquals("2026년 10월 25일 오전 11:09", KoreanDateTimeFormat.dateTime(publishedAt, NEW_YORK))
    }

    @Test
    fun dateUsesKoreanYearMonthDayWithoutPadding() {
        assertEquals("2026년 9월 19일", KoreanDateTimeFormat.date(LocalDate(2026, 9, 19)))
        assertEquals("2026년 12월 1일", KoreanDateTimeFormat.date(LocalDate(2026, 12, 1)))
    }

    @Test
    fun dateOrSourceFormatsParsedDateAndOtherwiseKeepsSourceText() {
        assertEquals(
            "2026년 9월 19일",
            KoreanDateTimeFormat.dateOrSource(LocalDate(2026, 9, 19), "September 19, 2026"),
        )
        // 서버가 field를 주지 않거나 mapper가 형식 오류로 null을 만든 경우다.
        assertEquals("September 19, 2026", KoreanDateTimeFormat.dateOrSource(null, "September 19, 2026"))
        assertEquals("", KoreanDateTimeFormat.dateOrSource(null, ""))
    }

    @Test
    fun remainingTimeTranslatesKnownVlrEtaFormats() {
        assertEquals("22분 후", KoreanDateTimeFormat.remainingTime("22m"))
        assertEquals("10시간 32분 후", KoreanDateTimeFormat.remainingTime("10h 32m"))
        assertEquals("1일 1시간 후", KoreanDateTimeFormat.remainingTime("1d 1h"))
        assertEquals("3시간 후", KoreanDateTimeFormat.remainingTime("3h"))
        assertEquals("2일 후", KoreanDateTimeFormat.remainingTime("2d"))
        assertEquals("1일 2시간 3분 후", KoreanDateTimeFormat.remainingTime("1d 2h 3m"))
        assertEquals("5분 후", KoreanDateTimeFormat.remainingTime(" 05m "))
    }

    @Test
    fun remainingTimeHidesUnknownFormats() {
        listOf(
            "",
            " ",
            "LIVE",
            "IN 2 HOURS",
            "1h ago",
            "2시간 후",
            "22M",
            "32m 10h",
            "1h 1h",
            "1w",
            "1d  1h",
            "1d1h",
            "-5m",
            "1.5h",
            "1d 2h 3m 4m",
        ).forEach { label ->
            assertNull(KoreanDateTimeFormat.remainingTime(label), "'$label' must not be shown")
        }
    }

    private companion object {
        val SEOUL = TimeZone.of("Asia/Seoul")
        val NEW_YORK = TimeZone.of("America/New_York")
    }
}
