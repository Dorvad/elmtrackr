package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.Money

/**
 * Why a payment was rejected. Typed rather than a message string so the UI can
 * localise it and, where useful, show the offending amounts.
 */
sealed interface PaymentRejection {
    /** ElmTrackr does not convert currencies, so the codes must match exactly. */
    data class CurrencyMismatch(val paymentCurrency: String, val billingCurrency: String) : PaymentRejection

    /** Zero-value payments carry no information and are not recorded. */
    data object ZeroAmount : PaymentRejection

    /** Refunds and credit notes are out of scope; a payment is money in. */
    data object NegativeAmount : PaymentRejection

    /**
     * The payment would take total receipts past the billed total. There is no
     * customer-credit model to hold the excess, so it is blocked rather than
     * silently recorded.
     */
    data class Overpayment(
        val billedTotal: Money,
        val alreadyPaid: Money,
        val attempted: Money,
        val maximumAllowed: Money,
    ) : PaymentRejection

    /** The payment references a record that is not there or was cancelled. */
    data object BillingRecordUnavailable : PaymentRejection
}

sealed interface PaymentValidationResult {
    data object Valid : PaymentValidationResult
    data class Invalid(val reason: PaymentRejection) : PaymentValidationResult

    val isValid: Boolean get() = this is Valid
    val rejection: PaymentRejection? get() = (this as? Invalid)?.reason
}

/**
 * MVP payment rules, enforced before anything is written:
 *  - the payment currency must equal the billing record's currency;
 *  - zero and negative amounts are refused;
 *  - the running total must not exceed the billed total.
 *
 * Pure, so both the repository and any future form can share one answer.
 */
object ProjectPaymentValidator {

    /**
     * @param existingPayments payments already recorded against [billingRecord],
     * excluding the one being edited.
     */
    fun validate(
        amount: Money,
        billingRecord: ProjectBillingRecord?,
        existingPayments: List<ProjectPayment>,
    ): PaymentValidationResult {
        if (billingRecord == null || billingRecord.isCancelled) {
            return PaymentValidationResult.Invalid(PaymentRejection.BillingRecordUnavailable)
        }
        if (amount.currencyCode != billingRecord.currencyCode) {
            return PaymentValidationResult.Invalid(
                PaymentRejection.CurrencyMismatch(amount.currencyCode, billingRecord.currencyCode),
            )
        }
        if (amount.isNegative) {
            return PaymentValidationResult.Invalid(PaymentRejection.NegativeAmount)
        }
        if (amount.isZero) {
            return PaymentValidationResult.Invalid(PaymentRejection.ZeroAmount)
        }

        val currency = billingRecord.currencyCode
        val relevant = existingPayments.filter { it.billingRecordId == billingRecord.id }
        // A payment already stored in another currency would make the running
        // total meaningless; treat it as a mismatch rather than summing it.
        relevant.firstOrNull { it.currencyCode != currency }?.let { stray ->
            return PaymentValidationResult.Invalid(
                PaymentRejection.CurrencyMismatch(stray.currencyCode, currency),
            )
        }

        val alreadyPaid = Money.sum(relevant.map { it.amount }, currency)
        val billedTotal = billingRecord.billedTotal
        val remaining = (billedTotal - alreadyPaid).coerceAtLeastZero()
        if (amount > remaining) {
            return PaymentValidationResult.Invalid(
                PaymentRejection.Overpayment(
                    billedTotal = billedTotal,
                    alreadyPaid = alreadyPaid,
                    attempted = amount,
                    maximumAllowed = remaining,
                ),
            )
        }
        return PaymentValidationResult.Valid
    }

    /** How much may still be recorded against [billingRecord]. */
    fun remainingPayable(
        billingRecord: ProjectBillingRecord,
        existingPayments: List<ProjectPayment>,
    ): Money {
        val currency = billingRecord.currencyCode
        val paid = Money.sum(
            existingPayments
                .filter { it.billingRecordId == billingRecord.id && it.currencyCode == currency }
                .map { it.amount },
            currency,
        )
        return (billingRecord.billedTotal - paid).coerceAtLeastZero()
    }
}

/** Thrown by the repository when a write is attempted with an invalid payment. */
class ProjectPaymentRejectedException(val reason: PaymentRejection) :
    IllegalArgumentException("Payment rejected: $reason")
