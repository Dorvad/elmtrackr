package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RefundAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteMapperTest {

    @Test
    fun `round trip maps shift fields and timestamps`() {
        val remote = RemoteShiftRow(
            id = "remote-1",
            userId = "user-1",
            startTime = "2024-06-01T08:00:00Z",
            endTime = "2024-06-01T16:00:00Z",
            breakMinutes = 30,
            notes = "Busy day",
            isSpecialDay = true,
            refundAction = "submitted",
            compensationProfileId = "profile-1",
            compensationSnapshotJson = null,
            taskId = "task-1",
            taskNameSnapshot = "Design",
            taskIconSnapshot = "🎨",
            taskHourlyRateSnapshot = 120.0,
            createdAt = "2024-06-01T08:00:00Z",
            updatedAt = "2024-06-01T16:00:00Z",
        )

        val local = remote.toLocalEntity(existingLocalId = "local-1")
        val insert = local.toRemoteInsert()

        assertEquals("local-1", local.localId)
        assertEquals("remote-1", local.remoteId)
        assertEquals("user-1", local.userId)
        assertEquals(SyncStatus.SYNCED, local.syncStatus)
        assertEquals(RefundAction.SUBMITTED.name, local.refundAction)
        assertEquals("user-1", insert.userId)
        assertEquals("2024-06-01T08:00:00Z", insert.startTime)
        assertEquals("submitted", insert.refundAction)
        assertEquals("Design", insert.taskNameSnapshot)
    }

    @Test
    fun `unknown refund action maps to null`() {
        val remote = RemoteShiftRow(
            id = "remote-2",
            userId = "user-1",
            startTime = "2024-06-01T08:00:00Z",
            createdAt = "2024-06-01T08:00:00Z",
            updatedAt = "2024-06-01T08:00:00Z",
            refundAction = "unknown_value",
        )

        assertNull(remote.toLocalEntity().refundAction)
    }
}
