package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.data.remote.PublicApiBusyException
import kr.co.cotton.vlrgg_mobile.domain.AppResult

internal suspend fun <T> wrapAsAppResult(
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (exception: CancellationException) {
    throw exception
} catch (exception: PublicApiBusyException) {
    AppResult.Busy(exception.retryDelay)
} catch (_: Exception) {
    AppResult.Failure
}
