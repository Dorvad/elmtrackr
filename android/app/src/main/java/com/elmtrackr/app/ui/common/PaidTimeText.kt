package com.elmtrackr.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.PaidTimeReason
import com.elmtrackr.app.domain.model.PayBracket
import com.elmtrackr.app.domain.model.PayCategory
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The one sentence that explains why paid time is not the time worked, or null when
 * it is.
 *
 * Shared by the dashboard month card, the clock-out recap and Reports so the same
 * rules are described in the same words everywhere. One reason gets its own
 * sentence; several get a general one rather than a list joined in code, which does
 * not translate.
 *
 * @param paidText the paid duration, already formatted the way the caller shows
 *   hours ("8h 00m" on a shift, "94.0h" on a month).
 */
@Composable
fun paidTimeNote(paidText: String, reasons: Set<PaidTimeReason>): String? {
    if (reasons.isEmpty()) return null
    @StringRes val res = when (reasons.singleOrNull()) {
        PaidTimeReason.ROUNDING -> R.string.paid_time_reason_rounding
        PaidTimeReason.MINIMUM_SHIFT -> R.string.paid_time_reason_minimum
        PaidTimeReason.PAID_BREAKS -> R.string.paid_time_reason_paid_breaks
        null -> R.string.paid_time_reason_mixed
    }
    return stringResource(res, paidText)
}

/**
 * A pay bracket as a person reads it — "125% Overtime" — translated.
 *
 * The engines' own [PayBracket.label] is English and stays that way: it is a
 * diagnostic string, and money is classified by [PayBracket.category], never by the
 * label. Rendering the label directly put "100% — Regular" in front of Hebrew,
 * Arabic and Russian readers on the shift form's pay preview.
 */
@Composable
fun payBracketLabel(bracket: PayBracket): String {
    val percent = NumberFormat.getPercentInstance(appLocale()).format(
        // Whole percent: no shipped preset or editor produces a fractional multiplier
        // a person would want to read to the decimal.
        (bracket.multiplier * 100).roundToInt() / 100.0,
    )
    return stringResource(R.string.pay_bracket_label, percent, payCategoryLabel(bracket.category))
}

@Composable
fun payCategoryLabel(category: PayCategory): String = stringResource(
    when (category) {
        PayCategory.REGULAR -> R.string.pay_bracket_regular
        PayCategory.OVERTIME,
        PayCategory.DAILY_OVERTIME,
        PayCategory.WEEKLY_OVERTIME,
        -> R.string.pay_bracket_overtime
        PayCategory.SEVENTH_DAY -> R.string.pay_bracket_seventh_day
        PayCategory.WEEKEND -> R.string.pay_bracket_weekend
        PayCategory.WEEKLY_REST -> R.string.pay_bracket_weekly_rest
        PayCategory.WEEKLY_REST_OVERTIME -> R.string.pay_bracket_weekly_rest_overtime
        PayCategory.HOLIDAY -> R.string.pay_bracket_holiday
        PayCategory.HOLIDAY_OVERTIME -> R.string.pay_bracket_holiday_overtime
        PayCategory.PREMIUM -> R.string.pay_bracket_premium
    },
)
