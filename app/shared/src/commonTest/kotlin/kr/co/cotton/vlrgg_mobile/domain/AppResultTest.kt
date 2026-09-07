package kr.co.cotton.vlrgg_mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AppResultTest {

    @Test
    fun successInvokesOnlySuccessActionAndReturnsSameResult() {
        val source = AppResult.Success("value")
        var receivedValue: String? = null
        var failureInvoked = false

        val result = source
            .onSuccess { value -> receivedValue = value }
            .onFailure { failureInvoked = true }

        assertEquals("value", receivedValue)
        assertFalse(failureInvoked)
        assertSame(source, result)
    }

    @Test
    fun failureInvokesOnlyFailureActionAndReturnsSameResult() {
        val source: AppResult<String> = AppResult.Failure
        var successInvoked = false
        var failureInvoked = false
        var busyInvoked = false

        val result = source
            .onSuccess { successInvoked = true }
            .onFailure { failureInvoked = true }
            .onBusy { busyInvoked = true }

        assertFalse(successInvoked)
        assertTrue(failureInvoked)
        assertFalse(busyInvoked)
        assertSame(source, result)
    }

    @Test
    fun busyInvokesOnlyBusyActionAndReturnsSameResult() {
        val source: AppResult<String> = AppResult.Busy(2.seconds)
        var successInvoked = false
        var failureInvoked = false
        var busyDelay = 0.seconds
        var busyInvocations = 0

        val result = source
            .onSuccess { successInvoked = true }
            .onFailure { failureInvoked = true }
            .onBusy {
                busyInvocations += 1
                busyDelay = it
            }

        assertFalse(successInvoked)
        assertFalse(failureInvoked)
        assertEquals(2.seconds, busyDelay)
        assertEquals(1, busyInvocations)
        assertSame(source, result)
    }
}
