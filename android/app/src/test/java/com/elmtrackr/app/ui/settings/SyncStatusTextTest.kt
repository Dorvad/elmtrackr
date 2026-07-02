package com.elmtrackr.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SyncStatusTextTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = Instant.parse("2026-07-02T12:00:00Z")

    @Test
    fun `null and blank statuses pass through`() {
        assertNull(SyncStatusText.format(null, now, zone))
        assertEquals("", SyncStatusText.format("", now, zone))
    }

    @Test
    fun `recent sync reads as just now`() {
        assertEquals(
            "Synced just now",
            SyncStatusText.format("Synced 2026-07-02T11:59:30Z", now, zone),
        )
    }

    @Test
    fun `sync minutes ago reads as relative time`() {
        assertEquals(
            "Synced 5 min ago",
            SyncStatusText.format("Synced 2026-07-02T11:55:00Z", now, zone),
        )
    }

    @Test
    fun `sync earlier the same local day shows time of day`() {
        // 05:00Z is 08:00 in Asia/Jerusalem (UTC+3 in July), same local day.
        assertEquals(
            "Synced today at 08:00",
            SyncStatusText.format("Synced 2026-07-02T05:00:00Z", now, zone),
        )
    }

    @Test
    fun `sync on a previous day shows the date`() {
        assertEquals(
            "Synced on Jun 30, 15:30",
            SyncStatusText.format("Synced 2026-06-30T12:30:00Z", now, zone),
        )
    }

    @Test
    fun `failure statuses read as sync failed`() {
        assertEquals(
            "Sync failed — push shifts: timeout",
            SyncStatusText.format("Failed: push shifts: timeout", now, zone),
        )
    }

    @Test
    fun `warning statuses pass through unchanged`() {
        val warning = "Synced with warnings: tasks table missing"
        assertEquals(warning, SyncStatusText.format(warning, now, zone))
    }

    @Test
    fun `unparseable synced status passes through unchanged`() {
        assertEquals("Synced ???", SyncStatusText.format("Synced ???", now, zone))
    }

    @Test
    fun `not configured passes through unchanged`() {
        assertEquals("Not configured", SyncStatusText.format("Not configured", now, zone))
    }
}
