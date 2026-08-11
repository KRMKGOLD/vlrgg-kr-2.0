package kr.co.cotton.vlrgg_mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

        val result = source
            .onSuccess { successInvoked = true }
            .onFailure { failureInvoked = true }

        assertFalse(successInvoked)
        assertTrue(failureInvoked)
        assertSame(source, result)
    }
}
