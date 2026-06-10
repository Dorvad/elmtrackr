package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import java.time.ZoneId

/**
 * Travel-refund eligibility rules.
 *
 * All checks use LOCAL time (not UTC), matching the web app's use of
 * new Date(iso).getHours() / getDay() which reads the JS engine's local timezone.
 * Pass an explicit [zone] in tests to keep them timezone-independent.
 */
object RefundPolicy {

    data class Eligibility(val eligible: Boolean, val reasons: List<String>)

    /** Eligibility for the ride TO work, based on shift start time. */
    fun checkToWorkEligibility(
        shift: Shift,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Eligibility {
        val start = shift.startTime.atZone(zone)
        val hour = start.hour
        val minute = start.minute
        val jsDay = start.dayOfWeek.toJsDay()
        val reasons = mutableListOf<String>()

        if ((hour == 23 && minute >= 30) || hour < 10) reasons += "Late night / early morning start"
        if (jsDay == 5 && hour >= 17) reasons += "Friday night shift"
        if (jsDay == 6) reasons += "Saturday shift"
        if (shift.isSpecialDay) reasons += "Holiday shift"

        return Eligibility(reasons.isNotEmpty(), reasons)
    }

    /** Eligibility for the ride FROM work, based on shift end time. */
    fun checkFromWorkEligibility(
        shift: Shift,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Eligibility {
        val endInstant = shift.endTime ?: return Eligibility(false, emptyList())
        val end = endInstant.atZone(zone)
        val hour = end.hour
        val minute = end.minute
        val jsDay = end.dayOfWeek.toJsDay()
        val reasons = mutableListOf<String>()

        if ((hour == 23 && minute >= 30) || hour < 10) reasons += "Late night / early morning shift"
        if (jsDay == 5 && hour >= 17) reasons += "Friday night shift"
        if (jsDay == 6) reasons += "Saturday shift"
        if (shift.isSpecialDay) reasons += "Holiday shift"

        return Eligibility(reasons.isNotEmpty(), reasons)
    }

    fun isEligibleForRefund(shift: Shift, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        checkFromWorkEligibility(shift, zone).eligible

    /** True if the shift is eligible but the user has not yet acted on the refund. */
    fun isUnresolved(shift: Shift, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        shift.isCompleted && isEligibleForRefund(shift, zone) && shift.refundAction == null

    fun countUnresolved(shifts: List<Shift>, zone: ZoneId = ZoneId.systemDefault()): Int =
        shifts.count { isUnresolved(it, zone) }
}
