package com.elmtrackr.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.model.PayByCurrency
import com.elmtrackr.app.domain.text.BidiText

/**
 * Pay in one currency or several, as on-screen text.
 *
 * One currency renders exactly as `MoneyFormatter.format` always has. Several render
 * one after another, each with its code, because ElmTrackr has no exchange rates and a
 * combined figure would be an amount of nothing. The patterns are the Paid Projects
 * ones (`project_money_with_code`, `project_money_label_with_code`), already
 * translated, so pay and project money name a currency the same way.
 */
@Composable
fun PayByCurrency.formattedEach(separator: String = " · "): String {
    val locale = appLocale()
    val pattern = stringResource(R.string.project_money_with_code)
    return MoneyFormatter.formatEach(this, locale, separator) { number, code ->
        String.format(locale, pattern, number, BidiText.isolate(code))
    }
}

/**
 * As [formattedEach], but a zero in [zeroCurrency] when nothing was earned: a pay
 * figure that has always read "0" for a week with no paid minutes keeps reading it.
 */
@Composable
fun PayByCurrency.formattedEachOrZero(zeroCurrency: String): String =
    if (isEmpty) MoneyFormatter.format(0.0, zeroCurrency, appLocale()) else formattedEach()

/** One amount with its code spelled out, for a list that holds more than one currency. */
@Composable
fun moneyWithCode(amount: Double, currencyCode: String): String {
    val locale = appLocale()
    val pattern = stringResource(R.string.project_money_with_code)
    return MoneyFormatter.formatWithCode(amount, currencyCode, locale) { number, code ->
        String.format(locale, pattern, number, BidiText.isolate(code))
    }
}

/** "Gross pay (USD)": a label naming the currency its figure is in. */
@Composable
fun labelWithCode(label: String, currencyCode: String): String =
    stringResource(R.string.project_money_label_with_code, label, BidiText.isolate(currencyCode))
