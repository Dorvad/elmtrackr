package com.elmtrackr.app.widget

import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.TodayMinutes
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
                isSignedIn = context.isSignedIn,
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
                isSignedIn = context.isSignedIn,
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
                isSignedIn = context.isSignedIn,
            )
        }
    }

    private fun computeTodayMinutes(
        activeShift: Shift?,
        todayShifts: List<Shift>,
        zone: ZoneId,
        today: LocalDate,
    ): Int {
        // One definition, shared with the Shifts screen's week cards — see
        // TodayMinutes. This counted completed shifts net of break and added the
        // running shift *gross*, so a shift with a break recorded on it counted
        // more while running than it did once it ended, and the ring stepped
        // backwards at clock-out.
        val counted = (todayShifts.filter { it.isCompleted } + listOfNotNull(activeShift))
            .distinctBy { it.id }
        return TodayMinutes.forDay(counted, zone, today)
    }
}
