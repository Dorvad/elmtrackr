package com.elmtrackr.app.ui.leave

import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import java.math.BigDecimal

/**
 * Reading what the absence form's number fields hold.
 *
 * `String.toDoubleOrNull` was the parser, and it was wrong three ways for a money
 * field: it rejects "1,5" — the decimal key on a Hebrew, Arabic or Russian keyboard
 * — so the typed amount silently became no amount; it accepts "NaN", "Infinity"
 * and "1e3"; and it accepts a minus sign. A part-day value it could not read fell
 * back to a whole day, so a typo saved a full day's leave.
 *
 * Here a value is digits with at most one separator, either `.` or `,`. Anything
 * else is invalid and says so; nothing is quietly replaced with another number.
 */
internal object LeaveAmountInput {

    private val PLAIN_DECIMAL = Regex("""^(\d+([.,]\d*)?|[.,]\d+)$""")

    /** Most hours one calendar date can hold. */
    private const val MAX_HOURS_PER_DAY = 24.0

    /** The number in [text]; null when it is blank or not a plain non-negative decimal. */
    fun parse(text: String): Double? {
        val trimmed = text.trim()
        if (!PLAIN_DECIMAL.matches(trimmed)) return null
        return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    /** True when something is typed but it is not a usable amount. Blank is not invalid. */
    fun isInvalid(text: String): Boolean = text.isNotBlank() && parse(text) == null

    /**
     * A part day's size, or null when it is not one: more than nothing, and at most a
     * whole day — 1 in days, 24 in hours. One date is one allocation, so a "part day"
     * of 1.5 days is not a part of anything.
     */
    fun partialUnits(text: String, unit: LeaveBalanceUnit): Double? {
        val value = parse(text) ?: return null
        val max = when (unit) {
            LeaveBalanceUnit.DAYS -> 1.0
            LeaveBalanceUnit.HOURS -> MAX_HOURS_PER_DAY
        }
        return value.takeIf { it > 0.0 && it <= max }
    }

    /** A stored amount as the field shows it: plain digits and a `.`, no grouping, no trailing zeros. */
    fun format(amount: Double): String =
        BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
}
