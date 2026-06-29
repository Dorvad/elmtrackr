package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ShiftWeekSection(
    val label: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val shifts: List<Shift>,
    val totalMinutes: Int,
    val pay: Double?,
    val isCurrentWeek: Boolean,
)

object ShiftWeekGrouper {

    private val weekLabelFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    fun groupByWeek(
        shifts: List<Shift>,
        activeShift: Shift?,
        month: YearMonth,
        settings: UserSettings?,
        profiles: List<CompensationProfile> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ShiftWeekSection> {
        val displayShifts = buildList {
            addAll(shifts)
            if (activeShift != null && !any { it.id == activeShift.id }) {
                add(activeShift)
            }
        }.distinctBy { it.id }

        if (displayShifts.isEmpty()) return emptyList()

        val today = LocalDate.now(zone)
        val grouped = displayShifts
            .groupBy { shift ->
                shift.startTime.atZone(zone).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            .map { (weekStart, weekShifts) ->
                val weekEnd = weekStart.plusDays(6)
                val sorted = weekShifts.sortedByDescending { it.startTime }
                val completed = sorted.filter { it.isCompleted }
                val totalMinutes = sorted.sumOf { shift ->
                    if (shift.isActive) {
                        ((java.time.Instant.now().toEpochMilli() - shift.startTime.toEpochMilli()) / 60_000)
                            .toInt()
                            .coerceAtLeast(0)
                    } else {
                        ShiftDurationCalculator.netMinutes(shift) ?: 0
                    }
                }
                val pay = settings?.let { s ->
                    completed.takeIf { it.isNotEmpty() }?.let {
                        PayrollCalculator.sumMonthlyPay(it, s, profiles).totalGross
                    }
                }
                val isCurrentWeek = !today.isBefore(weekStart) && !today.isAfter(weekEnd)
                val label = when {
                    isCurrentWeek && month == YearMonth.from(today) -> "THIS WEEK"
                    weekStart.month == weekEnd.month ->
                        "${weekStart.format(weekLabelFmt).uppercase(Locale.getDefault())} - ${weekEnd.dayOfMonth}"
                    else ->
                        "${weekStart.format(weekLabelFmt).uppercase(Locale.getDefault())} - " +
                            weekEnd.format(weekLabelFmt).uppercase(Locale.getDefault())
                }
                ShiftWeekSection(
                    label = label,
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    shifts = sorted,
                    totalMinutes = totalMinutes,
                    pay = pay,
                    isCurrentWeek = isCurrentWeek,
                )
            }
            .sortedByDescending { it.weekStart }

        return grouped
    }
}
