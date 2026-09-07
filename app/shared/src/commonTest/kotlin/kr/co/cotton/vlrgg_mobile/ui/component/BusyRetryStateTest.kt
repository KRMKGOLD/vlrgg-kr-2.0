package kr.co.cotton.vlrgg_mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class BusyRetryStateTest {
    @Test
    fun zeroCooldownCanRetryImmediately() {
        assertTrue(BusyRetryState.create("matches:upcoming:page:1", ZERO).canRetry())
    }

    @Test
    fun positiveCooldownCannotRetryImmediately() {
        val clock = TestTimeSource()
        val busy = BusyRetryStateFactory.forTest(clock).create("matches:upcoming:page:1", 1.seconds)

        assertFalse(busy.canRetry())
        clock += 1.seconds
        assertTrue(busy.canRetry())
    }

    @Test
    fun dismissingTheDialogKeepsTheCooldownActive() {
        val busy = BusyRetryState.create("news:page:2", 1.seconds)

        assertFalse(busy.dismiss().isDialogVisible)
        assertFalse(busy.dismiss().canRetry())
    }
}
