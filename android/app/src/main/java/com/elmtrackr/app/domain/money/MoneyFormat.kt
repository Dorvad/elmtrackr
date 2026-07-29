package com.elmtrackr.app.domain.money

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
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = money.fractionDigits
            maximumFractionDigits = money.fractionDigits
            roundingMode = MoneyPolicy.DISPLAY_ROUNDING
        }.format(money.amount)
        return String.format(locale, "%s %s", money.currencyCode, number)
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

    /** "12h 30m" style duration, digits localised. */
    fun formatMinutes(totalMinutes: Int, locale: Locale = Locale.getDefault()): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val numbers = NumberFormat.getIntegerInstance(locale)
        return if (minutes == 0) {
            String.format(locale, "%sh", numbers.format(hours))
        } else {
            String.format(locale, "%sh %sm", numbers.format(hours), numbers.format(minutes))
        }
    }
}
