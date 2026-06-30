package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    }
}
