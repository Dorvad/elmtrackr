package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Pay-week anchoring. The week anchor defaults to ISO Monday (same boundary as
 * [ShiftWeekGrouper]) but pay calculations pass the profile's configured
 * [com.elmtrackr.app.domain.model.CompensationRules.weekStartDay].
 */
object PayWeekMinutes {

    private val JS_DAYS = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
    )

    /** First day of the pay week containing [date], anchored to [weekStartDay] (0=Sun … 6=Sat). */
    fun weekStartOf(date: LocalDate, weekStartDay: Int): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(JS_DAYS[weekStartDay.coerceIn(0, 6)]))

    /** First day of the pay week containing [shift], anchored to [weekStartDay] (0=Sun … 6=Sat). */
    fun weekStart(shift: Shift, zone: ZoneId, weekStartDay: Int): LocalDate =
        weekStartOf(shift.startTime.atZone(zone).toLocalDate(), weekStartDay)
}
