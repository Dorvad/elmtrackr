package com.elmtrackr.wear.sync

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

    fun displayFor(snapshot: WearShiftSnapshot, nowMillis: Long = System.currentTimeMillis()): WearDisplayState {
        val elapsed = if (snapshot.isActive && snapshot.shiftStartEpochMillis > 0L) {
            elapsedHms(snapshot.shiftStartEpochMillis, nowMillis)
        } else {
            ""
        }
        val progress = progressPercent(snapshot.todayMinutes, snapshot.dailyGoalMinutes)
        return WearDisplayState(
            snapshot = snapshot,
            elapsedHms = elapsed,
            todayHms = minutesToHms(snapshot.todayMinutes),
            todayShort = minutesToShort(snapshot.todayMinutes),
            progressPercent = progress,
            statusLabel = if (snapshot.isActive) "CLOCKED IN" else "CLOCKED OUT",
            actionLabel = if (snapshot.isActive) "PUNCH OUT" else "PUNCH IN",
            primaryTimeLabel = when {
                snapshot.isActive && elapsed.isNotEmpty() -> elapsed
                snapshot.startTimeLabel != "--:--" -> snapshot.startTimeLabel
                else -> minutesToHms(snapshot.todayMinutes)
            },
            complicationShortText = when {
                snapshot.isActive && elapsed.isNotEmpty() -> elapsed
                snapshot.isActive -> "IN"
                snapshot.startTimeLabel != "--:--" -> snapshot.startTimeLabel
                else -> "OUT"
            },
            complicationLongText = when {
                snapshot.isActive && elapsed.isNotEmpty() -> "Clocked in · $elapsed"
                snapshot.lastPunchLabel.isNotBlank() -> snapshot.lastPunchLabel
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
