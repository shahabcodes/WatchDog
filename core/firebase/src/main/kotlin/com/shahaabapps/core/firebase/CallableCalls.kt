package com.shahaabapps.core.firebase

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.tasks.await
import java.io.IOException

/** A Cloud Function couldn't be reached (no internet, timeout). */
class OfflineException : IOException("No internet connection. Connect and try again.")

/**
 * Calls a Cloud Function, turning connection failures into [OfflineException].
 * Offline, the Functions SDK reports a bare "INTERNAL" (or UNAVAILABLE /
 * DEADLINE_EXCEEDED), which users saw as "INTERNAL"; errors the function itself
 * raises keep their own message.
 */
suspend fun HttpsCallableReference.callSafely(data: Any? = null): HttpsCallableResult =
    try {
        (if (data == null) call() else call(data)).await()
    } catch (e: FirebaseFunctionsException) {
        if (e.isConnectionFailure()) throw OfflineException() else throw e
    } catch (e: IOException) {
        throw OfflineException()
    }

private fun FirebaseFunctionsException.isConnectionFailure(): Boolean {
    if (code == FirebaseFunctionsException.Code.UNAVAILABLE ||
        code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
    ) return true
    var c: Throwable? = cause
    while (c != null) {
        if (c is IOException) return true
        c = c.cause
    }
    // The SDK's own wrapper for transport errors has no server-supplied message.
    return code == FirebaseFunctionsException.Code.INTERNAL && (message == null || message == "INTERNAL")
}
