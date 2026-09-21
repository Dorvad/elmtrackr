package com.elmtrackr.wear

import android.util.Log
import com.elmtrackr.wear.monitoring.WearCrashReporting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * [runCatching], minus the part that breaks structured concurrency.
 *
 * The watch app guards its startup and background paths so a failing system
 * surface degrades instead of taking the process down, and plain `runCatching`
 * is the obvious way to write that. It catches [Throwable] though, and on a
 * coroutine that includes the [CancellationException] the framework throws to
 * unwind a cancelled job — so a guard around a suspending call turns
 * "this work was cancelled" into "this work failed", and whatever follows the
 * guard runs on a coroutine that was supposed to have stopped. Cancellation is
 * rethrown here; everything else is reported as a failure.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

/**
 * SupervisorJob does not stop an unhandled child from reaching the thread's
 * default handler, which on Android kills the process. The tile host and the
 * data-layer listener both start work the system triggers — a reviewer adding
 * the tile is enough — so those scopes have to swallow and report rather than
 * take the app down.
 */
internal fun wearBackgroundExceptionHandler(tag: String) = CoroutineExceptionHandler { _, throwable ->
    if (throwable is CancellationException) return@CoroutineExceptionHandler
    Log.e(tag, "Unhandled failure on a watch background scope", throwable)
    WearCrashReporting.report(throwable)
}
