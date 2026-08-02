package com.elmtrackr.app.domain

import java.util.Locale

/**
 * The single place worked hours are turned into text.
 *
 * Four copies of this rule used to live next to the screens that needed it, and
 * one of them formatted without a locale — so the same minute count could render
 * as `8.5` on one screen and `8,5` on another whenever the device locale used a
 * comma while the app language did not.
 *
 * Everything is pinned to [Locale.US] to match [MoneyFormatter], which already
 * formats every amount that way. Hours and money appear side by side throughout
 * the app; they have to agree on what a decimal separator looks like.
 */
object HoursFormatter {

    /** One decimal place — the display format used by every screen. */
    fun decimal(minutes: Int): String = "%.1f".format(Locale.US, minutes / 60.0)

    /** Two decimal places — the export format, where precision outranks brevity. */
    fun csv(minutes: Int): String = "%.2f".format(Locale.US, minutes / 60.0)
}
