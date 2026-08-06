package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class SyncStatusTextTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = Instant.parse("2026-07-02T12:00:00Z")
    private val locale = Locale.ENGLISH

    @Test
    fun `null and blank statuses produce nothing`() {
        assertNull(SyncStatusText.format(null, now, zone, locale))
        assertNull(SyncStatusText.format("", now, zone, locale))
    }

    @Test
    fun `recent sync reads as just now`() {
        assertEquals(
            UiText.Res(R.string.sync_synced_just_now),
            SyncStatusText.format("Synced 2026-07-02T11:59:30Z", now, zone, locale),
        )
    }

    @Test
    fun `sync minutes ago reads as relative time`() {
        assertEquals(
            UiText.Res(R.string.sync_synced_min_ago, 5L),
            SyncStatusText.format("Synced 2026-07-02T11:55:00Z", now, zone, locale),
        )
    }

    @Test
    fun `sync earlier the same local day shows time of day`() {
        // 05:00Z is 08:00 in Asia/Jerusalem (UTC+3 in July), same local day.
        assertEquals(
            UiText.Res(R.string.sync_synced_today_at, "08:00"),
            SyncStatusText.format("Synced 2026-07-02T05:00:00Z", now, zone, locale),
        )
    }

    @Test
    fun `sync on a previous day shows the date`() {
        assertEquals(
            UiText.Res(R.string.sync_synced_on, "Jun 30, 15:30"),
            SyncStatusText.format("Synced 2026-06-30T12:30:00Z", now, zone, locale),
        )
    }

    /**
     * The detail is deliberately dropped. "push shifts: timeout" is a step label
     * and a transport error — untranslated, unactionable, and sometimes naming a
     * column or constraint. It belongs in diagnostics, not on the status line.
     */
    @Test
    fun `failure statuses read as sync failed, without the pipeline's detail`() {
        assertEquals(
            UiText.Res(R.string.sync_failed_retrying),
            SyncStatusText.format("Failed: push shifts: timeout", now, zone, locale),
        )
    }

    @Test
    fun `warning statuses are localized`() {
        assertEquals(
            UiText.Res(R.string.settings_sync_synced_some_skipped),
            SyncStatusText.format("SyncedWarn: tasks table missing", now, zone, locale),
        )
    }

    /**
     * The status is persisted, so installs upgrading from a build that wrote the
     * English sentence still have it in their settings row. It began with
     * "Synced ", which meant it matched the timestamp prefix and failed to parse.
     */
    @Test
    fun `the legacy English warning status is still recognised`() {
        assertEquals(
            UiText.Res(R.string.settings_sync_synced_some_skipped),
            SyncStatusText.format("Synced with warnings: tasks table missing", now, zone, locale),
        )
    }

    /**
     * A sync where the pull succeeded but every pending row was rejected used to
     * report a plain "Synced <time>". The marker carries the count so the
     * sentence itself can be translated.
     */
    @Test
    fun `unsent-change count is localized`() {
        assertEquals(
            UiText.Res(R.string.settings_sync_synced_unsent, 3),
            SyncStatusText.format("SyncedUnsent: 3", now, zone, locale),
        )
    }

    @Test
    fun `an unsent marker without a count falls back to localized text`() {
        assertEquals(
            UiText.Res(R.string.sync_failed_retrying),
            SyncStatusText.format("SyncedUnsent: many", now, zone, locale),
        )
    }

    @Test
    fun `an unparseable synced status falls back to localized text`() {
        assertEquals(
            UiText.Res(R.string.sync_failed_retrying),
            SyncStatusText.format("Synced ???", now, zone, locale),
        )
    }

    /**
     * Not a failure — there is no cloud to sync with. Telling the user "we'll try
     * again" about a sync that will never run is worse than saying nothing.
     */
    @Test
    fun `not configured reads as local-only, not as a failure`() {
        assertEquals(
            UiText.Res(R.string.sync_not_configured),
            SyncStatusText.format("Not configured", now, zone, locale),
        )
    }
}
