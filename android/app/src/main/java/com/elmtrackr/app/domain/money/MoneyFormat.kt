package com.elmtrackr.app.domain.money

import com.elmtrackr.app.domain.text.BidiText
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Locale-aware money rendering.
 *
 * Formatting is delegated to [NumberFormat.getCurrencyInstance], which places
 * the symbol, groups digits and picks the decimal separator according to the
 * locale. Nothing here concatenates a currency symbol by hand — that is what
 * puts "₪" on the wrong side of the number in Hebrew.
 */
object MoneyFormat {

    /**
     * @param locale the UI locale, so the same amount reads correctly in
     * English and in Hebrew.
     */
    fun format(money: Money, locale: Locale = Locale.getDefault()): String {
        val currency = runCatching { Currency.getInstance(money.currencyCode) }.getOrNull()
            ?: return formatWithoutCurrencyData(money, locale)
        return currencyFormatter(currency, money.fractionDigits, locale).format(money.amount)
    }

    /**
     * The same rendering for an amount held as a `Double`.
     *
     * Exists so [com.elmtrackr.app.domain.MoneyFormatter] — 37 call sites that pass a
     * `Double` from the payroll layer — can produce byte-identical output without each
     * one allocating a [Money] and its [java.math.BigDecimal] on every recomposition.
     * The scale comes from [CurrencyScales], the same source [Money.of] uses, so the two
     * entry points cannot disagree about how many digits a currency has.
     *
     * A `Double` is the wrong type to hold money in and the right type to have arrived
     * from the pay engine, which computes in `Double` throughout. Nothing here does
     * arithmetic on it; it is formatted and discarded.
     */
    fun formatAmount(
        amount: Double,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val code = CurrencyScales.normalize(currencyCode)
        val digits = CurrencyScales.digitsFor(code)
        val currency = runCatching { Currency.getInstance(code) }.getOrNull()
            ?: return String.format(
                locale,
                "%s %s",
                BidiText.isolate(code),
                numberFormatter(digits, locale).format(amount),
            )
        return currencyFormatter(currency, digits, locale).format(amount)
    }

    private fun currencyFormatter(
        currency: Currency,
        digits: Int,
        locale: Locale,
    ): NumberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = digits
        maximumFractionDigits = digits
        roundingMode = MoneyPolicy.DISPLAY_ROUNDING
    }

    private fun numberFormatter(digits: Int, locale: Locale): NumberFormat =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = MoneyPolicy.DISPLAY_ROUNDING
        }

    /**
     * A code the platform has no currency data for. The number is still
     * formatted for the locale; the code is placed by the locale's own list
     * pattern rather than glued on.
     */
    private fun formatWithoutCurrencyData(money: Money, locale: Locale): String {
        val number = formatNumber(money, locale)
        return String.format(locale, "%s %s", BidiText.isolate(money.currencyCode), number)
    }

    /**
     * The amount as a bare localised number — grouped, with its currency's
     * fraction digits, and with no symbol and no code.
     *
     * This exists so a caller that needs to name the currency itself can do so
     * through a translatable format string, where the translator controls the
     * order, instead of gluing a code onto a formatted amount that may already
     * carry direction marks. [format] remains the right call whenever the
     * currency is not in question.
     */
    fun formatNumber(money: Money, locale: Locale = Locale.getDefault()): String =
        numberFormatter(money.fractionDigits, locale).format(money.amount)

    /**
     * True when this amount's symbol does not identify its currency on its own.
     *
     * CLDR disambiguates within a locale — in both English and Hebrew, USD
     * renders as "$" while CAD renders as "CA$" and AUD as "A$" — so a symbol
     * collision between two currencies is not the failure mode here. What
     * remains is a reader looking at "$1,200.00" in a list that also holds
     * another currency, with no way to tell from the glyph which one this row
     * is. Any amount whose symbol is not simply its own ISO code is therefore
     * treated as needing the code spelled out when it appears alongside a
     * different currency.
     *
     * A currency the platform has no data for already renders with its code and
     * needs no help.
     */
    fun symbolNeedsCode(money: Money, locale: Locale = Locale.getDefault()): Boolean {
        val currency = runCatching { Currency.getInstance(money.currencyCode) }.getOrNull()
            ?: return false
        return currency.getSymbol(locale) != currency.currencyCode
    }

    /**
     * A rate held as a fraction rendered as a localised percentage: 0.5 becomes
     * "50%", 1.25 becomes "125%".
     *
     * Separate from [formatTaxRate] because the inputs differ — that one takes a
     * figure already expressed in percent, as tax rates are stored — and because a
     * pay multiplier can exceed 100%, which reads as an error in a tax field.
     */
    fun formatRate(fraction: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getPercentInstance(locale).apply {
            // Whole percents for the common cases (0%, 50%, 100%, 150%), one decimal
            // where a rate genuinely needs it rather than "62.5%" shown as "63%".
            maximumFractionDigits = if (fraction * 1000 % 10 == 0.0) 0 else 1
            roundingMode = RoundingMode.HALF_UP
        }.format(fraction)

    /** Percentage rendered for the locale, e.g. "18%" or "17.5%". */
    fun formatTaxRate(ratePercent: java.math.BigDecimal, locale: Locale = Locale.getDefault()): String {
        val normalized = ratePercent.stripTrailingZeros()
        val scale = normalized.scale().coerceIn(0, 4)
        val formatter = NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = scale
            maximumFractionDigits = scale
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(normalized.movePointLeft(2))
    }

    /**
     * The hour and minute parts of a duration, each as a localised numeral.
     *
     * The unit words are deliberately not here. A duration reads as "12h 30m"
     * in English and wants Hebrew abbreviations in Hebrew, and only the
     * resource layer knows which language is on screen. Callers pair these
     * numerals with a translated pattern; see `formattedMinutes` in the project
     * UI. Each numeral is isolated so that two of them in one Hebrew phrase
     * cannot swap places.
     */
    fun durationParts(totalMinutes: Int, locale: Locale = Locale.getDefault()): DurationParts {
        val safeMinutes = totalMinutes.coerceAtLeast(0)
        val numbers = NumberFormat.getIntegerInstance(locale)
        return DurationParts(
            hours = safeMinutes / 60,
            minutes = safeMinutes % 60,
            hoursText = BidiText.isolate(numbers.format(safeMinutes / 60)),
            minutesText = BidiText.isolate(numbers.format(safeMinutes % 60)),
        )
    }

    /** Localised numerals for a duration, plus the raw values to choose a pattern by. */
    data class DurationParts(
        val hours: Int,
        val minutes: Int,
        val hoursText: String,
        val minutesText: String,
    )
}
