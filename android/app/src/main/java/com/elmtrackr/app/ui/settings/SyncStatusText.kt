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
        // Checked before SYNCED_PREFIX: the legacy warning status also began
        // with "Synced ", so it matched here, failed to parse as an Instant and
        // fell through to UiText.Raw — showing English to Hebrew users.
        warningDetail(status)?.let {
            return UiText.Res(R.string.settings_sync_synced_with_warnings, it)
        }
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

    /**
     * Returns the detail of a "synced with warnings" status, or null.
     *
     * Two forms are accepted because the status is persisted: the marker
     * written today, and the English sentence written by earlier builds, which
     * is still sitting in existing installs' settings rows.
     */
    private fun warningDetail(status: String): String? = when {
        status.startsWith(SYNCED_WARN_PREFIX) -> status.removePrefix(SYNCED_WARN_PREFIX).trim()
        status.startsWith(LEGACY_SYNCED_WARN_PREFIX) -> status.removePrefix(LEGACY_SYNCED_WARN_PREFIX).trim()
        else -> null
    }

    private const val SYNCED_PREFIX = "Synced "
    private const val FAILED_PREFIX = "Failed:"

    /**
     * Machine marker, not display text — do not translate. [SYNCED_PREFIX] and
     * [FAILED_PREFIX] are the same: all three parse values written by
     * SyncRepositoryImpl, which owns the format.
     */
    private const val SYNCED_WARN_PREFIX = "SyncedWarn:"
    private const val LEGACY_SYNCED_WARN_PREFIX = "Synced with warnings:"
}
