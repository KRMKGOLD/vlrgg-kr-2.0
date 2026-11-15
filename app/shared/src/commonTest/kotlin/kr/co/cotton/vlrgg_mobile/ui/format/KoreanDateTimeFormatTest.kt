package kr.co.cotton.vlrgg_mobile.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventMonthDay

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

    @Test
    fun periodFormatsEveryEventPeriodShape() {
        assertEquals("9월 12일 ~ 10월 4일", KoreanDateTimeFormat.period(EventMonthDay(9, 12), EventMonthDay(10, 4), "Sep 12—Oct 4"))
        assertEquals("11월 20일 ~ 23일", KoreanDateTimeFormat.period(EventMonthDay(11, 20), EventMonthDay(11, 23), "Nov 20—23"))
        assertEquals("12월 17일", KoreanDateTimeFormat.period(EventMonthDay(12, 17), EventMonthDay(12, 17), "Dec 17"))
        assertEquals("11월 1일 ~ 미정", KoreanDateTimeFormat.period(EventMonthDay(11, 1), null, "Nov 1—TBD"))
        // A period that crosses the new year keeps both months.
        assertEquals("12월 28일 ~ 1월 4일", KoreanDateTimeFormat.period(EventMonthDay(12, 28), EventMonthDay(1, 4), "Dec 28—Jan 4"))
    }

    @Test
    fun periodWithoutStructuredStartKeepsTheSourceLabel() {
        assertEquals("Sep 13 – Dec 25, 2026", KoreanDateTimeFormat.period(null, null, "Sep 13 – Dec 25, 2026"))
        assertEquals("Nov 5—16", KoreanDateTimeFormat.period(null, EventMonthDay(11, 16), "Nov 5—16"))
        assertNull(KoreanDateTimeFormat.period(null, null, null))
    }

    @Test
    fun spokenPeriodReadsTheRangeWithoutTheTilde() {
        assertEquals("9월 12일부터 10월 4일까지", KoreanDateTimeFormat.periodSpoken(EventMonthDay(9, 12), EventMonthDay(10, 4), "x"))
        assertEquals("11월 20일부터 23일까지", KoreanDateTimeFormat.periodSpoken(EventMonthDay(11, 20), EventMonthDay(11, 23), "x"))
        assertEquals("12월 17일", KoreanDateTimeFormat.periodSpoken(EventMonthDay(12, 17), EventMonthDay(12, 17), "x"))
        assertEquals("11월 1일부터, 종료일 미정", KoreanDateTimeFormat.periodSpoken(EventMonthDay(11, 1), null, "x"))
        assertEquals("Nov 5—16", KoreanDateTimeFormat.periodSpoken(null, null, "Nov 5—16"))
        assertNull(KoreanDateTimeFormat.periodSpoken(null, null, null))
    }

    @Test
    fun matchDateOmitsTheYearOnlyInTheDeviceYear() {
        assertEquals("7월 12일", KoreanDateTimeFormat.matchDate(LocalDate(2026, 7, 12), currentYear = 2026))
        assertEquals("2025년 7월 12일", KoreanDateTimeFormat.matchDate(LocalDate(2025, 7, 12), currentYear = 2026))
        assertEquals("2027년 1월 3일", KoreanDateTimeFormat.matchDate(LocalDate(2027, 1, 3), currentYear = 2026))
    }

    @Test
    fun matchDateTimeAddsTheTimeOnlyWhenPresent() {
        assertEquals(
            "9월 24일 오후 6:00",
            KoreanDateTimeFormat.matchDateTime(LocalDate(2026, 9, 24), LocalTime(18, 0), currentYear = 2026),
        )
        assertEquals(
            "2025년 9월 24일 오후 6:00",
            KoreanDateTimeFormat.matchDateTime(LocalDate(2025, 9, 24), LocalTime(18, 0), currentYear = 2026),
        )
        assertEquals(
            "8월 28일 오후 7:05",
            KoreanDateTimeFormat.matchDateTime(LocalDate(2026, 8, 28), LocalTime(19, 5), currentYear = 2026),
        )
        assertEquals("9월 24일", KoreanDateTimeFormat.matchDateTime(LocalDate(2026, 9, 24), null, currentYear = 2026))
    }

    @Test
    fun remainingTimeOrSourceTranslatesOnlyRemainingTimeSources() {
        assertEquals("1일 6시간 후", KoreanDateTimeFormat.remainingTimeOrSource("1d 6h"))
        assertEquals("18시간 5분 후", KoreanDateTimeFormat.remainingTimeOrSource("18h 5m"))
        assertEquals("22분 후", KoreanDateTimeFormat.remainingTimeOrSource("22m"))
        // Scores, live markers and unknown text stay as the source wrote them.
        listOf("2 : 1", "0 : 2", "LIVE", "TBD", "in 2d", "1h ago", "6h 1d", "").forEach { source ->
            assertEquals(source, KoreanDateTimeFormat.remainingTimeOrSource(source))
        }
    }

    private companion object {
        val SEOUL = TimeZone.of("Asia/Seoul")
        val NEW_YORK = TimeZone.of("America/New_York")
    }
}
