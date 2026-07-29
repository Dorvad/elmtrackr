package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.Money
import java.time.LocalDate

/**
 * Where a project stands with the client's money.
 *
 * Never stored. It is derived from the project's billing records, its payments
 * and today's date, so it cannot go stale or be edited into a state the numbers
 * do not support.
 */
enum class ProjectBillingStatus {
    /** No active billing record exists. */
    NOT_BILLED,

    /** Billed, nothing received yet, not past due. */
    BILLED,

    /** Something received, but less than the billed total. */
    PARTIALLY_PAID,

    /** Recorded payments equal the billed total. */
    PAID,

    /** Billed, still outstanding, and the due date has passed. */
    OVERDUE,
}

/**
 * The full billing picture for a project. Callers get both the single [status]
 * and the underlying figures, so a screen can render "Partially paid · overdue"
 * without the enum having to carry two states at once.
 */
data class ProjectBillingState(
    val currencyCode: String,
    val status: ProjectBillingStatus,
    /** Null when nothing has been billed. */
    val billedTotal: Money?,
    val paid: Money,
    /** Billed total minus payments, never negative. */
    val outstanding: Money,
    /** Payments beyond the billed total. Zero unless data predates validation. */
    val credit: Money,
    val isOverdue: Boolean,
    val dueOn: LocalDate?,
    /** Negative once the due date has passed. Null with no due date. */
    val daysUntilDue: Long?,
    val activeBillingRecordId: String?,
)

/**
 * Derives [ProjectBillingStatus] from records, payments, balances and dates.
 *
 * Precedence, where several conditions hold at once:
 *  - PAID always wins. A late-but-settled project is settled, not overdue.
 *  - OVERDUE outranks BILLED and PARTIALLY_PAID, because it is the state the
 *    user needs to act on. The partial-payment detail stays available through
 *    [ProjectBillingState.paid] and [ProjectBillingState.outstanding].
 *  - Due *today* is not overdue.
 *  - Cancelled records are ignored entirely, so they can never produce OVERDUE.
 *
 * [today] must be computed in the user's work timezone (WorkTimezone), not the
 * device zone, or a project flips overdue a day early while travelling.
 */
object ProjectBillingStatusResolver {

    fun resolve(
        currencyCode: String,
        billingRecords: List<ProjectBillingRecord>,
        payments: List<ProjectPayment>,
        today: LocalDate,
    ): ProjectBillingState {
        val active = activeRecords(billingRecords)

        if (active.isEmpty()) {
            return ProjectBillingState(
                currencyCode = currencyCode,
                status = ProjectBillingStatus.NOT_BILLED,
                billedTotal = null,
                paid = Money.zero(currencyCode),
                outstanding = Money.zero(currencyCode),
                credit = Money.zero(currencyCode),
                isOverdue = false,
                dueOn = null,
                daysUntilDue = null,
                activeBillingRecordId = null,
            )
        }

        val activeIds = active.map { it.id }.toSet()
        val billedTotal = Money.sum(active.map { it.billedTotal }, currencyCode)
        // Payments against a cancelled record do not offset what is still owed.
        val paid = Money.sum(
            payments.filter { it.billingRecordId in activeIds }.map { it.amount },
            currencyCode,
        )

        val difference = billedTotal - paid
        val outstanding = difference.coerceAtLeastZero()
        val credit = (paid - billedTotal).coerceAtLeastZero()

        // Earliest due date across active records: the first one to lapse makes
        // the project overdue.
        val dueOn = active.mapNotNull { it.dueOn }.minOrNull()
        val pastDue = dueOn != null && today.isAfter(dueOn)
        val isOverdue = pastDue && outstanding.isPositive

        val status = when {
            !outstanding.isPositive -> ProjectBillingStatus.PAID
            isOverdue -> ProjectBillingStatus.OVERDUE
            paid.isPositive -> ProjectBillingStatus.PARTIALLY_PAID
            else -> ProjectBillingStatus.BILLED
        }

        return ProjectBillingState(
            currencyCode = currencyCode,
            status = status,
            billedTotal = billedTotal,
            paid = paid,
            outstanding = outstanding,
            credit = credit,
            isOverdue = isOverdue,
            dueOn = dueOn,
            daysUntilDue = dueOn?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) },
            activeBillingRecordId = active.minByOrNull { it.billedOn }?.id,
        )
    }

    /** Non-cancelled records, oldest first. */
    fun activeRecords(billingRecords: List<ProjectBillingRecord>): List<ProjectBillingRecord> =
        billingRecords.filter { it.isActive }.sortedBy { it.billedOn }
}
