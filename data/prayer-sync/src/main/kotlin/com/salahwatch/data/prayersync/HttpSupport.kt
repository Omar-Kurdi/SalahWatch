package com.salahwatch.data.prayersync

import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * A suspending GET returning the body as text.
 *
 * Shared by both time sources so cancellation and error handling behave identically: cancelling the
 * coroutine cancels the call, and any non-2xx becomes an [IOException] the sync worker can retry.
 */
internal suspend fun OkHttpClient.getBody(url: HttpUrl, userAgent: String? = null): String {
    val request = Request.Builder()
        .url(url)
        .header("Accept", "application/json, text/html")
        .apply { userAgent?.let { header("User-Agent", it) } }
        .build()

    return newCall(request).await()
}

private suspend fun Call.await(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }

    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                val body = it.body.string()
                if (it.isSuccessful) {
                    continuation.resume(body) { _, _, _ -> }
                } else {
                    continuation.resumeWithException(IOException("HTTP ${it.code} for ${call.request().url}"))
                }
            }
        }
    })
}
