package com.elmtrackr.app.ui.components.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * The current time, re-read while the screen is at least STARTED.
 *
 * A composable that calls `Instant.now()` directly reads the clock once and then
 * shows that reading until something *else* causes it to recompose. On the
 * dashboard nothing else does: with no shift running there is no elapsed ticker,
 * no state changing, and nothing arriving from the database — so the clock face
 * sat at whatever minute the screen was opened on, the greeting stayed on "Good
 * morning" all afternoon, and the month heading kept naming the month that had
 * just ended.
 *
 * [unitMillis] is the granularity the caller renders at, and the returned instant
 * is truncated to it, so a clock showing `HH:mm` passes a minute and recomposes
 * once a minute rather than sixty times. Truncation is what makes that work:
 * writing an equal `Instant` back to the state is not a change, so the extra
 * ticks cost a comparison and nothing else.
 *
 * Ticking once a second regardless of [unitMillis] keeps the rollover prompt —
 * a clock that changed up to a minute late would be worse than one that never
 * changed, because it would look right.
 *
 * As in [rememberElapsedUnits], `repeatOnLifecycle` stops the tick when the app
 * is backgrounded: composition survives being stopped and `delay` is a scheduled
 * resumption rather than a frame callback, so a plain loop would keep waking the
 * main thread behind the launcher with nothing on screen to update.
 *
 * This is a clock, not an animation. It deliberately does not consult the
 * reduce-motion setting: someone who has turned animations off still needs to be
 * told the correct time.
 */
@Composable
fun rememberCurrentInstant(unitMillis: Long = MINUTE_MILLIS): Instant {
    var now by remember(unitMillis) { mutableStateOf(truncatedNow(unitMillis)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(unitMillis, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = truncatedNow(unitMillis)
                delay(TICK_MILLIS)
            }
        }
    }
    return now
}

/** `now` is a parameter so the arithmetic is testable without a clock. */
internal fun truncatedNow(unitMillis: Long, now: Instant = Instant.now()): Instant {
    if (unitMillis <= 1L) return now
    return Instant.ofEpochMilli(now.toEpochMilli() / unitMillis * unitMillis)
}

const val MINUTE_MILLIS = 60_000L
const val HOUR_MILLIS = 3_600_000L

private const val TICK_MILLIS = 1_000L
