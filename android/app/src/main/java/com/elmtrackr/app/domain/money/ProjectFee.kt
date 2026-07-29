package com.elmtrackr.app.domain.money

import java.math.BigDecimal

/**
 * The three amounts a project fee always resolves to, plus the inputs that
 * produced them.
 *
 * Wording note for any UI built on this: `base` is "Your fee before tax", not
 * "you keep" — income tax, expenses and other deductions still apply to it.
 *
 * Invariant, guaranteed for every instance: `base + tax == clientTotal` at the
 * currency's display scale. Both [from] and [fromPersisted] establish it, so a
 * screen can render the three numbers straight from here and they will always
 * add up.
 */
class ProjectFee private constructor(
    val base: Money,
    val tax: Money,
    val clientTotal: Money,
    val taxMode: TaxMode,
    val taxRatePercent: BigDecimal,
    val taxLabel: String?,
) {
    val currencyCode: String get() = base.currencyCode

    /** The amount the user typed for this mode, i.e. what an edit form shows. */
    val enteredAmount: Money get() = if (taxMode == TaxMode.INCLUSIVE) clientTotal else base

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProjectFee) return false
        return base == other.base &&
            tax == other.tax &&
            clientTotal == other.clientTotal &&
            taxMode == other.taxMode &&
            taxRatePercent.compareTo(other.taxRatePercent) == 0 &&
            taxLabel == other.taxLabel
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + tax.hashCode()
        result = 31 * result + clientTotal.hashCode()
        result = 31 * result + taxMode.hashCode()
        result = 31 * result + taxRatePercent.stripTrailingZeros().hashCode()
        result = 31 * result + (taxLabel?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "ProjectFee(base=$base, tax=$tax, clientTotal=$clientTotal, mode=$taxMode, rate=$taxRatePercent)"

    companion object {

        private val HUNDRED = BigDecimal("100")

        /**
         * The one place project tax is calculated. Everything that needs a
         * base / tax / total triple goes through here.
         *
         * NONE
         *   base == clientTotal == entered, tax zero. The rate is ignored and
         *   normalised to zero so a disabled tax cannot leave a stale rate
         *   behind.
         *
         * EXCLUSIVE — the user entered their fee before tax
         *   base  = round(entered)
         *   tax   = round(base × rate / 100)
         *   total = base + tax          (exact by construction)
         *
         * INCLUSIVE — the user entered the client total
         *   total = round(entered)      (preserved exactly as typed)
         *   base  = round(total × 100 / (100 + rate))   at calculation precision
         *   tax   = total − base        (residual, so the three always reconcile)
         *
         * Taking tax as the residual in the inclusive case is what makes
         * repeating decimals safe: rounding base and tax independently can leave
         * their sum a minor unit away from the total the user typed.
         */
        fun from(
            enteredAmount: BigDecimal,
            currencyCode: String,
            taxMode: TaxMode,
            taxRatePercent: BigDecimal,
            taxLabel: String? = null,
        ): ProjectFee {
            val entered = Money.of(enteredAmount, currencyCode)
            val rate = normalizeRate(taxMode, taxRatePercent)
            val label = taxLabel?.trim()?.takeIf { it.isNotEmpty() && taxMode.isEnabled }

            if (rate.signum() == 0 || !taxMode.isEnabled) {
                // A zero rate behaves exactly like a disabled tax: no tax line,
                // and the client is charged the fee as entered.
                return ProjectFee(
                    base = entered,
                    tax = Money.zero(entered.currencyCode),
                    clientTotal = entered,
                    taxMode = if (rate.signum() == 0) TaxMode.NONE else taxMode,
                    taxRatePercent = BigDecimal.ZERO,
                    taxLabel = if (rate.signum() == 0) null else label,
                )
            }

            return when (taxMode) {
                TaxMode.EXCLUSIVE -> {
                    val tax = entered * rate.divide(HUNDRED, MoneyPolicy.CALCULATION_CONTEXT)
                    ProjectFee(entered, tax, entered + tax, taxMode, rate, label)
                }
                TaxMode.INCLUSIVE -> {
                    val divisor = HUNDRED.add(rate)
                    val base = Money.of(
                        entered.amount
                            .multiply(HUNDRED, MoneyPolicy.CALCULATION_CONTEXT)
                            .divide(divisor, MoneyPolicy.CALCULATION_CONTEXT),
                        entered.currencyCode,
                    )
                    ProjectFee(base, entered - base, entered, taxMode, rate, label)
                }
                TaxMode.NONE -> error("handled above")
            }
        }

        /**
         * Rebuilds a fee from three separately stored amounts.
         *
         * Storage is authoritative — a rate change must not silently restate an
         * already-billed fee — but the invariant is re-established rather than
         * asserted: if a persisted triple does not reconcile (a partial write, a
         * row from an older format, a hand-edited backup), tax is recomputed as
         * total − base instead of throwing on every read of the row.
         */
        fun fromPersisted(
            base: Money,
            tax: Money,
            clientTotal: Money,
            taxMode: TaxMode,
            taxRatePercent: BigDecimal,
            taxLabel: String?,
        ): ProjectFee {
            val reconciled = if ((base + tax) == clientTotal) tax else clientTotal - base
            return ProjectFee(
                base = base,
                tax = reconciled,
                clientTotal = clientTotal,
                taxMode = taxMode,
                taxRatePercent = taxRatePercent,
                taxLabel = taxLabel?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        fun zero(currencyCode: String): ProjectFee = from(
            enteredAmount = BigDecimal.ZERO,
            currencyCode = currencyCode,
            taxMode = TaxMode.NONE,
            taxRatePercent = BigDecimal.ZERO,
        )

        /** Clamps a rate to 0–100 %, and to zero when tax is off. */
        internal fun normalizeRate(taxMode: TaxMode, taxRatePercent: BigDecimal): BigDecimal = when {
            !taxMode.isEnabled -> BigDecimal.ZERO
            taxRatePercent.signum() <= 0 -> BigDecimal.ZERO
            taxRatePercent > HUNDRED -> HUNDRED
            else -> taxRatePercent
        }
    }
}
