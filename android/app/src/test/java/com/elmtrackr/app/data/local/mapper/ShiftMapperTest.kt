package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RefundAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShiftMapperTest {

    private fun entity(
        localId: String = "local-1",
        startTime: Long = 1_000_000L,
        endTime: Long? = 2_000_000L,
        breakMinutes: Int = 0,
        refundAction: String? = null,
        isSpecialDay: Boolean = false,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ) = ShiftEntity(
        localId = localId,
        remoteId = "remote-1",
        userId = "u1",
        startTime = startTime,
        endTime = endTime,
        breakMinutes = breakMinutes,
        notes = null,
        isSpecialDay = isSpecialDay,
        refundAction = refundAction,
        createdAt = startTime,
        updatedAt = startTime,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    @Test
    fun `toDomain converts epochMilli to Instant correctly`() {
        val e = entity(startTime = 1_000_000L, endTime = 2_000_000L)
        val d = e.toDomain()
        assertEquals(Instant.ofEpochMilli(1_000_000L), d.startTime)
        assertEquals(Instant.ofEpochMilli(2_000_000L), d.endTime)
    }

    @Test
    fun `toDomain preserves null endTime — shift is active`() {
        val e = entity(endTime = null)
        val d = e.toDomain()
        assertNull(d.endTime)
        assertTrue(d.isActive)
        assertFalse(d.isCompleted)
    }

    @Test
    fun `toDomain maps localId to domain id`() {
        val e = entity(localId = "my-uuid")
        assertEquals("my-uuid", e.toDomain().id)
    }

    @Test
    fun `toDomain deserialises refundAction correctly`() {
        assertEquals(RefundAction.SUBMITTED, entity(refundAction = "SUBMITTED").toDomain().refundAction)
        assertNull(entity(refundAction = null).toDomain().refundAction)
    }

    @Test
    fun `toDomain maps isSpecialDay flag`() {
        assertTrue(entity(isSpecialDay = true).toDomain().isSpecialDay)
        assertFalse(entity(isSpecialDay = false).toDomain().isSpecialDay)
    }

    @Test
    fun `toEntity preserves syncStatus`() {
        val d = entity().toDomain()
        val roundTripped = d.toEntity(syncStatus = SyncStatus.PENDING_UPDATE)
        assertEquals(SyncStatus.PENDING_UPDATE, roundTripped.syncStatus)
    }

    @Test
    fun `round-trip entity → domain → entity is stable`() {
        val original = entity(
            startTime = 5_000L,
            endTime = 10_000L,
            breakMinutes = 30,
            refundAction = "NO_RIDE_TAKEN",
            isSpecialDay = true,
        )
        val roundTripped = original.toDomain().toEntity(
            syncStatus = original.syncStatus,
            remoteId = original.remoteId,
            deletedAt = original.deletedAt,
        )
        assertEquals(original.localId, roundTripped.localId)
        assertEquals(original.startTime, roundTripped.startTime)
        assertEquals(original.endTime, roundTripped.endTime)
        assertEquals(original.breakMinutes, roundTripped.breakMinutes)
        assertEquals(original.refundAction, roundTripped.refundAction)
        assertEquals(original.isSpecialDay, roundTripped.isSpecialDay)
    }
}
