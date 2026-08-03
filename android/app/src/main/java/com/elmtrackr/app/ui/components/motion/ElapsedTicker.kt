package com.elmtrackr.app.ui.components.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Time elapsed since [start], in whole [unitMillis] units, ticking while the
 * screen is at least STARTED.
 *
 * Two reasons this exists rather than a bare `LaunchedEffect { while (true) }`,
 * which is what the three live-duration displays each had their own copy of:
 *
 * `repeatOnLifecycle` stops the tick when the app is backgrounded. Composition
 * survives being stopped, so the plain loop kept waking the main thread once a
 * second behind the launcher — `delay` is a scheduled resumption, not a frame
 * callback, so it does not pause with the display. Nothing was visible to
 * update; the wakeups were pure battery cost.
 *
 * [unitMillis] keeps recomposition proportional to what the caller actually
 * shows. A display reading whole minutes passes 60_000 and recomposes once a
 * minute instead of sixty times, because writing an unchanged value to a
 * `mutableLongStateOf` is not a state change. The delay stays at one second
 * either way, so the rollover is still prompt.
 *
 * Returns 0 when [start] is null or in the future.
 */
@Composable
fun rememberElapsedUnits(start: Instant?, unitMillis: Long = 1_000L): Long {
    var elapsed by remember(start, unitMillis) {
        mutableLongStateOf(elapsedUnits(start, unitMillis))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(start, unitMillis, lifecycleOwner) {
        if (start == null) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                elapsed = elapsedUnits(start, unitMillis)
                delay(TICK_MILLIS)
            }
        }
    }
    return elapsed
}

/** `now` is a parameter so the arithmetic is testable without a clock. */
internal fun elapsedUnits(start: Instant?, unitMillis: Long, now: Instant = Instant.now()): Long {
    if (start == null) return 0L
    return ((now.toEpochMilli() - start.toEpochMilli()) / unitMillis).coerceAtLeast(0L)
}

private const val TICK_MILLIS = 1_000L
