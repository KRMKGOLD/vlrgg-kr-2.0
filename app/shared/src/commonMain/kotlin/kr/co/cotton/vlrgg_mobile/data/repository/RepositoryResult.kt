package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.domain.AppResult

internal suspend fun <T> wrapAsAppResult(
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (exception: CancellationException) {
    throw exception
} catch (_: Exception) {
    AppResult.Failure
}
