package kr.co.cotton.vlrgg_mobile.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Clock behind [rememberDeviceToday]; tests provide their own to cross a date. */
internal val LocalDeviceClock = staticCompositionLocalOf<Clock> { Clock.System }

/** Reads the device time zone for [rememberDeviceToday] each time it is needed; tests can change it. */
internal val LocalDeviceTimeZone = staticCompositionLocalOf<() -> TimeZone> { TimeZone::currentSystemDefault }

/**
 * The device's calendar date in its system time zone, for date rules such as
 * [KoreanDateTimeFormat.matchDate]'s year. It is read again each time the screen resumes and at
 * each midnight while it is shown, so a screen left open into a new year does not keep the old one.
 * A resume also reschedules that midnight in the time zone of the moment. A time zone change while
 * the screen stays in front is picked up at the next resume or the previously scheduled midnight.
 */
@Composable
internal fun rememberDeviceToday(): LocalDate {
    val clock = LocalDeviceClock.current
    val timeZone = LocalDeviceTimeZone.current
    var today by remember(clock, timeZone) { mutableStateOf(clock.todayIn(timeZone())) }
    // Each resume restarts the midnight wait, so a time zone changed in the background takes effect.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(clock, timeZone) {
        today = clock.todayIn(timeZone())
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(clock, timeZone, resumes) {
        while (true) {
            val zone = timeZone()
            val now = clock.now()
            val nextDay = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            delay((nextDay - now).coerceAtLeast(1.seconds))
            today = clock.todayIn(timeZone())
        }
    }
    return today
}
