package com.elmtrackr.app.widget

import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.time.WorkTimezone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WidgetStateMapper {

    fun map(context: WidgetContext, locale: Locale = Locale.getDefault()): WidgetShiftState {
        val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
        val zone = context.settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = Instant.now().atZone(zone)
        val dateLabel = now.format(dateFormatter)
        val dailyGoal = context.settings?.dailyOvertimeThresholdMinutes
            ?: WidgetShiftState.DEFAULT_DAILY_GOAL_MINUTES
        val todayMinutes = computeTodayMinutes(context.activeShift, context.todayShifts, zone, today)

        val shift = context.activeShift
        if (shift != null && shift.isActive) {
            val startFormatted = WidgetPreferences.formatShiftStart(shift.startTime, zone)
            return WidgetShiftState(
                isActive = true,
                shiftId = shift.id,
                startTimeLabel = startFormatted,
                dateLabel = dateLabel,
                lastPunchLabel = "Since $startFormatted",
                pendingCount = context.pendingCount,
                shiftStartEpochMillis = shift.startTime.toEpochMilli(),
                todayMinutes = todayMinutes,
                dailyGoalMinutes = dailyGoal,
            )
        }

        val lastEnd = context.lastCompletedShift?.endTime
        return if (lastEnd != null) {
            WidgetShiftState(
                isActive = false,
                shiftId = "",
                startTimeLabel = WidgetPreferences.formatShiftStart(lastEnd, zone),
                dateLabel = dateLabel,
                lastPunchLabel = WidgetPreferences.formatLastPunch(lastEnd, zone),
                pendingCount = context.pendingCount,
                lastPunchEndEpochMillis = lastEnd.toEpochMilli(),
                todayMinutes = todayMinutes,
                dailyGoalMinutes = dailyGoal,
            )
        } else {
            WidgetShiftState(
                isActive = false,
                shiftId = "",
                startTimeLabel = "--:--",
                dateLabel = dateLabel,
                lastPunchLabel = "",
                pendingCount = context.pendingCount,
                todayMinutes = todayMinutes,
                dailyGoalMinutes = dailyGoal,
            )
        }
    }

    private fun computeTodayMinutes(
        activeShift: Shift?,
        todayShifts: List<Shift>,
        zone: ZoneId,
        today: LocalDate,
    ): Int {
        val completed = todayShifts
            .filter { it.isCompleted && it.startTime.atZone(zone).toLocalDate() == today }
            .sumOf { ShiftDurationCalculator.netMinutes(it) ?: 0 }
        val active = activeShift?.takeIf { it.isActive }?.let { shift ->
            if (shift.startTime.atZone(zone).toLocalDate() == today) {
                ShiftDurationCalculator.elapsedMinutes(shift) ?: 0
            } else {
                0
            }
        } ?: 0
        return completed + active
    }
}
