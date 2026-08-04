package com.elmtrackr.app.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.money.MoneyFormat
import java.util.Locale

/**
 * "8h 30m" from resources rather than from string concatenation in code.
 *
 * [com.elmtrackr.app.domain.ShiftDurationCalculator.formatMinutes] built the unit
 * suffixes inline, so every hourly surface — the report card, the shift form
 * summary, the compensation tier labels, the widget, and the exported PDF a user
 * hands an employer — printed English units to a Hebrew reader. Paid Projects had
 * already localised its own durations, so the app shipped two standards for the same
 * thing.
 *
 * Numerals come from [MoneyFormat.durationParts], which formats them for the locale
 * and wraps each in a bidi isolate so an hour count cannot be reordered by the
 * Hebrew text around it.
 *
 * Two entry points because the callers differ: composables read the pattern from the
 * composition, and [format] serves the PDF exporter and the widget, which hold a
 * [Context] but no composition.
 */
object DurationText {

    /**
     * @param context already locale-wrapped by the caller where that matters — the
     *   exporter passes its `withAppLocale()` context so the PDF follows the in-app
     *   language rather than the device one.
     */
    fun format(
        context: Context,
        minutes: Int,
        locale: Locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
    ): String {
        val parts = MoneyFormat.durationParts(minutes, locale)
        // The project_duration_* patterns, deliberately reused rather than duplicated
        // under a neutral name: they already say exactly this, in both locales, and a
        // second set would be two more strings to keep in step.
        return when {
            parts.hours == 0 -> context.getString(R.string.project_duration_m, parts.minutesText)
            parts.minutes == 0 -> context.getString(R.string.project_duration_h, parts.hoursText)
            else -> context.getString(
                R.string.project_duration_hm,
                parts.hoursText,
                parts.minutesText,
            )
        }
    }
}

/**
 * The composable form, for screens.
 *
 * Named apart from `formattedMinutes` in the projects package only because that one
 * is already imported widely there; both resolve the same patterns.
 */
@Composable
fun durationText(minutes: Int, locale: Locale = Locale.getDefault()): String {
    val parts = MoneyFormat.durationParts(minutes, locale)
    return when {
        parts.hours == 0 -> stringResource(R.string.project_duration_m, parts.minutesText)
        parts.minutes == 0 -> stringResource(R.string.project_duration_h, parts.hoursText)
        else -> stringResource(R.string.project_duration_hm, parts.hoursText, parts.minutesText)
    }
}
