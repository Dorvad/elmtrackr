package com.elmtrackr.wear

import kotlinx.coroutines.CancellationException

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
