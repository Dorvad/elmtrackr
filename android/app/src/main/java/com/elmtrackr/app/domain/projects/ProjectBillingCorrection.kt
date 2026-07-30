package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment

/**
 * What a user may do to a billing record they got wrong.
 *
 * The rule the whole file exists to enforce: **a billing record with payments
 * against it is never destructively replaced.** Payment history is the record of
 * money that actually moved; rewriting the bill it settled would leave payments
 * pointing at amounts nobody ever asked for.
 *
 * So there are two paths:
 *  - Nothing paid yet — the record can be cancelled and a corrected one
 *    recorded. The cancelled record stays visible, it is not deleted.
 *  - Something paid — correction is refused, and the caller explains why. The
 *    user's options are to record another payment or to void the payments first,
 *    both of which they do deliberately.
 */
object ProjectBillingCorrection {

    /** Why a correction is not offered. */
    enum class Block {
        /** The record already has payments recorded against it. */
        HAS_PAYMENTS,

        /** Already withdrawn; there is nothing left to correct. */
        ALREADY_CANCELLED,
    }

    sealed interface Verdict {
        /** Safe to cancel and re-record. */
        data object Allowed : Verdict

        data class Blocked(val reason: Block) : Verdict

        val isAllowed: Boolean get() = this is Allowed
        val blockedReason: Block? get() = (this as? Blocked)?.reason
    }

    /**
     * Whether [record] can be cancelled so a corrected one can be recorded.
     *
     * @param payments every payment for the project; only those referencing
     * [record] are considered, so a payment against a different (or already
     * cancelled) record does not block an unrelated correction.
     */
    fun canCancel(
        record: ProjectBillingRecord,
        payments: List<ProjectPayment>,
    ): Verdict = when {
        record.isCancelled -> Verdict.Blocked(Block.ALREADY_CANCELLED)
        paymentsFor(record, payments).isNotEmpty() -> Verdict.Blocked(Block.HAS_PAYMENTS)
        else -> Verdict.Allowed
    }

    /**
     * Whether [record] can be permanently deleted.
     *
     * Deliberately the same rule as [canCancel]. A record with payments is
     * history; cancelling keeps it auditable, deleting would not.
     */
    fun canDelete(
        record: ProjectBillingRecord,
        payments: List<ProjectPayment>,
    ): Verdict = canCancel(record, payments)

    /** Payments recorded against [record]. */
    fun paymentsFor(
        record: ProjectBillingRecord,
        payments: List<ProjectPayment>,
    ): List<ProjectPayment> = payments.filter { it.billingRecordId == record.id }

    /**
     * True when a project may be billed: it has no active billing record yet.
     *
     * The MVP surfaces one active record at a time. The schema does not enforce
     * that — several records already sum correctly in
     * [ProjectBillingStatusResolver] — so lifting this is a UI change, not a
     * migration.
     */
    fun canRecordBilling(billingRecords: List<ProjectBillingRecord>): Boolean =
        ProjectBillingStatusResolver.activeRecords(billingRecords).isEmpty()
}
