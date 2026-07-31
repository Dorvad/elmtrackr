package com.elmtrackr.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.ProjectBillingStatus
import com.elmtrackr.app.domain.projects.ProjectBillingStatusResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Real-SQLite persistence for projects, billing records and payments, including
 * that decimal money survives storage exactly.
 */
@RunWith(AndroidJUnit4::class)
class ProjectDaoTest {

    private lateinit var db: ElmTrackrDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var billingDao: ProjectBillingRecordDao
    private lateinit var paymentDao: ProjectPaymentDao

    private val userId = "u1"
    private val today = LocalDate.of(2026, 7, 29)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ElmTrackrDatabase::class.java,
        ).build()
        projectDao = db.projectDao()
        billingDao = db.projectBillingRecordDao()
        paymentDao = db.projectPaymentDao()
    }

    @After
    fun tearDown() = db.close()

    private fun project(
        id: String = "p1",
        currency: String = "ILS",
        entered: String = "10000",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
    ) = Project(
        id = id,
        userId = userId,
        name = "Project $id",
        clientName = "Acme",
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, mode, BigDecimal(rate), "VAT"),
        hourBudgetMinutes = 2400,
        targetHourlyRate = Money.of("250", currency),
        startDate = today.minusDays(30),
        deadline = today.plusDays(30),
        createdAt = Instant.ofEpochMilli(1_000),
        updatedAt = Instant.ofEpochMilli(1_000),
    )

    private fun billing(
        id: String = "bill-1",
        projectId: String = "p1",
        total: String = "11800",
        currency: String = "ILS",
        dueOn: LocalDate? = today.plusDays(14),
    ) = ProjectBillingRecord(
        id = id,
        userId = userId,
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal(total), currency, TaxMode.NONE, BigDecimal.ZERO),
        externalReference = "REF-$id",
        billedOn = today.minusDays(7),
        dueOn = dueOn,
        createdAt = Instant.ofEpochMilli(1_000),
        updatedAt = Instant.ofEpochMilli(1_000),
    )

    private fun payment(
        id: String,
        amount: String,
        projectId: String = "p1",
        recordId: String = "bill-1",
        currency: String = "ILS",
    ) = ProjectPayment(
        id = id,
        userId = userId,
        projectId = projectId,
        billingRecordId = recordId,
        paidOn = today,
        amount = Money.of(amount, currency),
        method = "Bank transfer",
        createdAt = Instant.ofEpochMilli(1_000),
        updatedAt = Instant.ofEpochMilli(1_000),
    )

    // ── Project persistence ───────────────────────────────────────────────────

    @Test
    fun projectPersistsAndReadsBackIdentically() = runTest {
        val original = project()
        projectDao.upsert(original.toEntity(SyncStatus.PENDING_CREATE))

        val restored = projectDao.getById(userId, "p1")!!.toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun decimalMoneySurvivesStorageExactly() = runTest {
        // An amount that no binary float can hold exactly.
        val original = project(entered = "1234.57", mode = TaxMode.INCLUSIVE, rate = "17")
        projectDao.upsert(original.toEntity())

        val row = projectDao.getByLocalId("p1")!!
        assertEquals(BigDecimal("1234.57"), row.clientTotal)
        assertEquals(row.clientTotal, row.baseFee.add(row.taxAmount))
        assertEquals(original.fee.base, row.toDomain().fee.base)
    }

    @Test
    fun projectsAreScopedToTheirUser() = runTest {
        projectDao.upsert(project(id = "mine").toEntity())
        projectDao.upsert(project(id = "theirs").copy(userId = "other").toEntity())

        assertEquals(listOf("mine"), projectDao.getProjects(userId).map { it.localId })
        assertNull(projectDao.getById(userId, "theirs"))
    }

    @Test
    fun archivedProjectsAreExcludedFromTheActiveList() = runTest {
        projectDao.upsert(project(id = "live").toEntity())
        projectDao.upsert(project(id = "old").toEntity())
        projectDao.setArchived("old", 5_000, ProjectWorkStatus.ARCHIVED.name, SyncStatus.PENDING_UPDATE, 5_000)

        assertEquals(listOf("live"), projectDao.observeActiveProjects(userId).first().map { it.localId })
        assertEquals(2, projectDao.observeProjects(userId).first().size)
    }

    @Test
    fun softDeleteHidesTheProjectButKeepsTheRow() = runTest {
        projectDao.upsert(project().toEntity())
        projectDao.softDelete("p1", 9_000, SyncStatus.PENDING_DELETE, 9_000)

        assertTrue(projectDao.observeProjects(userId).first().isEmpty())
        assertEquals(9_000L, projectDao.getByLocalId("p1")!!.deletedAt)
    }

    @Test
    fun pendingSyncProjectsAreReported() = runTest {
        projectDao.upsert(project(id = "pending").toEntity(SyncStatus.PENDING_CREATE))
        projectDao.upsert(project(id = "synced").toEntity(SyncStatus.SYNCED))

        assertEquals(listOf("pending"), projectDao.getPendingSyncProjects(userId).map { it.localId })
        assertTrue(projectDao.hasPendingSyncProjects(userId))
    }

    // ── Billing record persistence ────────────────────────────────────────────

    @Test
    fun billingRecordPersistsAndReadsBackIdentically() = runTest {
        projectDao.upsert(project().toEntity())
        val original = billing()
        billingDao.upsert(original.toEntity(SyncStatus.PENDING_CREATE))

        assertEquals(original, billingDao.getByLocalId("bill-1")!!.toDomain())
    }

    @Test
    fun aProjectSupportsMultipleBillingRecords() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing(id = "bill-1", total = "1000").toEntity())
        billingDao.upsert(billing(id = "bill-2", total = "2000").toEntity())

        val records = billingDao.getForProject("p1")
        assertEquals(2, records.size)
        assertEquals(
            BigDecimal("3000.00"),
            records.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.totalAmount) },
        )
    }

    @Test
    fun cancellingABillingRecordIsRecordedAndReversible() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing().toEntity())

        billingDao.setCancelled("bill-1", 7_000, SyncStatus.PENDING_UPDATE, 7_000)
        assertTrue(billingDao.getByLocalId("bill-1")!!.toDomain().isCancelled)

        billingDao.setCancelled("bill-1", null, SyncStatus.PENDING_UPDATE, 8_000)
        assertTrue(billingDao.getByLocalId("bill-1")!!.toDomain().isActive)
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    @Test
    fun multiplePaymentsPersistAgainstOneBillingRecord() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing().toEntity())
        paymentDao.upsert(payment("pay-1", "4000").toEntity())
        paymentDao.upsert(payment("pay-2", "3000").toEntity())
        paymentDao.upsert(payment("pay-3", "1800").toEntity())

        val payments = paymentDao.getForBillingRecord("bill-1")
        assertEquals(3, payments.size)
        assertEquals(
            BigDecimal("8800.00"),
            payments.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) },
        )
    }

    @Test
    fun paymentAmountsSurviveStorageExactly() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing().toEntity())
        paymentDao.upsert(payment("pay-1", "0.01").toEntity())
        paymentDao.upsert(payment("pay-2", "3933.33").toEntity())

        assertEquals(BigDecimal("0.01"), paymentDao.getByLocalId("pay-1")!!.amount)
        assertEquals(BigDecimal("3933.33"), paymentDao.getByLocalId("pay-2")!!.amount)
    }

    // ── Derived status over stored data ───────────────────────────────────────

    @Test
    fun partialPaymentDerivesPartiallyPaidFromStoredRows() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing(total = "11800").toEntity())
        paymentDao.upsert(payment("pay-1", "5000").toEntity())

        val state = ProjectBillingStatusResolver.resolve(
            currencyCode = "ILS",
            billingRecords = billingDao.getForProject("p1").map { it.toDomain() },
            payments = paymentDao.getForProject("p1").map { it.toDomain() },
            today = today,
        )
        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, state.status)
        assertEquals(Money.of("6800.00", "ILS"), state.outstanding)
    }

    @Test
    fun fullPaymentDerivesPaidFromStoredRows() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing(total = "11800").toEntity())
        paymentDao.upsert(payment("pay-1", "5900").toEntity())
        paymentDao.upsert(payment("pay-2", "5900").toEntity())

        val state = ProjectBillingStatusResolver.resolve(
            "ILS",
            billingDao.getForProject("p1").map { it.toDomain() },
            paymentDao.getForProject("p1").map { it.toDomain() },
            today,
        )
        assertEquals(ProjectBillingStatus.PAID, state.status)
        assertTrue(state.outstanding.isZero)
    }

    @Test
    fun overdueDerivesFromAStoredPastDueDate() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing(dueOn = today.minusDays(5)).toEntity())

        val state = ProjectBillingStatusResolver.resolve(
            "ILS",
            billingDao.getForProject("p1").map { it.toDomain() },
            emptyList(),
            today,
        )
        assertEquals(ProjectBillingStatus.OVERDUE, state.status)
    }

    @Test
    fun aCancelledStoredRecordIsNotOverdue() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing(dueOn = today.minusDays(50)).toEntity())
        billingDao.setCancelled("bill-1", 1_000, SyncStatus.PENDING_UPDATE, 1_000)

        val state = ProjectBillingStatusResolver.resolve(
            "ILS",
            billingDao.getForProject("p1").map { it.toDomain() },
            emptyList(),
            today,
        )
        assertEquals(ProjectBillingStatus.NOT_BILLED, state.status)
    }

    // ── Currencies ────────────────────────────────────────────────────────────

    @Test
    fun projectsInDifferentCurrenciesCoexist() = runTest {
        projectDao.upsert(project(id = "ils", currency = "ILS", entered = "10000").toEntity())
        projectDao.upsert(project(id = "usd", currency = "USD", entered = "2500").toEntity())
        projectDao.upsert(project(id = "jpy", currency = "JPY", entered = "300000").toEntity())

        val byCurrency = projectDao.getProjects(userId).groupBy { it.currencyCode }
        assertEquals(setOf("ILS", "USD", "JPY"), byCurrency.keys)
        // Zero-decimal currency keeps no minor units through storage.
        assertEquals(BigDecimal("300000"), projectDao.getByLocalId("jpy")!!.baseFee)
    }

    // ── Cascades ──────────────────────────────────────────────────────────────

    @Test
    fun softDeletingAProjectCascadesToBillingAndPayments() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing().toEntity())
        paymentDao.upsert(payment("pay-1", "100").toEntity())

        val now = 9_000L
        paymentDao.softDeleteForProject("p1", now, SyncStatus.PENDING_DELETE, now)
        billingDao.softDeleteForProject("p1", now, SyncStatus.PENDING_DELETE, now)
        projectDao.softDelete("p1", now, SyncStatus.PENDING_DELETE, now)

        assertTrue(billingDao.getForProject("p1").isEmpty())
        assertTrue(paymentDao.getForProject("p1").isEmpty())
        assertEquals(now, paymentDao.getByLocalId("pay-1")!!.deletedAt)
    }

    @Test
    fun deleteAllForUserClearsEveryProjectTable() = runTest {
        projectDao.upsert(project().toEntity())
        billingDao.upsert(billing().toEntity())
        paymentDao.upsert(payment("pay-1", "100").toEntity())

        paymentDao.deleteAllForUser(userId)
        billingDao.deleteAllForUser(userId)
        projectDao.deleteAllForUser(userId)

        assertTrue(projectDao.getAllForUser(userId).isEmpty())
        assertTrue(billingDao.getAllForUser(userId).isEmpty())
        assertTrue(paymentDao.getAllForUser(userId).isEmpty())
    }
}
