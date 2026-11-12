package kr.co.cotton.vlrgg_mobile.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/**
 * The device's calendar date in its system time zone, for date rules such as
 * [KoreanDateTimeFormat.matchDate]'s year. It is read again each time the screen resumes and at
 * each midnight while it is shown, so a screen left open into a new year does not keep the old one.
 */
@Composable
internal fun rememberDeviceToday(): LocalDate {
    val clock = LocalDeviceClock.current
    var today by remember(clock) { mutableStateOf(clock.deviceToday()) }
    LifecycleResumeEffect(clock) {
        today = clock.deviceToday()
        onPauseOrDispose { }
    }
    LaunchedEffect(clock) {
        while (true) {
            val timeZone = TimeZone.currentSystemDefault()
            val now = clock.now()
            val nextDay = now.toLocalDateTime(timeZone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
            delay((nextDay - now).coerceAtLeast(1.seconds))
            today = clock.deviceToday()
        }
    }
    return today
}

private fun Clock.deviceToday(): LocalDate = todayIn(TimeZone.currentSystemDefault())
