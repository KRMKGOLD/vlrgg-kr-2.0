package kr.co.cotton.vlrgg_mobile.domain

sealed interface AppResult<out T> {
    data class Success<out T>(
        val data: T,
    ) : AppResult<T>

    data object Failure : AppResult<Nothing>
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
