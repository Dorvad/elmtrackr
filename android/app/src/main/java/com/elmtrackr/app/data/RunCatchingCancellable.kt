package com.elmtrackr.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * [runCatching] for suspending work: everything is a failure except being told to stop.
 *
 * Plain `runCatching` catches [Throwable], and on a coroutine that includes the
 * [CancellationException] the framework throws to unwind a cancelled job. Wrapped
 * around a suspending call it therefore turns "this was cancelled" into "this
 * failed" — the cancellation is recorded as an error, and whatever follows the
 * guard keeps running on a coroutine that was supposed to have stopped.
 *
 * In sync that was not a technicality. WorkManager cancels a running sync whenever
 * its network constraint is lost or a newer request replaces it, so an ordinary
 * interruption finished by publishing "Failed: … was cancelled", after working
 * through every step that came after the one it was cancelled in.
 *
 * Cancellation is rethrown; anything else comes back as a failed [Result], exactly
 * as `runCatching` would return it.
 *
 * "Cancelled" is decided by asking the coroutine, not by the exception's type. A
 * [CancellationException] can also be thrown by something inside the block that
 * gave up on its own — a `withTimeout` in a library is the usual one — while the
 * caller is still very much alive. Rethrowing that would end the caller silently,
 * with no error and no result; here it is an ordinary failure.
 */
internal suspend inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (throwable: Throwable) {
        currentCoroutineContext().ensureActive()
        Result.failure(throwable)
    }
