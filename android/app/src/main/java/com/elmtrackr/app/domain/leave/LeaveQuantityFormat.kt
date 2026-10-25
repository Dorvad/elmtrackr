package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.FormattingPolicy
import java.text.NumberFormat
import java.util.Locale

/**
 * How a leave quantity is written.
 *
 * Days and hours stay in their own unit — a workplace may have no standard day
 * to convert with — and the display locale and the CSV locale are not the same
 * one. A comma decimal in a CSV cell would shift every column after it, which
 * is the same reason [com.elmtrackr.app.domain.HoursFormatter.csv] is pinned to
 * [FormattingPolicy.machine].
 */
object LeaveQuantityFormat {

    /** For a person reading the report. Trailing zeros dropped: `1` and `0.5`. */
    fun number(units: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(FormattingPolicy.display(locale)).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
            isGroupingUsed = false
        }.format(units)

    /** For a CSV cell. Two decimals, no grouping, root locale. */
    fun csv(units: Double): String =
        NumberFormat.getNumberInstance(FormattingPolicy.machine()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = false
        }.format(units)
}
