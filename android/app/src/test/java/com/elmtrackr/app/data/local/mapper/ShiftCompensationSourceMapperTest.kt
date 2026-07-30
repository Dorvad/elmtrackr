package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * How the compensation source crosses the persistence boundary.
 *
 * The column is nullable and never backfilled, so the mapper is what makes NULL
 * mean employee work. If that ever changed, every shift recorded before the
 * column existed would stop being paid.
 */
class ShiftCompensationSourceMapperTest {

    private fun entity(compensationSource: String?, projectId: String? = null) = ShiftEntity(
        localId = "s1",
        remoteId = null,
        userId = "u1",
        startTime = 1_000L,
        endTime = 29_800_000L,
        breakMinutes = 30,
        notes = null,
        isSpecialDay = false,
        refundAction = null,
        compensationProfileId = "profile-1",
        compensationSnapshotJson = null,
        taskId = "task-1",
        taskNameSnapshot = "Delivery",
        taskIconSnapshot = "bike",
        taskHourlyRateSnapshot = 70.0,
        projectId = projectId,
        projectNameSnapshot = projectId?.let { "Website rebuild" },
        compensationSource = compensationSource,
        createdAt = 1L,
        updatedAt = 2L,
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    @Test
    fun `a null column reads as employee work so existing shifts keep their pay`() {
        val shift = entity(compensationSource = null).toDomain()
        assertEquals(CompensationSource.EMPLOYEE, shift.compensationSource)
        assertTrue(shift.isEmployeePaid)
    }

    @Test
    fun `a stored project value reads as project time`() {
        val shift = entity(compensationSource = "PROJECT", projectId = "p1").toDomain()
        assertEquals(CompensationSource.PROJECT, shift.compensationSource)
        assertEquals("p1", shift.projectId)
        assertEquals("Website rebuild", shift.projectNameSnapshot)
    }

    @Test
    fun `an unrecognised stored value reads as employee work`() {
        val shift = entity(compensationSource = "CLIENT_FEE").toDomain()
        assertEquals(CompensationSource.EMPLOYEE, shift.compensationSource)
    }

    @Test
    fun `writing a shift stores the source explicitly`() {
        val shift = Shift(
            id = "s1",
            userId = "u1",
            startTime = Instant.ofEpochMilli(1_000L),
            endTime = Instant.ofEpochMilli(29_800_000L),
            compensationSource = CompensationSource.PROJECT,
            projectId = "p1",
            projectNameSnapshot = "Website rebuild",
        )
        assertEquals("PROJECT", shift.toEntity().compensationSource)
    }

    @Test
    fun `an employee shift is written as EMPLOYEE rather than left null`() {
        // New rows are explicit. NULL is only the meaning of rows written before
        // the column existed, not something the app keeps producing.
        val shift = Shift(
            id = "s1",
            userId = "u1",
            startTime = Instant.ofEpochMilli(1_000L),
            endTime = Instant.ofEpochMilli(29_800_000L),
        )
        assertEquals("EMPLOYEE", shift.toEntity().compensationSource)
    }

    /**
     * Process death and restart: the active shift is reloaded from Room like any
     * other, and its project context has to come back with it. If any of these
     * fields were lost, the shift would resume as unpaid hourly work.
     */
    @Test
    fun `an active project shift survives a reload from the database`() {
        val active = entity(compensationSource = "PROJECT", projectId = "p1")
            .copy(endTime = null)

        val reloaded = active.toDomain()

        assertTrue(reloaded.isActive)
        assertEquals(CompensationSource.PROJECT, reloaded.compensationSource)
        assertTrue(reloaded.isProjectTime)
        assertEquals("p1", reloaded.projectId)
        assertEquals("Website rebuild", reloaded.projectNameSnapshot)
        // And writing it back out preserves all of it, so a clock-out after the
        // restart still lands on the project.
        val rewritten = reloaded.toEntity(syncStatus = active.syncStatus)
        assertEquals("PROJECT", rewritten.compensationSource)
        assertEquals("p1", rewritten.projectId)
        assertEquals(null, rewritten.endTime)
    }

    @Test
    fun `a project shift round-trips through the entity unchanged`() {
        val original = entity(compensationSource = "PROJECT", projectId = "p1")
        val roundTripped = original.toDomain().toEntity(
            syncStatus = original.syncStatus,
            remoteId = original.remoteId,
            deletedAt = original.deletedAt,
            lastSyncError = original.lastSyncError,
            lastSyncedAt = original.lastSyncedAt,
        )
        assertEquals(original, roundTripped)
    }
}
