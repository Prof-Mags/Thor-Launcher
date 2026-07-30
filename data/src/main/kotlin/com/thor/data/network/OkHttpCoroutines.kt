package com.thor.data.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits an OkHttp call as a suspending function.
 *
 * Cancelling the coroutine cancels the HTTP call, which matters for the
 * metadata scraper: navigating away from a game mid-scrape must abort its
 * in-flight requests rather than leave them running to completion.
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                // A failure caused by our own cancellation is not an error.
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        },
    )

    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
}
