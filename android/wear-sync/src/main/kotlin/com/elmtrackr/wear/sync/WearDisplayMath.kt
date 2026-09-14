package com.elmtrackr.wear.sync

import java.time.ZoneId
import java.util.Locale
import kotlin.math.max

object WearDisplayMath {

    fun elapsedHms(startEpochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (startEpochMillis <= 0L) return "0:00"
        val totalSeconds = ((nowMillis - startEpochMillis) / 1000L).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Hours:minutes count-up (e.g. "2:34") for surfaces that refresh once a minute. */
    fun elapsedHm(startEpochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (startEpochMillis <= 0L) return "0:00"
        val totalMinutes = ((nowMillis - startEpochMillis) / 60_000L).coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", totalMinutes / 60, totalMinutes % 60)
    }

    fun minutesToHms(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format(Locale.US, "%d:%02d:00", hours, mins)
    }

    fun minutesToShort(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    fun progressPercent(todayMinutes: Int, dailyGoalMinutes: Int): Int {
        if (dailyGoalMinutes <= 0) return 0
        return ((todayMinutes.toFloat() / dailyGoalMinutes) * 100f).toInt().coerceIn(0, 100)
    }

    fun progressRemainderMinutes(todayMinutes: Int, dailyGoalMinutes: Int): Int =
        max(0, dailyGoalMinutes - todayMinutes)

    fun displayFor(
        snapshot: WearShiftSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): WearDisplayState {
        val rolled = WearLocalShift.rollToLocalDay(snapshot, nowMillis, zone)
        val elapsed = if (rolled.isActive && rolled.shiftStartEpochMillis > 0L) {
            elapsedHms(rolled.shiftStartEpochMillis, nowMillis)
        } else {
            ""
        }
        val progress = progressPercent(rolled.todayMinutes, rolled.dailyGoalMinutes)
        return WearDisplayState(
            snapshot = rolled,
            elapsedHms = elapsed,
            todayHms = minutesToHms(rolled.todayMinutes),
            todayShort = minutesToShort(rolled.todayMinutes),
            progressPercent = progress,
            statusLabel = if (rolled.isActive) "CLOCKED IN" else "CLOCKED OUT",
            actionLabel = if (rolled.isActive) "PUNCH OUT" else "PUNCH IN",
            primaryTimeLabel = when {
                rolled.isActive && elapsed.isNotEmpty() -> elapsed
                rolled.startTimeLabel != "--:--" -> rolled.startTimeLabel
                else -> minutesToHms(rolled.todayMinutes)
            },
            complicationShortText = when {
                rolled.isActive && elapsed.isNotEmpty() -> elapsed
                rolled.isActive -> "IN"
                rolled.startTimeLabel != "--:--" -> rolled.startTimeLabel
                else -> "OUT"
            },
            complicationLongText = when {
                rolled.isActive && elapsed.isNotEmpty() -> "Clocked in · $elapsed"
                rolled.lastPunchLabel.isNotBlank() -> rolled.lastPunchLabel
                else -> "Clocked out"
            },
        )
    }
}

data class WearDisplayState(
    val snapshot: WearShiftSnapshot,
    val elapsedHms: String,
    val todayHms: String,
    val todayShort: String,
    val progressPercent: Int,
    val statusLabel: String,
    val actionLabel: String,
    val primaryTimeLabel: String,
    val complicationShortText: String,
    val complicationLongText: String,
)
