package com.elmtrackr.app.widget

import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WidgetContext(
    val activeShift: Shift?,
    val lastCompletedShift: Shift?,
    val todayShifts: List<Shift>,
    val settings: UserSettings?,
    val pendingCount: Int = 0,
)

data class WidgetShiftState(
    val isActive: Boolean,
    val shiftId: String,
    /** HH:mm shift start when active; last punch time when idle. */
    val startTimeLabel: String,
    val dateLabel: String,
    val lastPunchLabel: String,
    val pendingCount: Int,
    val shiftStartEpochMillis: Long = 0L,
    val lastPunchEndEpochMillis: Long = 0L,
    val todayMinutes: Int = 0,
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL_MINUTES,
) {
    companion object {
        const val DEFAULT_DAILY_GOAL_MINUTES = 480
    }
}

object WidgetTimeFormat {

    fun elapsedHms(startEpochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (startEpochMillis <= 0L) return "0:00:00"
        val totalSeconds = ((nowMillis - startEpochMillis) / 1000L).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun minutesToHms(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format("%d:%02d:00", hours, mins)
    }

    fun minutesToShort(totalMinutes: Int): String =
        ShiftDurationCalculator.formatMinutes(totalMinutes)
}
