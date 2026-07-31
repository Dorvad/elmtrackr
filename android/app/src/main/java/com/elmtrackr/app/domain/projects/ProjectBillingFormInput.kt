package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.money.MoneyPolicy
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The "record billing details" form.
 *
 * ElmTrackr does not issue invoices. This form records that the user asked for
 * money — on some date, due on another, under a reference they created
 * elsewhere. [externalReference] is theirs, never generated here.
 *
 * Amounts are strings because that is what the user typed; parsing and rounding
 * happen once, in [toFee], so a half-typed number never reaches a Money.
 */
data class ProjectBillingFormInput(
    val projectId: String,
    /** Pre-tax amount as typed. Defaults to the project's current base fee. */
    val baseAmount: String,
    val currencyCode: String,
    val taxLabel: String?,
    /** Percent as typed, e.g. "18" or "17.5". Blank means no tax. */
    val taxRatePercent: String,
    val taxMode: TaxMode,
    val billedOn: LocalDate,
    val dueOn: LocalDate? = null,
    val externalReference: String = "",
    val notes: String = "",
) {
    companion object {
        /**
         * Pre-fills the form from [project]'s current fee, so the default billed
         * amount is the client total the user already agreed.
         *
         * The tax mode, rate and label come from the project too: the point of the
         * default is that accepting it bills exactly what the project says. A
         * deliberate change is the user's to make.
         */
        fun from(
            project: Project,
            today: LocalDate,
            dueOn: LocalDate? = project.deadline,
        ): ProjectBillingFormInput = ProjectBillingFormInput(
            projectId = project.id,
            baseAmount = project.fee.base.amount.toPlainString(),
            currencyCode = project.currencyCode,
            taxLabel = project.fee.taxLabel,
            taxRatePercent = project.fee.taxRatePercent
                .takeIf { it.signum() > 0 }
                ?.stripTrailingZeros()
                ?.toPlainString()
                .orEmpty(),
            taxMode = project.fee.taxMode,
            billedOn = today,
            dueOn = dueOn,
        )
    }
}

/** Why a billing form could not be saved. Field-keyed so the form can mark inputs. */
enum class BillingFormError {
    AMOUNT_REQUIRED,
    AMOUNT_NOT_A_NUMBER,
    AMOUNT_NOT_POSITIVE,
    TAX_RATE_INVALID,
    CURRENCY_REQUIRED,
    DUE_BEFORE_BILLED,
}

sealed interface BillingFormResult {
    data class Valid(val fee: ProjectFee) : BillingFormResult
    data class Invalid(val errors: Map<String, BillingFormError>) : BillingFormResult
}

/**
 * Validates the billing form and builds the fee snapshot it will store.
 *
 * The snapshot is computed here, once, and written verbatim. Nothing recomputes a
 * billed amount from the project afterwards — that is the whole point of
 * snapshotting, and [ProjectFee] guarantees base + tax reconciles to the total
 * exactly.
 */
object ProjectBillingFormValidator {

    const val FIELD_AMOUNT = "amount"
    const val FIELD_TAX_RATE = "taxRate"
    const val FIELD_CURRENCY = "currency"
    const val FIELD_DUE_ON = "dueOn"

    private val MAX_TAX_RATE = BigDecimal("100")

    fun validate(input: ProjectBillingFormInput): BillingFormResult {
        val errors = mutableMapOf<String, BillingFormError>()

        val amountText = input.baseAmount.trim()
        val amount = when {
            amountText.isEmpty() -> {
                errors[FIELD_AMOUNT] = BillingFormError.AMOUNT_REQUIRED
                null
            }
            else -> amountText.toBigDecimalOrNull().also {
                if (it == null) errors[FIELD_AMOUNT] = BillingFormError.AMOUNT_NOT_A_NUMBER
            }
        }
        if (amount != null && amount.signum() <= 0) {
            errors[FIELD_AMOUNT] = BillingFormError.AMOUNT_NOT_POSITIVE
        }

        val rateText = input.taxRatePercent.trim()
        val rate = if (rateText.isEmpty()) {
            BigDecimal.ZERO
        } else {
            rateText.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it <= MAX_TAX_RATE }
                ?: run {
                    errors[FIELD_TAX_RATE] = BillingFormError.TAX_RATE_INVALID
                    null
                }
        }

        if (input.currencyCode.isBlank()) {
            errors[FIELD_CURRENCY] = BillingFormError.CURRENCY_REQUIRED
        }

        // Date-only comparison: a due date equal to the billing date is fine —
        // "due on receipt" is a normal term.
        if (input.dueOn != null && input.dueOn.isBefore(input.billedOn)) {
            errors[FIELD_DUE_ON] = BillingFormError.DUE_BEFORE_BILLED
        }

        if (errors.isNotEmpty() || amount == null || rate == null) {
            return BillingFormResult.Invalid(errors)
        }

        return BillingFormResult.Valid(
            ProjectFee.from(
                enteredAmount = amount.setScale(
                    com.elmtrackr.app.domain.money.CurrencyScales.digitsFor(input.currencyCode),
                    MoneyPolicy.DISPLAY_ROUNDING,
                ),
                currencyCode = input.currencyCode,
                taxMode = input.taxMode,
                taxRatePercent = rate,
                taxLabel = input.taxLabel,
            ),
        )
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
}
