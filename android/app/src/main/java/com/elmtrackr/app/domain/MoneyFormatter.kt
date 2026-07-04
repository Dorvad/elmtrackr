package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CurrencyCode
import java.text.NumberFormat
import java.math.RoundingMode
import java.util.Locale

object MoneyFormatter {
    fun format(amount: Double, currency: CurrencyCode): String = formatLegacy(amount, currency)

    fun format(amount: Double, currencyCode: String): String {
        val code = currencyCode.uppercase()
        val locale = when (code) {
            "ILS" -> Locale("he", "IL")
            "GBP" -> Locale.UK
            "EUR" -> Locale.GERMANY
            "JPY" -> Locale.JAPAN
            else -> Locale.US
        }
        return try {
            val javaCurrency = java.util.Currency.getInstance(code)
            // Use the currency's own minor-unit count (e.g. JPY has none), not a flat 2.
            val digits = CurrencyCode.entries.firstOrNull { it.name == code }?.fractionDigits
                ?: javaCurrency.defaultFractionDigits.coerceAtLeast(0)
            val formatter = java.text.NumberFormat.getCurrencyInstance(locale)
            formatter.currency = javaCurrency
            formatter.minimumFractionDigits = digits
            formatter.maximumFractionDigits = digits
            formatter.roundingMode = RoundingMode.HALF_UP
            formatter.format(amount)
        } catch (_: Exception) {
            val enumCurrency = CurrencyCode.entries.firstOrNull { it.name == code }
            if (enumCurrency != null) formatLegacy(amount, enumCurrency)
            else "$code ${"%.2f".format(amount)}"
        }
    }

    private fun formatLegacy(amount: Double, currency: CurrencyCode): String {
        val number = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = currency.fractionDigits
            maximumFractionDigits = currency.fractionDigits
            roundingMode = RoundingMode.HALF_UP
        }.format(amount)
        val separator = if (currency.symbol.lastOrNull()?.isLetter() == true) " " else ""
        return "${currency.symbol}$separator$number"
    }
}
