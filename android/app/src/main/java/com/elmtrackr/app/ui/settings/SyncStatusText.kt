package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.UiText
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns the machine-oriented sync status stored by the sync repository
 * (e.g. "Synced 2026-07-02T08:15:30.123Z") into text meant for people,
 * as a [UiText] that localizes at render time. Unrecognised statuses pass
 * through unchanged as [UiText.Raw].
 */
object SyncStatusText {

    fun format(
        status: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): UiText? {
        if (status.isNullOrBlank()) return null
        if (status.startsWith(SYNCED_PREFIX)) {
            val instant = runCatching { Instant.parse(status.removePrefix(SYNCED_PREFIX).trim()) }
                .getOrNull() ?: return UiText.Raw(status)
            return relativeTime(instant, now, zone, locale)
        }
        if (status.startsWith(FAILED_PREFIX)) {
            return UiText.Res(R.string.sync_failed_with, status.removePrefix(FAILED_PREFIX).trim())
        }
        return UiText.Raw(status)
    }

    private fun relativeTime(instant: Instant, now: Instant, zone: ZoneId, locale: Locale): UiText {
        val elapsed = Duration.between(instant, now)
        val minutes = elapsed.toMinutes()
        return when {
            elapsed.isNegative || minutes < 1 -> UiText.Res(R.string.sync_synced_just_now)
            minutes < 60 -> UiText.Res(R.string.sync_synced_min_ago, minutes)
            instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() ->
                UiText.Res(
                    R.string.sync_synced_today_at,
                    instant.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", locale)),
                )
            else -> UiText.Res(
                R.string.sync_synced_on,
                instant.atZone(zone).format(DateTimeFormatter.ofPattern("MMM d, HH:mm", locale)),
            )
        }
    }

    private const val SYNCED_PREFIX = "Synced "
    private const val FAILED_PREFIX = "Failed:"
}
