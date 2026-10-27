package kr.co.cotton.vlrgg_mobile.common.scraping

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * Parses VLR.GG list date labels such as `Tue, October 27, 2026`. The value is a calendar date in
 * the display time zone VLR.GG chose for the requester, not an instant. A weekday that disagrees
 * with the date is rejected.
 */
internal fun String.toVlrDisplayDateOrNull(): String? = try {
    LocalDate.parse(trim(), VLR_DISPLAY_DATE_FORMAT).toString()
} catch (_: DateTimeException) {
    null
}

/** Parses VLR.GG list clock labels such as `3:00 AM` into ISO local time (`03:00`). */
internal fun String.toVlrDisplayTimeOrNull(): String? = try {
    LocalTime.parse(trim(), VLR_DISPLAY_TIME_FORMAT).toString()
} catch (_: DateTimeException) {
    null
}

private val VLR_DISPLAY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("EEE, MMMM d, uuuu", Locale.ENGLISH)
    .withResolverStyle(ResolverStyle.STRICT)

private val VLR_DISPLAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("h:mm a")
    .toFormatter(Locale.ENGLISH)
    .withResolverStyle(ResolverStyle.STRICT)
