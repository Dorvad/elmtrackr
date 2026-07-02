package com.elmtrackr.app.ui.settings

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns the machine-oriented sync status stored by the sync repository
 * (e.g. "Synced 2026-07-02T08:15:30.123Z") into text meant for people.
 * Unrecognised statuses pass through unchanged.
 */
object SyncStatusText {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

    fun format(
        status: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (status.isNullOrBlank()) return status
        if (status.startsWith(SYNCED_PREFIX)) {
            val instant = runCatching { Instant.parse(status.removePrefix(SYNCED_PREFIX).trim()) }
                .getOrNull() ?: return status
            return "Synced ${relativeTime(instant, now, zone)}"
        }
        if (status.startsWith(FAILED_PREFIX)) {
            return "Sync failed — ${status.removePrefix(FAILED_PREFIX).trim()}"
        }
        return status
    }

    private fun relativeTime(instant: Instant, now: Instant, zone: ZoneId): String {
        val elapsed = Duration.between(instant, now)
        val minutes = elapsed.toMinutes()
        return when {
            elapsed.isNegative || minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() ->
                "today at ${instant.atZone(zone).format(timeFormatter)}"
            else -> "on ${instant.atZone(zone).format(dateTimeFormatter)}"
        }
    }

    private const val SYNCED_PREFIX = "Synced "
    private const val FAILED_PREFIX = "Failed:"
}
