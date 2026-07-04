package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Pay-week helpers. The week anchor defaults to ISO Monday (same boundary as
 * [ShiftWeekGrouper]) but pay calculations pass the profile's configured
 * [com.elmtrackr.app.domain.model.CompensationRules.weekStartDay].
 */
object PayWeekMinutes {

    private val JS_DAYS = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
    )

    /** First day of the pay week containing [shift], anchored to [weekStartDay] (0=Sun … 6=Sat). */
    fun weekStart(shift: Shift, zone: ZoneId, weekStartDay: Int): LocalDate =
        shift.startTime.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(JS_DAYS[weekStartDay.coerceIn(0, 6)]))

    fun isoWeekStart(shift: Shift, zone: ZoneId): LocalDate = weekStart(shift, zone, 1)

    /**
     * Net minutes already worked in the same ISO week before [shift] starts.
     */
    fun priorMinutesBefore(
        shift: Shift,
        completedShifts: List<Shift>,
        zone: ZoneId,
    ): Int {
        val weekStart = isoWeekStart(shift, zone)
        return completedShifts
            .asSequence()
            .filter { it.id != shift.id && it.endTime != null }
            .filter { isoWeekStart(it, zone) == weekStart }
            .filter { it.startTime.isBefore(shift.startTime) }
            .sumOf { ShiftDurationCalculator.netMinutes(it) ?: 0 }
    }

    /**
     * Invokes [block] for each completed shift in chronological order with running week totals.
     */
    fun forEachWithPriorWeekMinutes(
        shifts: List<Shift>,
        zoneForShift: (Shift) -> ZoneId,
        block: (shift: Shift, priorWeekMinutes: Int) -> Unit,
    ) {
        val completed = shifts.filter { it.endTime != null }.sortedBy { it.startTime }
        val byWeek = completed.groupBy { isoWeekStart(it, zoneForShift(it)) }
        for (weekShifts in byWeek.values) {
            var prior = 0
            for (shift in weekShifts.sortedBy { it.startTime }) {
                block(shift, prior)
                prior += ShiftDurationCalculator.netMinutes(shift) ?: 0
            }
        }
    }
}
