package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RepositoryResultTest {

    @Test
    fun successfulBlockReturnsSuccess() = runTest {
        val result = wrapAsAppResult { "result" }

        assertEquals(AppResult.Success("result"), result)
    }

    @Test
    fun nonCancellationExceptionReturnsFailure() = runTest {
        val result = wrapAsAppResult<String> {
            throw IllegalStateException("failure")
        }

        assertSame(AppResult.Failure, result)
    }

    @Test
    fun cancellationExceptionIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> {
            wrapAsAppResult<String> {
                throw cancellation
            }
        }

        assertSame(cancellation, thrown)
    }
}
