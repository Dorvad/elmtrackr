package com.elmtrackr.app.domain

import java.text.NumberFormat
import java.util.Locale

/**
 * The single place worked hours are turned into text.
 *
 * Four copies of this rule used to live next to the screens that needed it, and one of
 * them formatted without a locale — so the same minute count could render as `8.5` on
 * one screen and `8,5` on another whenever the device locale used a comma while the app
 * language did not.
 *
 * Both were then pinned to [Locale.US] to match [MoneyFormatter]. The pinning was the
 * right instinct — hours and money share a row and must share a decimal separator — and
 * the wrong fix: agreeing on the wrong convention is still wrong. A Russian reader writes
 * `8,5` and a reader of Arabic writes `٨٫٥`. (Hebrew writes `8.5`, so nothing changes
 * there; see the table in [FormattingPolicy].) They now agree by both asking
 * [FormattingPolicy].
 *
 * [decimal] and [csv] deliberately do **not** share a locale. See [csv].
 */
object HoursFormatter {

    /**
     * One decimal place, for the reader — `8.5` in English, `8,5` in Hebrew.
     *
     * Composables should pass `appLocale()`; see the note on [MoneyFormatter].
     */
    fun decimal(minutes: Int, locale: Locale = Locale.getDefault()): String =
        format(minutes, digits = 1, locale = FormattingPolicy.display(locale))

    /**
     * Two decimal places at [Locale.ROOT], for a machine.
     *
     * Not localised, and that is the point. `ReportsViewModel.buildCsvContent` joins its
     * columns with `,` and escapes only the notes field, so a Russian reader's `8,58`
     * shifts every column after it — silently, in the file a user hands to payroll. An
     * Arabic reader's `٨٫٥٨` would not parse as a number at all. Neither is visible from
     * inside the app.
     *
     * Two decimals rather than one because an export is parsed, not skimmed, and 8h20
     * is 8.33 hours.
     */
    fun csv(minutes: Int): String = format(minutes, digits = 2, locale = FormattingPolicy.machine())

    private fun format(minutes: Int, digits: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            // Grouping off: hour counts here are a shift or a month of them, never large
            // enough to need it, and a group separator inside a CSV cell would break the
            // same parse the ROOT locale above protects.
            isGroupingUsed = false
        }.format(minutes / 60.0)
}
