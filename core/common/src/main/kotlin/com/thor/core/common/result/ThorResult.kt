package com.thor.core.common.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A loading-aware result wrapper.
 *
 * Repositories expose `Flow<T>`; screens that need to distinguish "still
 * loading" from "loaded but empty" map that flow through [asResult].
 */
sealed interface ThorResult<out T> {
    data class Success<T>(val data: T) : ThorResult<T>
    data class Error(val cause: Throwable, val message: String? = null) : ThorResult<Nothing>
    data object Loading : ThorResult<Nothing>
}

/** Wraps a flow's emissions, prefixing [ThorResult.Loading] and catching failures. */
fun <T> Flow<T>.asResult(): Flow<ThorResult<T>> = this
    .map<T, ThorResult<T>> { ThorResult.Success(it) }
    .onStart { emit(ThorResult.Loading) }
    .catch { emit(ThorResult.Error(it, it.message)) }

/** Returns the payload, or `null` while loading or on failure. */
fun <T> ThorResult<T>.dataOrNull(): T? = (this as? ThorResult.Success)?.data

/**
 * Runs [block], converting any non-cancellation throwable into a failed
 * [Result] rather than letting it escape.
 *
 * Coroutine cancellation is rethrown so that structured concurrency keeps
 * working — swallowing it is the classic source of scopes that refuse to die.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: kotlinx.coroutines.CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
