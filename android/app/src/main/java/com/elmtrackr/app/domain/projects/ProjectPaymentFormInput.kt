package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.PaymentMethod
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyPolicy
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The "record payment" form.
 *
 * The amount is a string because that is what the user typed. It is parsed once,
 * here, and then handed to [ProjectPaymentValidator] — which owns every rule
 * about currency, sign and overpayment — so the form and the repository can never
 * disagree about whether a payment is allowed.
 */
data class ProjectPaymentFormInput(
    val paymentId: String? = null,
    val projectId: String,
    val billingRecordId: String,
    val amount: String,
    val currencyCode: String,
    val paidOn: LocalDate,
    val method: PaymentMethod? = null,
    val externalReference: String = "",
    val notes: String = "",
) {
    companion object {
        /**
         * Pre-fills from what is still owed, so the common case — the client paid
         * the balance — is one tap. The user can type less for a partial payment.
         */
        fun forRecord(
            record: ProjectBillingRecord,
            payments: List<ProjectPayment>,
            today: LocalDate,
        ): ProjectPaymentFormInput = ProjectPaymentFormInput(
            projectId = record.projectId,
            billingRecordId = record.id,
            amount = ProjectPaymentValidator.remainingPayable(record, payments)
                .amount
                .toPlainString(),
            currencyCode = record.currencyCode,
            paidOn = today,
        )

        /** Loads an existing payment back into the form for editing. */
        fun from(payment: ProjectPayment): ProjectPaymentFormInput = ProjectPaymentFormInput(
            paymentId = payment.id,
            projectId = payment.projectId,
            billingRecordId = payment.billingRecordId,
            amount = payment.amount.amount.toPlainString(),
            currencyCode = payment.currencyCode,
            paidOn = payment.paidOn,
            method = PaymentMethod.fromPersisted(payment.method),
            externalReference = payment.externalReference.orEmpty(),
            notes = payment.notes.orEmpty(),
        )
    }
}

sealed interface PaymentFormResult {
    data class Valid(val amount: Money) : PaymentFormResult

    /** The typed amount is not a usable number at all. */
    data object NotANumber : PaymentFormResult

    /** Parsed fine, but broke a payment rule. Carries the typed reason. */
    data class Rejected(val reason: PaymentRejection) : PaymentFormResult
}

object ProjectPaymentFormValidator {

    /**
     * @param existingPayments every payment for the project. The one being edited
     * is excluded by id, so re-saving a payment unchanged does not read as an
     * overpayment against itself.
     */
    fun validate(
        input: ProjectPaymentFormInput,
        billingRecord: ProjectBillingRecord?,
        existingPayments: List<ProjectPayment>,
    ): PaymentFormResult {
        val parsed = runCatching { BigDecimal(input.amount.trim()) }.getOrNull()
            ?: return PaymentFormResult.NotANumber

        val amount = Money.of(
            parsed.setScale(
                com.elmtrackr.app.domain.money.CurrencyScales.digitsFor(input.currencyCode),
                MoneyPolicy.DISPLAY_ROUNDING,
            ),
            input.currencyCode,
        )

        val others = existingPayments.filter { it.id != input.paymentId }
        return when (val result = ProjectPaymentValidator.validate(amount, billingRecord, others)) {
            is PaymentValidationResult.Valid -> PaymentFormResult.Valid(amount)
            is PaymentValidationResult.Invalid -> PaymentFormResult.Rejected(result.reason)
        }
    }
}
