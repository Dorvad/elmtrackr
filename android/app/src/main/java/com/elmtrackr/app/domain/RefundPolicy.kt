package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.Shift
import java.time.ZoneId

/**
 * Travel-refund helpers.
 *
 * Reimbursement is user-driven: every shift can have ride claims added from
 * shift edit when the travel-refunds feature is enabled, including shifts that
 * are still running, and each direction may hold several rides. There is no
 * automatic eligibility detection.
 */
object RefundPolicy {

    data class Eligibility(val eligible: Boolean, val reasons: List<String>)

    /** Eligibility for the ride TO work. Available on every shift. */
    fun checkToWorkEligibility(
        shift: Shift,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Eligibility = Eligibility(true, emptyList())

    /** Eligibility for the ride FROM work. Available on every shift. */
    fun checkFromWorkEligibility(
        shift: Shift,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Eligibility = Eligibility(true, emptyList())

    fun isEligibleForRefund(shift: Shift, zone: ZoneId = ZoneId.systemDefault()): Boolean = true

    /** True when the user asked to be reminded later about a from-work ride. */
    fun isUnresolved(shift: Shift, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        shift.isCompleted && shift.refundAction == RefundAction.REMIND_LATER

    fun countUnresolved(shifts: List<Shift>, zone: ZoneId = ZoneId.systemDefault()): Int =
        shifts.count { isUnresolved(it, zone) }
}
