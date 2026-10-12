package com.example.data

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Suspends until an ML Kit / Play Services [Task] completes. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
