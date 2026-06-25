package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CurrencyCode
import java.text.NumberFormat
import java.math.RoundingMode
import java.util.Locale

object MoneyFormatter {
    fun format(amount: Double, currency: CurrencyCode): String {
        val number = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = currency.fractionDigits
            maximumFractionDigits = currency.fractionDigits
            roundingMode = RoundingMode.HALF_UP
        }.format(amount)
        val separator = if (currency.symbol.lastOrNull()?.isLetter() == true) " " else ""
        return "${currency.symbol}$separator$number"
    }
}
