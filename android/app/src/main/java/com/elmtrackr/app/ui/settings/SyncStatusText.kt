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
 * as a [UiText] that localizes at render time.
 *
 * Nothing the pipeline wrote is ever shown verbatim. Its failure and warning
 * details are step labels and PostgREST bodies — "push refund claims: PGRST205
 * Could not find the table…" — which are untranslated, unactionable, and
 * occasionally name a column or a constraint. The status line says what
 * happened; the detail belongs in diagnostics.
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
        // with "Synced ", so it matched there instead and failed to parse as an
        // Instant.
        if (warningDetail(status) != null) {
            return UiText.Res(R.string.settings_sync_synced_some_skipped)
        }
        // Carries a count rather than a sentence, so the whole message is
        // translatable — which is why this one keeps its detail and the warning
        // and failure statuses, which carry free-form English, do not.
        if (status.startsWith(UNSENT_PREFIX)) {
            val count = status.removePrefix(UNSENT_PREFIX).trim().toIntOrNull()
                ?: return UiText.Res(R.string.sync_failed_retrying)
            return UiText.Res(R.string.settings_sync_synced_unsent, count)
        }
        if (status.startsWith(SYNCED_PREFIX)) {
            val instant = runCatching { Instant.parse(status.removePrefix(SYNCED_PREFIX).trim()) }
                .getOrNull() ?: return UiText.Res(R.string.sync_failed_retrying)
            return relativeTime(instant, now, zone, locale)
        }
        if (status.startsWith(FAILED_PREFIX)) {
            return UiText.Res(R.string.sync_failed_retrying)
        }
        // Not a failure: there is no cloud to sync with, and saying "we'll try
        // again" about a sync that will never run is worse than saying nothing.
        if (status == NOT_CONFIGURED) {
            return UiText.Res(R.string.sync_not_configured)
        }
        // A status this function does not recognise is still a string the
        // pipeline wrote, not a sentence for a user to read.
        return UiText.Res(R.string.sync_failed_retrying)
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

    /** Kept in step with SyncRepositoryImpl's NotConfigured status. */
    private const val NOT_CONFIGURED = "Not configured"

    /**
     * Machine marker, not display text — do not translate. [SYNCED_PREFIX] and
     * [FAILED_PREFIX] are the same: all three parse values written by
     * SyncRepositoryImpl, which owns the format.
     */
    private const val SYNCED_WARN_PREFIX = "SyncedWarn:"
    private const val LEGACY_SYNCED_WARN_PREFIX = "Synced with warnings:"

    /** Kept in step with SyncRepositoryImpl.UNSENT_STATUS_PREFIX. */
    private const val UNSENT_PREFIX = "SyncedUnsent:"
}
