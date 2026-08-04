package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.DirectTransactionRunner
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.fake.FakeProjectBillingRecordDao
import com.elmtrackr.app.fake.FakeProjectDao
import com.elmtrackr.app.fake.FakeProjectPaymentDao
import com.elmtrackr.app.fake.FakeShiftDao
import com.elmtrackr.app.fake.FakeSyncTrigger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Deleting a project.
 *
 * Deletion used to be refused unless the project was an untouched draft, so a
 * project used once could never be removed — only archived. Any project can be
 * deleted now, which makes what happens to the surrounding data the thing worth
 * pinning down:
 *
 * The shifts are the user's record of hours worked and survive, recast as ordinary
 * employee-paid work. Leaving them pointing at a deleted project would put them in
 * the one state ShiftCompensationReassignment exists to prevent — belonging to
 * nothing and paid by nothing.
 *
 * The billing records and payments describe what this client was asked to pay.
 * Without the project they describe nothing, so they go.
 */
class LocalProjectsRepositoryDeleteTest {

    private val projectDao = FakeProjectDao()
    private val billingDao = FakeProjectBillingRecordDao()
    private val paymentDao = FakeProjectPaymentDao()
    private val shiftDao = FakeShiftDao()
    private val syncTrigger = FakeSyncTrigger()

    private val repository = LocalProjectsRepository(
        projectDao = projectDao,
        billingRecordDao = billingDao,
        paymentDao = paymentDao,
        shiftDao = shiftDao,
        transactionRunner = DirectTransactionRunner,
        syncTrigger = syncTrigger,
    )

    private fun project(localId: String = "p1", workStatus: String = "ACTIVE") = ProjectEntity(
        localId = localId,
        remoteId = null,
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        clientId = null,
        description = null,
        workStatus = workStatus,
        currencyCode = "USD",
        baseFee = BigDecimal("10000.00"),
        taxLabel = "VAT",
        taxRatePercent = BigDecimal("18"),
        taxMode = "EXCLUSIVE",
        taxAmount = BigDecimal("1800.00"),
        clientTotal = BigDecimal("11800.00"),
        hourBudgetMinutes = null,
        targetHourlyRate = null,
        startDate = null,
        deadline = null,
        completionDate = null,
        notes = null,
        createdAt = 1,
        updatedAt = 2,
        archivedAt = null,
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = 3,
    )

    private fun projectShift(
        localId: String,
        projectId: String? = "p1",
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ) = ShiftEntity(
        localId = localId,
        remoteId = "r-$localId",
        userId = "u1",
        startTime = 1_000L,
        endTime = 2_000L,
        breakMinutes = 0,
        notes = null,
        isSpecialDay = false,
        refundAction = null,
        compensationProfileId = null,
        compensationSnapshotJson = """{"kind":"project"}""",
        taskId = "t1",
        taskNameSnapshot = "Design",
        taskIconSnapshot = "🎨",
        taskHourlyRateSnapshot = null,
        projectId = projectId,
        projectNameSnapshot = if (projectId != null) "Website rebuild" else null,
        compensationSource = if (projectId != null) "PROJECT" else "EMPLOYEE",
        createdAt = 1,
        updatedAt = 2,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = 3,
    )

    @Test
    fun `an active project with tracked time can be deleted`() = runTest {
        projectDao.upsert(project(workStatus = "ACTIVE"))
        shiftDao.insertShift(projectShift("s1"))

        val released = repository.deleteProject("u1", "p1")

        assertEquals(1, released)
        assertNotNull("the row is soft-deleted, not dropped", projectDao.getByLocalId("p1"))
        assertNotNull(projectDao.getByLocalId("p1")!!.deletedAt)
    }

    @Test
    fun `deleting a project keeps its shifts and makes them employee paid`() = runTest {
        projectDao.upsert(project())
        shiftDao.insertShift(projectShift("s1"))
        shiftDao.insertShift(projectShift("s2"))

        repository.deleteProject("u1", "p1")

        listOf("s1", "s2").forEach { id ->
            val shift = shiftDao.getShiftById(id)
            assertNotNull("shift $id must survive the project", shift)
            assertNull("shift $id still points at the project", shift!!.projectId)
            assertNull(shift.projectNameSnapshot)
            assertEquals("EMPLOYEE", shift.compensationSource)
            assertNull("a project-time snapshot must not survive", shift.compensationSnapshotJson)
            assertNull("the hours must not be deleted", shift.deletedAt)
        }
    }

    /** The task records what was worked on, which stays true whoever pays for it. */
    @Test
    fun `releasing a shift keeps its task link`() = runTest {
        projectDao.upsert(project())
        shiftDao.insertShift(projectShift("s1"))

        repository.deleteProject("u1", "p1")

        val shift = shiftDao.getShiftById("s1")!!
        assertEquals("t1", shift.taskId)
        assertEquals("Design", shift.taskNameSnapshot)
    }

    @Test
    fun `a released shift is queued for upload`() = runTest {
        projectDao.upsert(project())
        shiftDao.insertShift(projectShift("s1", syncStatus = SyncStatus.SYNCED))

        repository.deleteProject("u1", "p1")

        assertEquals(SyncStatus.PENDING_UPDATE, shiftDao.getShiftById("s1")!!.syncStatus)
    }

    /**
     * A shift that has never reached the server must stay PENDING_CREATE: demoting
     * it to PENDING_UPDATE would make the push try to update a row that does not
     * exist remotely.
     */
    @Test
    fun `a never-synced shift stays pending create`() = runTest {
        projectDao.upsert(project())
        shiftDao.insertShift(projectShift("s1", syncStatus = SyncStatus.PENDING_CREATE))

        repository.deleteProject("u1", "p1")

        assertEquals(SyncStatus.PENDING_CREATE, shiftDao.getShiftById("s1")!!.syncStatus)
    }

    @Test
    fun `shifts belonging to another project are untouched`() = runTest {
        projectDao.upsert(project(localId = "p1"))
        projectDao.upsert(project(localId = "p2"))
        shiftDao.insertShift(projectShift("mine", projectId = "p1"))
        shiftDao.insertShift(projectShift("theirs", projectId = "p2"))

        val released = repository.deleteProject("u1", "p1")

        assertEquals(1, released)
        assertEquals("p2", shiftDao.getShiftById("theirs")!!.projectId)
        assertNotNull(projectDao.getByLocalId("p2"))
        assertNull(projectDao.getByLocalId("p2")!!.deletedAt)
    }

    @Test
    fun `deleting an unknown project changes nothing`() = runTest {
        shiftDao.insertShift(projectShift("s1"))

        val released = repository.deleteProject("u1", "missing")

        assertEquals(0, released)
        assertEquals("p1", shiftDao.getShiftById("s1")!!.projectId)
    }

    @Test
    fun `a project with no tracked time reports nothing released`() = runTest {
        projectDao.upsert(project(workStatus = "DRAFT"))

        assertEquals(0, repository.deleteProject("u1", "p1"))
    }
}
