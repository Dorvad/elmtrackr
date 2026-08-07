package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningShiftResolverTest {

    @Test
    fun `no open shifts is nothing to resolve`() {
        assertNull(RunningShiftResolver.resolve(emptyList()))
    }

    @Test
    fun `a single open shift is left exactly as it is`() {
        val only = openShift("a", startTime = 100L, notes = "kept")

        val resolution = RunningShiftResolver.resolve(listOf(only))!!

        assertFalse(resolution.hasDuplicates)
        assertEquals(only, resolution.winner)
    }

    @Test
    fun `the earliest clock-in wins`() {
        val early = openShift("early", startTime = 100L)
        val late = openShift("late", startTime = 500L)

        val resolution = RunningShiftResolver.resolve(listOf(late, early))!!

        assertEquals("early", resolution.winner.localId)
        assertEquals(listOf("late"), resolution.duplicates.map { it.localId })
    }

    /**
     * Both devices must reach the same answer from the same rows, or they never
     * converge — so the result cannot depend on the order rows happen to arrive in.
     */
    @Test
    fun `the winner does not depend on input order`() {
        val a = openShift("a", startTime = 300L)
        val b = openShift("b", startTime = 100L)
        val c = openShift("c", startTime = 200L)

        val forwards = RunningShiftResolver.resolve(listOf(a, b, c))!!
        val backwards = RunningShiftResolver.resolve(listOf(c, b, a))!!

        assertEquals(forwards.winner.localId, backwards.winner.localId)
        assertEquals(
            forwards.duplicates.map { it.localId }.toSet(),
            backwards.duplicates.map { it.localId }.toSet(),
        )
    }

    /**
     * Local ids differ between devices — a pulled row gets a fresh UUID — so a tie
     * has to break on something both devices see the same way.
     */
    @Test
    fun `ties break on the remote id so every device agrees`() {
        val first = openShift("local-x", startTime = 100L).copy(remoteId = "remote-a")
        val second = openShift("local-y", startTime = 100L).copy(remoteId = "remote-b")

        val resolution = RunningShiftResolver.resolve(listOf(second, first))!!

        assertEquals("remote-a", resolution.winner.remoteId)
    }

    @Test
    fun `detail typed on a losing clock-in is carried onto the winner`() {
        val bare = openShift("bare", startTime = 100L)
        val detailed = openShift("detailed", startTime = 500L, notes = "site visit").copy(
            taskId = "task-1",
            taskNameSnapshot = "Deliveries",
        )

        val resolution = RunningShiftResolver.resolve(listOf(bare, detailed))!!

        assertEquals("bare", resolution.winner.localId)
        assertEquals("site visit", resolution.winner.notes)
        assertEquals("task-1", resolution.winner.taskId)
        assertEquals("Deliveries", resolution.winner.taskNameSnapshot)
    }

    @Test
    fun `the winner's own detail is never replaced by a loser's`() {
        val winner = openShift("winner", startTime = 100L, notes = "mine").copy(taskId = "task-mine")
        val loser = openShift("loser", startTime = 500L, notes = "theirs").copy(taskId = "task-theirs")

        val resolution = RunningShiftResolver.resolve(listOf(winner, loser))!!

        assertEquals("mine", resolution.winner.notes)
        assertEquals("task-mine", resolution.winner.taskId)
    }

    @Test
    fun `closed and deleted shifts are not candidates`() {
        val closed = openShift("closed", startTime = 50L).copy(endTime = 900L)
        val deleted = openShift("deleted", startTime = 60L).copy(deletedAt = 900L)
        val running = openShift("running", startTime = 100L)

        val resolution = RunningShiftResolver.resolve(listOf(closed, deleted, running))!!

        assertEquals("running", resolution.winner.localId)
        assertTrue(resolution.duplicates.isEmpty())
    }

    private fun openShift(
        localId: String,
        startTime: Long,
        notes: String? = null,
    ) = ShiftEntity(
        localId = localId,
        remoteId = null,
        userId = "user-1",
        startTime = startTime,
        endTime = null,
        breakMinutes = 0,
        notes = notes,
        isSpecialDay = false,
        refundAction = null,
        compensationProfileId = null,
        compensationSnapshotJson = null,
        taskId = null,
        taskNameSnapshot = null,
        taskIconSnapshot = null,
        taskHourlyRateSnapshot = null,
        createdAt = startTime,
        updatedAt = startTime,
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = null,
    )
}
