package kr.co.cotton.vlrgg_mobile.domain

import kotlin.time.Duration

sealed interface AppResult<out T> {
    data class Success<out T>(
        val data: T,
    ) : AppResult<T>

    data object Failure : AppResult<Nothing>

    data class Busy(
        val retryDelay: Duration,
    ) : AppResult<Nothing>
}

inline fun <T> AppResult<T>.onSuccess(
    action: (T) -> Unit,
): AppResult<T> {
    if (this is AppResult.Success) {
        action(data)
    }
    return this
}

inline fun <T> AppResult<T>.onFailure(
    action: () -> Unit,
): AppResult<T> {
    if (this is AppResult.Failure) {
        action()
    }
    return this
}

inline fun <T> AppResult<T>.onBusy(
    action: (retryDelay: Duration) -> Unit,
): AppResult<T> {
    if (this is AppResult.Busy) {
        action(retryDelay)
    }
    return this
}
