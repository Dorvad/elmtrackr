package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * How much has been worked, on one basis.
 *
 * The app had three answers to "how long today". The widget and the watch counted
 * completed shifts **net of break** and added the running shift **gross**; the
 * Shifts screen's week cards did the same; the dashboard used a third arrangement
 * behind a comment claiming parity with the other two.
 *
 * Mixing the bases has a visible consequence: a running shift with a break
 * recorded on it counts more while it is running than it does the moment it ends,
 * so the ring goes *backwards* at clock-out. It only shows on a shift edited to
 * add a break mid-shift, which is why it survived — but it is the kind of thing a
 * user notices and cannot explain.
 *
 * One rule here: **every shift counts net of its break, running or not.**
 */
object TodayMinutes {

    /**
     * A running shift's elapsed time, less any break already recorded on it.
     *
     * [ShiftDurationCalculator.elapsedMinutes] is deliberately gross — it answers
     * "how long since clock-in", which is what a live timer displays. This answers
     * "how much of that is paid time", which is what a day total needs.
     */
    fun activeNetMinutes(shift: Shift, now: Instant = Instant.now()): Int {
        if (!shift.isActive) return 0
        val elapsed = ShiftDurationCalculator.elapsedMinutes(shift, now) ?: return 0
        return (elapsed - shift.breakMinutes).coerceAtLeast(0)
    }

    /** Net minutes for one shift, whether it is running or finished. */
    fun netMinutes(shift: Shift, now: Instant = Instant.now()): Int =
        if (shift.isActive) {
            activeNetMinutes(shift, now)
        } else {
            ShiftDurationCalculator.netMinutes(shift) ?: 0
        }

    /**
     * Minutes worked on [date], attributed by the shift's **start** date.
     *
     * By start date, not split across midnight, and deliberately: a night shift
     * belongs to the day it began for every other purpose in this app — the daily
     * overtime standard treats it as one workday, and the shift list files it under
     * its start date. Splitting it here alone would make the day ring disagree with
     * the row above it. It does mean hours worked after midnight show against
     * yesterday; that is the same convention, not an oversight.
     */
    fun forDay(
        shifts: List<Shift>,
        zone: ZoneId,
        date: LocalDate,
        now: Instant = Instant.now(),
    ): Int = shifts
        .filter { it.startTime.atZone(zone).toLocalDate() == date }
        .sumOf { netMinutes(it, now) }
}
