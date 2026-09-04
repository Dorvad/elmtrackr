package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `tasks.compensation_profile_id` is a real foreign key, and it was pushed as a
 * raw local id.
 *
 * Local and remote profile ids coincide only for a profile *this* device created —
 * the insert sends `id = localId`. A profile **pulled** from another device gets a
 * fresh random local id, so a task scoped to it pushed an id no server row had,
 * was rejected 23503, and stayed FAILED forever: retried every fifteen minutes and
 * counted in "unsynced changes" for good. The pull was the mirror image, writing
 * the remote id into the local column where it matched no local profile, so the
 * task silently fell back to the default job.
 *
 * Every other parent link in the pipeline goes through `SyncIdMapper`. This one
 * did not, and nothing tested it.
 */
class RemoteTaskProfileScopeTest {

    private fun task(profileLocalId: String?) = TaskEntity(
        localId = "task-local",
        remoteId = null,
        userId = "u1",
        name = "Design",
        icon = "🎨",
        color = null,
        hourlyRate = 100.0,
        compensationProfileId = profileLocalId,
        isArchived = false,
        lastUsedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        syncStatus = SyncStatus.PENDING_CREATE,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    private fun row(profileRemoteId: String?) = RemoteTaskRow(
        id = "task-remote",
        userId = "u1",
        name = "Design",
        icon = "🎨",
        color = null,
        hourlyRate = 100.0,
        compensationProfileId = profileRemoteId,
        isArchived = false,
        lastUsedAt = null,
        createdAt = "2024-06-01T08:00:00Z",
        updatedAt = "2024-06-01T08:00:00Z",
    )

    @Test
    fun `the push sends the remote profile id, not the local one`() {
        val t = task(profileLocalId = "profile-local")

        assertEquals(
            "profile-remote",
            t.toRemoteInsert(compensationProfileRemoteId = "profile-remote").compensationProfileId,
        )
        assertEquals(
            "profile-remote",
            t.toRemoteUpdate(compensationProfileRemoteId = "profile-remote").compensationProfileId,
        )
    }

    @Test
    fun `the pull writes the local profile id, not the remote one`() {
        val local = row(profileRemoteId = "profile-remote")
            .toLocalEntity(compensationProfileLocalId = "profile-local")

        assertEquals("profile-local", local.compensationProfileId)
    }

    @Test
    fun `a pull keeps the task's scope when the profile has not been pulled yet`() {
        val existing = task(profileLocalId = "profile-local")

        val reapplied = row(profileRemoteId = "profile-remote").toLocalEntity(
            existingLocalId = existing.localId,
            compensationProfileLocalId = null,
            preserveLocal = existing,
        )

        assertEquals(
            "the task must not fall back to the default job",
            "profile-local",
            reapplied.compensationProfileId,
        )
    }

    @Test
    fun `an unscoped task stays unscoped`() {
        // NULL is read as the default work profile, so an upgrading user keeps
        // their whole task list. It must not become a guess.
        assertNull(task(profileLocalId = null).toRemoteInsert().compensationProfileId)
        assertNull(row(profileRemoteId = null).toLocalEntity().compensationProfileId)
    }

    // ── The classifier that keeps such a row retryable ────────────────────────

    @Test
    fun `a foreign-key violation is recognised`() {
        val pg = RuntimeException(
            """insert or update on table "tasks" violates foreign key constraint """ +
                """"tasks_compensation_profile_id_fkey" (SQLSTATE 23503)""",
        )

        assertTrue(RemoteSyncErrors.isForeignKeyViolation(pg))
        // And is not confused with the unique violation that means "the previous
        // attempt landed", which is adopted rather than retried.
        assertFalse(RemoteSyncErrors.isUniqueViolation(pg))
    }

    @Test
    fun `a unique violation is not a foreign-key violation`() {
        val pg = RuntimeException("""duplicate key value violates unique constraint "tasks_pkey" (23505)""")

        assertFalse(RemoteSyncErrors.isForeignKeyViolation(pg))
        assertTrue(RemoteSyncErrors.isUniqueViolation(pg))
    }
}
