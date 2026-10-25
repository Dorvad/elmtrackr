package com.elmtrackr.app.domain.dashboard

import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.Shift
import java.time.Instant

/**
 * When the dashboard asks whether a running shift was forgotten.
 *
 * A shift left running overnight is the most common way hours and pay go wrong, and
 * it is invisible on the clock card: the timer simply keeps counting. The long-shift
 * reminder notification can be switched off or missed, so the dashboard says it too,
 * with an action that fixes it rather than one that records the wrong end time.
 *
 * Sixteen hours, not the daily overtime threshold. Long shifts are real — a double, a
 * hospital night — and a warning that fires on an ordinary twelve-hour shift teaches
 * people to ignore it. Past sixteen hours a forgotten clock-out is by far the likelier
 * explanation; it is still phrased as a question, never as an error.
 */
object LongShiftNotice {

    const val THRESHOLD_MINUTES: Int = 16 * 60

    fun shouldShow(activeShift: Shift?, now: Instant): Boolean {
        val shift = activeShift ?: return false
        val elapsed = ShiftDurationCalculator.elapsedMinutes(shift, now) ?: return false
        return elapsed >= THRESHOLD_MINUTES
    }
}
