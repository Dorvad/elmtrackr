package com.elmtrackr.app.ui.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusPresentationTest {

    @Test
    fun `local only when remote not configured`() {
        val status = resolveSyncStatusCopy(
            isRemoteConfigured = false,
            isOnline = true,
            isSyncing = false,
            pendingCount = 5,
            lastSyncStatus = null,
            syncError = null,
        )
        assertEquals("Local only", status.shortLabel)
        assertTrue(status.contentDescription.contains("Local only"))
    }

    @Test
    fun `no network distinguishes from pending`() {
        val status = resolveSyncStatusCopy(
            isRemoteConfigured = true,
            isOnline = false,
            isSyncing = false,
            pendingCount = 3,
            lastSyncStatus = null,
            syncError = null,
        )
        assertEquals("No network", status.shortLabel)
        assertTrue(status.detail.contains("3 changes"))
    }

    @Test
    fun `pending upload when online`() {
        val status = resolveSyncStatusCopy(
            isRemoteConfigured = true,
            isOnline = true,
            isSyncing = false,
            pendingCount = 3,
            lastSyncStatus = null,
            syncError = null,
        )
        assertEquals("3 waiting", status.shortLabel)
        assertTrue(status.isActionable)
    }

    @Test
    fun `sync error is retryable`() {
        val status = resolveSyncStatusCopy(
            isRemoteConfigured = true,
            isOnline = true,
            isSyncing = false,
            pendingCount = 0,
            lastSyncStatus = null,
            syncError = "Server error",
        )
        assertEquals("Couldn't sync", status.shortLabel)
        assertTrue(status.isActionable)
    }

    @Test
    fun `all synced when clean`() {
        val status = resolveSyncStatusCopy(
            isRemoteConfigured = true,
            isOnline = true,
            isSyncing = false,
            pendingCount = 0,
            lastSyncStatus = "success",
            syncError = null,
        )
        assertEquals("All synced", status.shortLabel)
    }
}
