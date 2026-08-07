package kr.co.cotton.vlrgg_mobile.domain

sealed interface AppResult<out T> {
    data class Success<out T>(
        val data: T,
    ) : AppResult<T>

    data object Failure : AppResult<Nothing>
}