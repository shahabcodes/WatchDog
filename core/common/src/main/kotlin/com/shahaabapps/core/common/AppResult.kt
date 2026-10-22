package com.shahaabapps.core.common

/**
 * A lightweight result type for operations that can fail, avoiding exceptions
 * crossing layer boundaries.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Failure(val error: Throwable) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> runCatchingResult(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (t: Throwable) {
    if (t is kotlinx.coroutines.CancellationException) throw t
    Diag.failure(t)
    AppResult.Failure(t)
}
