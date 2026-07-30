package com.thor.core.common.coroutines

import com.thor.core.common.log.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches work that must never take the process down.
 *
 * A bare `viewModelScope.launch { repository.doSomething() }` propagates any
 * exception to the scope's handler, and for a view model that means the default
 * handler and a force close. Nothing a launcher does in the background — a
 * rename, a metadata write, a scan — is worth losing the whole shell over: the
 * right outcome is a logged failure and a launcher that is still on screen.
 *
 * [CancellationException] is rethrown rather than logged. Swallowing it would
 * break structured concurrency, leaving a cancelled scope's children believing
 * they completed normally.
 *
 * @param tag log tag identifying the call site
 * @param onError optional hook for surfacing the failure in the UI
 */
inline fun CoroutineScope.launchSafely(
    tag: String,
    crossinline onError: (Throwable) -> Unit = {},
    crossinline block: suspend CoroutineScope.() -> Unit,
): Job = launch {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        CrashReporter.nonFatal(tag, "Background work failed", error)
        onError(error)
    }
}

/**
 * Runs a suspending call, returning null if it fails.
 *
 * For the read-then-write sequences in the repositories, where a failure should
 * abandon the operation rather than half-apply it.
 */
suspend inline fun <T> safely(tag: String, crossinline block: suspend () -> T): T? = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    CrashReporter.nonFatal(tag, "Operation failed", error)
    null
}
