package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RefundAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteMapperTest {

    // ── The job link ──────────────────────────────────────────────────────────
    // These are the tests whose absence let the defect ship: `workplace_id` was
    // never on the wire, and nothing asserted it survived a pull, so every pull
    // rebuilt the entity without it and silently reset what clock-in had stamped.
    // `LocalLeaveRepository` reads a null workplace as matching every workplace,
    // so the damage was an absence counted against the wrong job's leave.

    @Test
    fun `the job link survives a pull`() {
        val remote = shiftRow(workplaceId = "wp-remote")

        val local = remote.toLocalEntity(
            existingLocalId = "local-1",
            workplaceLocalId = "wp-local",
        )

        assertEquals("wp-local", local.workplaceId)
    }

    @Test
    fun `a pull keeps the local job link when the remote one has not landed yet`() {
        // The link travels as a remote id while the entity holds a local one, so a
        // shift pulled before its workplace resolves to null. The local value has
        // to survive that, or the round trip destroys it.
        val existing = shiftRow(workplaceId = "wp-remote")
            .toLocalEntity(existingLocalId = "local-1", workplaceLocalId = "wp-local")

        val reapplied = shiftRow(workplaceId = "wp-remote").toLocalEntity(
            existingLocalId = "local-1",
            workplaceLocalId = null,
            preserveLocal = existing,
        )

        assertEquals("wp-local", reapplied.workplaceId)
    }

    @Test
    fun `the job link is pushed as the remote id`() {
        val local = shiftRow().toLocalEntity(existingLocalId = "local-1")
            .copy(workplaceId = "wp-local")

        assertEquals("wp-remote", local.toRemoteInsert(workplaceRemoteId = "wp-remote").workplaceId)
        assertEquals("wp-remote", local.toRemoteUpdate(workplaceRemoteId = "wp-remote").workplaceId)
    }

    @Test
    fun `a shift with no job pushes and pulls null`() {
        // NULL is never backfilled and means "not assigned yet", so it has to stay
        // NULL rather than become a guess.
        val local = shiftRow().toLocalEntity(existingLocalId = "local-1")

        assertNull(local.workplaceId)
        assertNull(local.toRemoteInsert().workplaceId)
    }

    private fun shiftRow(workplaceId: String? = null) = RemoteShiftRow(
        id = "remote-1",
        userId = "user-1",
        startTime = "2024-06-01T08:00:00Z",
        endTime = "2024-06-01T16:00:00Z",
        breakMinutes = 30,
        workplaceId = workplaceId,
        createdAt = "2024-06-01T08:00:00Z",
        updatedAt = "2024-06-01T16:00:00Z",
    )

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

    /**
     * The remote shift schema has no project columns, so a pull carries no value
     * for the project link or the compensation source. Rebuilding a row without
     * preserving the local values would drop a shift's project and turn tracked
     * project time back into paid hourly work on the next sync.
     */
    @Test
    fun `a pull preserves the local project link and compensation source`() {
        val existing = RemoteShiftRow(
            id = "remote-1",
            userId = "user-1",
            startTime = "2024-06-01T08:00:00Z",
            createdAt = "2024-06-01T08:00:00Z",
            updatedAt = "2024-06-01T08:00:00Z",
        ).toLocalEntity(existingLocalId = "local-1").copy(
            projectId = "p1",
            projectNameSnapshot = "Website rebuild",
            compensationSource = "PROJECT",
        )

        val pulled = RemoteShiftRow(
            id = "remote-1",
            userId = "user-1",
            startTime = "2024-06-01T08:00:00Z",
            endTime = "2024-06-01T16:00:00Z",
            createdAt = "2024-06-01T08:00:00Z",
            updatedAt = "2024-06-01T18:00:00Z",
        ).toLocalEntity(existingLocalId = "local-1", preserveLocal = existing)

        assertEquals("p1", pulled.projectId)
        assertEquals("Website rebuild", pulled.projectNameSnapshot)
        assertEquals("PROJECT", pulled.compensationSource)
        // The remote fields still win where the remote has them.
        assertEquals(1_717_257_600_000L, pulled.endTime)
    }

    @Test
    fun `a pull with no local row leaves the project fields empty`() {
        val inserted = RemoteShiftRow(
            id = "remote-2",
            userId = "user-1",
            startTime = "2024-06-01T08:00:00Z",
            createdAt = "2024-06-01T08:00:00Z",
            updatedAt = "2024-06-01T08:00:00Z",
        ).toLocalEntity()

        assertNull(inserted.projectId)
        assertNull(inserted.projectNameSnapshot)
        // Null reads as employee work, which is what a shift from the server is.
        assertNull(inserted.compensationSource)
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

    /**
     * Every other fixture here uses `Z`, which is exactly why this gap survived:
     * PostgREST serialises `timestamptz` with a numeric offset, and `ISO_INSTANT`
     * before JDK 12 — the platform java.time on older devices, since this module
     * has no core library desugaring — accepts only `Z`. The symptom would have
     * been "sync fails on old phones only".
     */
    @Test
    fun `timestamps parse with a numeric offset as PostgREST returns them`() {
        val expected = 1_717_228_800_000L // 2024-06-01T08:00:00Z
        assertEquals(expected, isoToEpoch("2024-06-01T08:00:00Z"))
        assertEquals(expected, isoToEpoch("2024-06-01T08:00:00+00:00"))
        assertEquals(expected, isoToEpoch("2024-06-01T11:00:00+03:00"))
        assertEquals(expected, isoToEpoch("2024-06-01T08:00:00.000+00:00"))
    }

    /** A `timestamp without time zone` column arrives with no offset at all. */
    @Test
    fun `timestamps with no offset are read as UTC`() {
        assertEquals(1_717_228_800_000L, isoToEpoch("2024-06-01T08:00:00"))
    }

    @Test(expected = java.time.format.DateTimeParseException::class)
    fun `an unrecognised timestamp fails loudly rather than defaulting to the epoch`() {
        isoToEpoch("last Tuesday")
    }
}
