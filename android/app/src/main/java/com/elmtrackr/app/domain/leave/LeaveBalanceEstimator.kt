package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceSnapshot
import com.elmtrackr.app.domain.model.LeaveBalanceUnit

/**
 * The estimated balance for one workplace and one kind of leave.
 *
 * Three numbers are kept apart on purpose, and the UI shows all three: what the
 * payslip said, what has been reported since, and the subtraction of the two.
 * Collapsing them into a single "balance" would be claiming a certainty the app
 * does not have — it knows only what the user entered and what they reported.
 */
data class LeaveBalanceEstimate(
    val workplaceId: String,
    val balanceType: AbsenceType,
    /** The most recent balance the user entered, or null if they never have. */
    val official: LeaveBalanceSnapshot?,
    val unitsUsedSinceSnapshot: Double,
    /**
     * Official minus reported usage. Null when there is no official balance to
     * subtract from: "unknown" is not the same as zero, and showing 0 days
     * remaining to someone who simply has not entered a payslip yet would be a
     * lie in the alarming direction.
     */
    val estimatedBalance: Double?,
    val unit: LeaveBalanceUnit,
    /**
     * Reported leave that could not be counted because it was recorded in the
     * other unit and the workplace has no reliable standard day to convert with.
     * Surfaced rather than guessed: converting hours to days at an invented
     * length would silently move the balance.
     */
    val unconvertibleCount: Int,
) {
    val isNegative: Boolean get() = (estimatedBalance ?: 0.0) < 0.0
    val hasOfficialBalance: Boolean get() = official != null
}

object LeaveBalanceEstimator {

    /**
     * [allocations] must already be scoped to this workplace and leave type, and
     * must exclude deleted rows. Scoping is the caller's job because the queries
     * that fetch them are indexed on exactly those columns; re-filtering here
     * would only hide a mistake in the query.
     *
     * Accrual is deliberately not added. A balance grows between payslips, and
     * the app knows neither the rate nor the ceiling reliably enough for a
     * part-time or irregular worker, so it reports the last official figure minus
     * what has been used and says as much on screen.
     */
    fun estimate(
        workplaceId: String,
        balanceType: AbsenceType,
        latestSnapshot: LeaveBalanceSnapshot?,
        allocations: List<AbsenceAllocation>,
        standardDayMinutes: Int?,
    ): LeaveBalanceEstimate {
        val unit = latestSnapshot?.unit ?: LeaveBalanceUnit.DAYS

        // Only leave taken *after* the balance date counts. A payslip dated the
        // 31st already accounts for everything up to and including that day, so
        // subtracting those again would double-count them.
        val countable = latestSnapshot?.let { snapshot ->
            allocations.filter { it.affectedDate.isAfter(snapshot.asOfDate) }
        } ?: allocations

        var used = 0.0
        var unconvertible = 0
        for (allocation in countable) {
            val converted = convert(
                units = allocation.entitlementUnits,
                from = allocation.unit,
                to = unit,
                standardDayMinutes = standardDayMinutes,
            )
            if (converted == null) unconvertible++ else used += converted
        }

        return LeaveBalanceEstimate(
            workplaceId = workplaceId,
            balanceType = balanceType,
            official = latestSnapshot,
            unitsUsedSinceSnapshot = used,
            // A negative result is shown, not clamped. It is real information: the
            // payslip balance is out of date, or the workplace allowed leave in
            // advance. Clamping to zero hides both.
            estimatedBalance = latestSnapshot?.let { it.balance - used },
            unit = unit,
            unconvertibleCount = unconvertible,
        )
    }

    /**
     * Null when the two units differ and there is no trustworthy standard day to
     * convert between them.
     */
    fun convert(
        units: Double,
        from: LeaveBalanceUnit,
        to: LeaveBalanceUnit,
        standardDayMinutes: Int?,
    ): Double? {
        if (from == to) return units
        val dayMinutes = standardDayMinutes?.takeIf { it > 0 } ?: return null
        val dayHours = dayMinutes / 60.0
        return when {
            from == LeaveBalanceUnit.HOURS && to == LeaveBalanceUnit.DAYS -> units / dayHours
            from == LeaveBalanceUnit.DAYS && to == LeaveBalanceUnit.HOURS -> units * dayHours
            else -> null
        }
    }

    /**
     * The snapshot that governs today: the latest by balance date, and among
     * snapshots sharing a date the one entered last, so correcting a mistyped
     * balance supersedes the earlier row without erasing it.
     */
    fun latestSnapshot(snapshots: List<LeaveBalanceSnapshot>): LeaveBalanceSnapshot? =
        snapshots.maxWithOrNull(compareBy({ it.asOfDate }, { it.createdAt }))
}
