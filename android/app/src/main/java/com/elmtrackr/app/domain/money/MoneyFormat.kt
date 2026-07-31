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
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            val digits = money.fractionDigits
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = MoneyPolicy.DISPLAY_ROUNDING
        }
        return formatter.format(money.amount)
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
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = money.fractionDigits
            maximumFractionDigits = money.fractionDigits
            roundingMode = MoneyPolicy.DISPLAY_ROUNDING
        }.format(money.amount)

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
