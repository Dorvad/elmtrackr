package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * ISO Monday-anchored pay-week helpers (same week boundary as [ShiftWeekGrouper]).
 */
object PayWeekMinutes {

    fun isoWeekStart(shift: Shift, zone: ZoneId): LocalDate =
        shift.startTime.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

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
