package com.elmtrackr.app.domain

import java.time.DayOfWeek

/**
 * Converts java.time.DayOfWeek to JavaScript-style day integer.
 * JS: 0=Sun, 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat
 * java.time: MONDAY=1 … SUNDAY=7 (different encoding for Sunday)
 *
 * Used wherever weekendDays settings (stored in JS encoding) are compared.
 */
internal fun DayOfWeek.toJsDay(): Int = when (this) {
    DayOfWeek.SUNDAY    -> 0
    DayOfWeek.MONDAY    -> 1
    DayOfWeek.TUESDAY   -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY  -> 4
    DayOfWeek.FRIDAY    -> 5
    DayOfWeek.SATURDAY  -> 6
}
