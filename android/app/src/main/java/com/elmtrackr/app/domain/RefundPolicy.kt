package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.Shift
import java.time.ZoneId

/**
 * Travel-refund helpers.
 *
 * Reimbursement is user-driven: any completed shift can have ride claims added
 * from shift edit when the travel-refunds feature is enabled. There is no
 * automatic time-based eligibility detection.
 */
object RefundPolicy {

    data class Eligibility(val eligible: Boolean, val reasons: List<String>)

    /** Eligibility for the ride TO work. Available on any completed shift. */
    fun checkToWorkEligibility(
        shift: Shift,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Eligibility {
        if (!shift.isCompleted) return Eligibility(false, emptyList())
        return Eligibility(true, emptyList())
    }

    /** Eligibility for the ride FROM work. Available on any completed shift. */
    fun checkFromWorkEligibility(
        shift: Shift,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Eligibility {
        if (!shift.isCompleted) return Eligibility(false, emptyList())
        return Eligibility(true, emptyList())
    }

    fun isEligibleForRefund(shift: Shift, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        shift.isCompleted

    /** True when the user asked to be reminded later about a from-work ride. */
    fun isUnresolved(shift: Shift, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        shift.isCompleted && shift.refundAction == RefundAction.REMIND_LATER

    fun countUnresolved(shifts: List<Shift>, zone: ZoneId = ZoneId.systemDefault()): Int =
        shifts.count { isUnresolved(it, zone) }
}
