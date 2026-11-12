package kr.co.cotton.vlrgg_mobile.ui.format

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class DeviceTodayUiTest {

    @Test
    fun resumingAfterTheYearChangesReadsTheNewDate() = runComposeUiTest {
        val clock = FakeClock(at(2026, 12, 31, 23, 30))
        val owner = TestLifecycleOwner()
        setContent {
            CompositionLocalProvider(LocalDeviceClock provides clock, LocalLifecycleOwner provides owner) {
                Text("today ${rememberDeviceToday()}")
            }
        }
        onNodeWithText("today 2026-12-31").assertExists()

        // The app goes to the background before midnight and comes back in the new year.
        owner.registry.currentState = Lifecycle.State.STARTED
        clock.now = at(2027, 1, 1, 9, 0)
        owner.registry.currentState = Lifecycle.State.RESUMED
        waitForIdle()
        onNodeWithText("today 2027-01-01").assertExists()
    }

    @Test
    fun midnightWhileShownReadsTheNewDate() = runComposeUiTest {
        val clock = FakeClock(at(2026, 12, 31, 23, 59, 58))
        setContent {
            CompositionLocalProvider(LocalDeviceClock provides clock) {
                Text("today ${rememberDeviceToday()}")
            }
        }
        onNodeWithText("today 2026-12-31").assertExists()

        clock.now = at(2027, 1, 1, 0, 0, 1)
        mainClock.advanceTimeBy(3_000)
        onNodeWithText("today 2027-01-01").assertExists()
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Instant =
        LocalDateTime(year, month, day, hour, minute, second).toInstant(TimeZone.currentSystemDefault())

    private class FakeClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}
