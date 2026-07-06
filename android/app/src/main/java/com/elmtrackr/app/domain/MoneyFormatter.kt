package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CurrencyCode
import java.text.NumberFormat
import java.math.RoundingMode
import java.util.Locale

object MoneyFormatter {
    fun format(amount: Double, currency: CurrencyCode): String = formatLegacy(amount, currency)

    fun format(amount: Double, currencyCode: String): String {
        val code = currencyCode.uppercase()
        // Route known currencies through the same symbol-first style as the
        // CurrencyCode overload so amounts look identical on every screen.
        CurrencyCode.entries.firstOrNull { it.name == code }?.let { return formatLegacy(amount, it) }
        return try {
            val javaCurrency = java.util.Currency.getInstance(code)
            // Use the currency's own minor-unit count (e.g. JPY has none), not a flat 2.
            val digits = javaCurrency.defaultFractionDigits.coerceAtLeast(0)
            val formatter = java.text.NumberFormat.getCurrencyInstance(Locale.US)
            formatter.currency = javaCurrency
            formatter.minimumFractionDigits = digits
            formatter.maximumFractionDigits = digits
            formatter.roundingMode = RoundingMode.HALF_UP
            formatter.format(amount)
        } catch (_: Exception) {
            "$code ${"%.2f".format(amount)}"
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
