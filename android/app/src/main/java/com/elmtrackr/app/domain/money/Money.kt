package com.elmtrackr.app.domain.money

import com.elmtrackr.app.domain.model.CurrencyCode
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Financial policy for the whole app: how money is calculated, rounded and
 * persisted.
 *
 * Deliberately explicit rather than relying on defaults. `BigDecimal.divide`
 * throws on a non-terminating result unless it is given a MathContext or a
 * scale, so every division in the money layer must go through
 * [CALCULATION_CONTEXT].
 */
object MoneyPolicy {

    /**
     * Precision used for intermediate arithmetic — 34 significant digits, the
     * same precision as IEEE 754 decimal128. Wide enough that repeating
     * decimals (a tax-inclusive divide by 1.17, say) carry far more digits than
     * any currency's display scale needs, so the single rounding step at the
     * end decides the result.
     */
    val CALCULATION_CONTEXT: MathContext = MathContext(34, RoundingMode.HALF_UP)

    /**
     * Rounding applied when an amount is reduced to its currency's display
     * scale. HALF_UP matches what MoneyFormatter already does on screen, so a
     * stored amount and a formatted amount never disagree.
     */
    val DISPLAY_ROUNDING: RoundingMode = RoundingMode.HALF_UP

    /** Fallback display scale for currency codes the platform does not know. */
    const val DEFAULT_FRACTION_DIGITS: Int = 2
}

/**
 * Display scale per ISO 4217 code.
 *
 * Resolved from the app's own [CurrencyCode] first, then from the platform's
 * currency table, so a project priced in a currency outside the app's picker
 * (or one with no minor unit, like JPY) still scales correctly. The app is not
 * limited to a single global currency, and never stores only a symbol.
 */
object CurrencyScales {

    fun digitsFor(currencyCode: String): Int {
        val code = currencyCode.trim().uppercase()
        CurrencyCode.entries.firstOrNull { it.name == code }?.let { return it.fractionDigits }
        return runCatching {
            java.util.Currency.getInstance(code).defaultFractionDigits.coerceAtLeast(0)
        }.getOrDefault(MoneyPolicy.DEFAULT_FRACTION_DIGITS)
    }

    /** True for a syntactically plausible ISO 4217 alphabetic code. */
    fun isValidCode(currencyCode: String?): Boolean {
        val code = currencyCode?.trim()?.uppercase() ?: return false
        return code.length == 3 && code.all { it in 'A'..'Z' }
    }

    fun normalize(currencyCode: String): String = currencyCode.trim().uppercase()
}

/** Thrown when two amounts in different currencies are combined. */
class CurrencyMismatchException(
    val left: String,
    val right: String,
) : IllegalArgumentException("Cannot combine $left and $right: ElmTrackr does not convert currencies")

/**
 * A decimal amount tagged with its currency.
 *
 * Backed by [BigDecimal] — never Double or Float. The amount is always held at
 * its currency's display scale, so two Money values that represent the same
 * amount are equal, and a sum of stored amounts never drifts from what the user
 * was shown.
 *
 * Money carries no exchange rate and refuses cross-currency arithmetic:
 * currency conversion is deliberately out of scope, and silently mixing
 * currencies would produce a meaningless total.
 */
class Money private constructor(
    val amount: BigDecimal,
    val currencyCode: String,
) : Comparable<Money> {

    /** Digits after the decimal point for this currency. */
    val fractionDigits: Int get() = amount.scale()

    val isZero: Boolean get() = amount.signum() == 0
    val isPositive: Boolean get() = amount.signum() > 0
    val isNegative: Boolean get() = amount.signum() < 0

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return of(amount.add(other.amount), currencyCode)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return of(amount.subtract(other.amount), currencyCode)
    }

    /** Multiplies by a plain factor (e.g. a tax rate) at calculation precision. */
    operator fun times(factor: BigDecimal): Money =
        of(amount.multiply(factor, MoneyPolicy.CALCULATION_CONTEXT), currencyCode)

    /** Negative amounts clamped to zero — used for outstanding balances. */
    fun coerceAtLeastZero(): Money = if (isNegative) zero(currencyCode) else this

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    private fun requireSameCurrency(other: Money) {
        if (currencyCode != other.currencyCode) {
            throw CurrencyMismatchException(currencyCode, other.currencyCode)
        }
    }

    /**
     * Canonical persistence form: a plain decimal string, never scientific
     * notation, so a stored value round-trips byte for byte.
     */
    fun toPersistedString(): String = amount.toPlainString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return currencyCode == other.currencyCode && amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int = 31 * currencyCode.hashCode() + amount.stripTrailingZeros().hashCode()

    override fun toString(): String = "$currencyCode ${amount.toPlainString()}"

    companion object {

        /**
         * Builds a Money at the currency's display scale, rounding with
         * [MoneyPolicy.DISPLAY_ROUNDING].
         */
        fun of(amount: BigDecimal, currencyCode: String): Money {
            val code = CurrencyScales.normalize(currencyCode)
            val scaled = amount.setScale(CurrencyScales.digitsFor(code), MoneyPolicy.DISPLAY_ROUNDING)
            return Money(scaled, code)
        }

        fun of(amount: String, currencyCode: String): Money =
            of(BigDecimal(amount.trim()), currencyCode)

        fun zero(currencyCode: String): Money = of(BigDecimal.ZERO, currencyCode)

        /**
         * Reads a persisted amount, falling back to zero for a blank or
         * unparseable value rather than throwing on every read of the row.
         */
        fun fromPersisted(amount: String?, currencyCode: String): Money {
            val raw = amount?.trim()
            if (raw.isNullOrEmpty()) return zero(currencyCode)
            return runCatching { of(BigDecimal(raw), currencyCode) }.getOrElse { zero(currencyCode) }
        }

        /** Sums amounts that must all share [currencyCode]. */
        fun sum(amounts: List<Money>, currencyCode: String): Money =
            amounts.fold(zero(currencyCode)) { acc, money -> acc + money }
    }
}
