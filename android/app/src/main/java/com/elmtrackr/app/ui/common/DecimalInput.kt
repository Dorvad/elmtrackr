package com.elmtrackr.app.ui.common

import java.util.Locale

/**
 * Typed decimal numbers — rates, hours, multipliers — read the way people type them.
 *
 * Each field used to filter input its own way. Onboarding kept digits and `.` and
 * dropped everything else, so "62,5" typed on a keyboard offering a comma became
 * "625" — a rate ten times too high, accepted without a word. The overtime-hours and
 * multiplier fields kept a comma on screen but could not parse it, so the edit was
 * silently ignored; the premium editor refused the keystroke outright. Hours were
 * also *displayed* with the device locale's separator, so a Russian reader opened
 * the screen to "8,60" and could not change it.
 *
 * Here `.`, `,` and the Arabic decimal separator all mean the decimal point,
 * Arabic-Indic digits mean their Western values, and the result is always a string
 * [String.toDoubleOrNull] understands.
 */
object DecimalInput {

    private val SEPARATORS = setOf('.', ',', '٫')

    /**
     * Keeps digits (normalised to 0–9) and the first separator (normalised to `.`);
     * drops anything else. A separator typed first becomes "0.".
     */
    fun sanitize(input: String): String = buildString {
        var hasSeparator = false
        input.forEach { c ->
            when {
                c.isDigit() -> append(Character.forDigit(Character.digit(c, 10), 10))
                c in SEPARATORS && !hasSeparator -> {
                    if (isEmpty()) append('0')
                    append('.')
                    hasSeparator = true
                }
            }
        }
    }

    /** [sanitize]d and parsed; null for blank or unparseable input. */
    fun parse(input: String): Double? = sanitize(input).toDoubleOrNull()

    /**
     * A value for an editable field: no trailing ".0", at most [maxDecimals]
     * decimals, always with `.` so it parses back. Never the device locale's
     * separator — that is what made the hours fields uneditable in Russian.
     */
    fun format(value: Double, maxDecimals: Int = 2): String {
        if (value == value.toLong().toDouble()) return value.toLong().toString()
        return String.format(Locale.ROOT, "%.${maxDecimals}f", value).trimEnd('0').trimEnd('.')
    }
}
