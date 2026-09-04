package com.elmtrackr.app.domain

import java.util.Locale

/**
 * Which locale numbers render in, and the one place that decision is made.
 *
 * Hours and money sit next to each other on almost every screen — a shift row shows
 * "8.5" beside "₪531.25", a report card stacks them — so they have to agree on what a
 * decimal separator and a group separator look like. Two formatters each picking their
 * own locale is how "8.5" ends up beside "531,25 ₪".
 *
 * They used to agree by both being pinned to [Locale.US]. Correct for consistency and
 * wrong for everyone else: the app ships in Hebrew, Arabic and Russian, all of which use
 * a comma decimal separator, and Hebrew and Arabic place a currency symbol on the other
 * side of the number. `MoneyFormatter` also concatenated the symbol by hand, which no
 * locale setting can fix.
 *
 * Now they agree by both asking here. Flipping [LOCALE_AWARE] moves both together; there
 * is no state in which one is localised and the other is not.
 *
 * ### Display versus machine-readable
 *
 * [display] is for text a person reads. [machine] is for text something else parses — a
 * CSV cell, a value written to a file — and is [Locale.ROOT] whatever the UI language is.
 * That distinction is not cosmetic. `buildCsvContent` joins its columns with a comma and
 * escapes only the notes field, so a Russian reader's `8,58` shifts every column after it
 * and corrupts the export a user hands to payroll — silently, with nothing wrong-looking
 * in the app. An Arabic reader's `٨٫٥٨` would not parse as a number at all. The old code
 * had no way to say which kind a caller wanted, because everything was `Locale.US`.
 */
object FormattingPolicy {

    /**
     * The switch. `true` renders for the reader's language; `false` restores the old
     * [Locale.US] behaviour for both hours and money at once.
     *
     * Kept as a constant rather than a setting: the choice is "does this app respect the
     * reader's number conventions", which is not a per-user preference, and a runtime
     * toggle would let hours and money drift apart between two reads.
     */
    const val LOCALE_AWARE: Boolean = true

    /** Locale for numbers a person reads. */
    fun display(uiLocale: Locale = Locale.getDefault()): Locale =
        if (LOCALE_AWARE) uiLocale else Locale.US

    /**
     * Locale for numbers something else parses. Never the UI locale — a CSV consumer
     * does not know what language the app was in when the file was written.
     */
    fun machine(): Locale = Locale.ROOT
}
