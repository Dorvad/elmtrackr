package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.WorkplaceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDetailsBuilderTest {

    @Test
    fun `counts pending and failed rows by type`() {
        val task = TaskEntity(
            localId = "t1",
            remoteId = null,
            userId = "u1",
            name = "Client work",
            icon = "💼",
            color = null,
            hourlyRate = 50.0,
            isArchived = false,
            lastUsedAt = null,
            createdAt = 1L,
            updatedAt = 2L,
            deletedAt = null,
            syncStatus = SyncStatus.FAILED,
            lastSyncError = "Network timeout",
            lastSyncedAt = 100L,
        )
        val syncedTask = task.copy(localId = "t2", syncStatus = SyncStatus.SYNCED, lastSyncError = null)

        val details = SyncDetailsBuilder.build(
            tasks = listOf(task, syncedTask),
            shifts = emptyList(),
            claims = emptyList(),
            settings = null,
            profiles = emptyList(),
            lastSyncStatus = "Synced 2024-01-01T00:00:00Z",
        )

        assertEquals(1, details.totalFailed)
        assertEquals(1, details.totalPending)
        assertEquals(1, details.pendingByType.first { it.entityType == SyncEntityType.TASKS }.pendingCount)
        assertEquals(1, details.pendingByType.first { it.entityType == SyncEntityType.TASKS }.syncedCount)
        assertEquals("Network timeout", details.failedRows.single().errorMessage)
        assertNotNull(details.lastSuccessfulSyncAt)
        // Unused entity types stay off the list.
        assertTrue(details.pendingByType.none { it.entityType == SyncEntityType.WORKPLACES })
    }

    @Test
    fun `surfaces a pending workplace that belongs to a work profile`() {
        val workplace = WorkplaceEntity(
            localId = "wp-1",
            remoteId = null,
            userId = "u1",
            name = "Clinic",
            regionCode = "IL",
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            employmentStartDate = null,
            isDefault = true,
            isArchived = false,
            createdAt = 1L,
            updatedAt = 2L,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_CREATE,
            lastSyncError = null,
            lastSyncedAt = null,
        )

        val details = SyncDetailsBuilder.build(
            tasks = emptyList(),
            shifts = emptyList(),
            claims = emptyList(),
            settings = null,
            profiles = emptyList(),
            workplaces = listOf(workplace),
            lastSyncStatus = null,
        )

        assertEquals(1, details.totalPending)
        assertEquals(
            1,
            details.pendingByType.first { it.entityType == SyncEntityType.WORKPLACES }.pendingCount,
        )
    }

    @Test
    fun `surfaces a failed workplace tombstone that live observers would omit`() {
        val tombstone = WorkplaceEntity(
            localId = "wp-gone",
            remoteId = "wp-remote",
            userId = "u1",
            name = "Old clinic",
            regionCode = "IL",
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            employmentStartDate = null,
            isDefault = false,
            isArchived = true,
            createdAt = 1L,
            updatedAt = 2L,
            deletedAt = 3L,
            syncStatus = SyncStatus.FAILED,
            lastSyncError = "JWT expired",
            lastSyncedAt = 1L,
        )

        val details = SyncDetailsBuilder.build(
            tasks = emptyList(),
            shifts = emptyList(),
            claims = emptyList(),
            settings = null,
            profiles = emptyList(),
            workplaces = listOf(tombstone),
            lastSyncStatus = null,
        )

        assertEquals(1, details.totalPending)
        assertEquals(1, details.totalFailed)
        assertEquals("JWT expired", details.failedRows.single().errorMessage)
        assertEquals(SyncEntityType.WORKPLACES, details.failedRows.single().entityType)
    }
}
