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
    /** False when no user is signed in — punch taps open the app instead. */
    val isSignedIn: Boolean = true,
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
    val isSignedIn: Boolean = true,
) {
    companion object {
        /** Aliased, not redeclared: see [com.elmtrackr.wear.sync.WearShiftSnapshot]. */
        const val DEFAULT_DAILY_GOAL_MINUTES =
            com.elmtrackr.wear.sync.WearShiftSnapshot.DEFAULT_DAILY_GOAL_MINUTES
    }
}

object WidgetTimeFormat {

    // Widgets repaint at best every ~10 seconds (WidgetTimerScheduler), and
    // far less often under Doze, so a seconds display would sit frozen or
    // jump in visible chunks. h:mm matches the Wear tile.
    fun elapsedHm(startEpochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (startEpochMillis <= 0L) return "0:00"
        val totalMinutes = ((nowMillis - startEpochMillis) / 60_000L).coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%d:%02d", totalMinutes / 60, totalMinutes % 60)
    }

    fun minutesToHm(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%d:%02d", minutes / 60, minutes % 60)
    }
}
