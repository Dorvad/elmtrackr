package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.money.MoneyFormat
import java.util.Locale

/**
 * Money as the reader's language writes it.
 *
 * A thin front for [MoneyFormat], kept because 37 call sites across nine files pass a
 * `Double` and a [CurrencyCode] or an ISO string, which is what the payroll layer
 * produces. Every one of them now renders through CLDR.
 *
 * It used to format at [Locale.US] and glue the symbol on by hand:
 *
 * ```
 * val separator = if (currency.symbol.lastOrNull()?.isLetter() == true) " " else ""
 * return "${'$'}{currency.symbol}${'$'}separator${'$'}number"
 * ```
 *
 * That is symbol-first, always, with a hardcoded `.` decimal separator and `,` groups.
 * For an Israeli user reading Hebrew — the app's primary audience — it produced "₪531.25"
 * where the language writes "531.25 ₪", and no locale setting could have moved the
 * symbol, because the placement lived in the concatenation rather than in a format.
 * Hebrew's separators were already right; its symbol placement never was.
 *
 * The locale comes from [FormattingPolicy], which [HoursFormatter] also asks, so hours
 * and money on the same row cannot disagree about a decimal separator or a numeral
 * system — the second matters more than it sounds: Arabic renders both in Arabic-Indic
 * digits, and one of the pair opting out would put "٨٫٥" beside "₪531.25".
 *
 * **Composables should pass `appLocale()`.** [Locale.getDefault] follows
 * `AppCompatDelegate.setApplicationLocales`, so it is usually right, but it is process
 * state — a background thread formatting for a notification or a widget gets whatever
 * was set last, while `appLocale()` reads the configuration the UI is actually drawing
 * in.
 */
object MoneyFormatter {

    fun format(
        amount: Double,
        currency: CurrencyCode,
        locale: Locale = Locale.getDefault(),
    ): String = MoneyFormat.formatAmount(amount, currency.name, FormattingPolicy.display(locale))

    fun format(
        amount: Double,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
    ): String = MoneyFormat.formatAmount(amount, currencyCode, FormattingPolicy.display(locale))
}
