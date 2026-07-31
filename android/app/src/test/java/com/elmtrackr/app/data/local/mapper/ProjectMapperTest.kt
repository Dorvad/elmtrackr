package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.sync.toBackupRow
import com.elmtrackr.app.data.sync.toEntity
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ProjectMapperTest {

    private fun project(
        currency: String = "ILS",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
        entered: String = "10000",
    ) = Project(
        id = "p1",
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        description = "Five pages plus a CMS",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal(entered), currency, mode, BigDecimal(rate), "VAT"),
        hourBudgetMinutes = 60 * 40,
        targetHourlyRate = Money.of("250", currency),
        startDate = LocalDate.of(2026, 7, 1),
        deadline = LocalDate.of(2026, 8, 31),
        completionDate = null,
        notes = "Kickoff done",
        createdAt = Instant.ofEpochMilli(1_000),
        updatedAt = Instant.ofEpochMilli(2_000),
    )

    // ── Projects ──────────────────────────────────────────────────────────────

    @Test
    fun `project round-trips through the entity unchanged`() {
        val original = project()
        val restored = original.toEntity().toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun `the entity stores the derived tax and client total from the fee`() {
        val entity = project().toEntity()
        assertEquals(BigDecimal("10000.00"), entity.baseFee)
        assertEquals(BigDecimal("1800.00"), entity.taxAmount)
        assertEquals(BigDecimal("11800.00"), entity.clientTotal)
        assertEquals("EXCLUSIVE", entity.taxMode)
        assertEquals(BigDecimal("18"), entity.taxRatePercent)
    }

    @Test
    fun `stored derived amounts cannot drift from the calculator`() {
        // The mapper is the only writer of taxAmount and clientTotal, so whatever
        // ProjectFee computed is exactly what lands on the row.
        listOf(TaxMode.NONE, TaxMode.EXCLUSIVE, TaxMode.INCLUSIVE).forEach { mode ->
            val fee = ProjectFee.from(BigDecimal("100"), "ILS", mode, BigDecimal("17"), "VAT")
            val entity = project().copy(fee = fee).toEntity()
            assertEquals(fee.base.amount, entity.baseFee)
            assertEquals(fee.tax.amount, entity.taxAmount)
            assertEquals(fee.clientTotal.amount, entity.clientTotal)
            assertEquals(
                entity.clientTotal,
                entity.baseFee.add(entity.taxAmount),
            )
        }
    }

    @Test
    fun `an inclusive fee round-trips with its repeating-decimal base`() {
        val original = project(mode = TaxMode.INCLUSIVE, rate = "17", entered = "100")
        val restored = original.toEntity().toDomain()
        assertEquals(Money.of("85.47", "ILS"), restored.fee.base)
        assertEquals(Money.of("14.53", "ILS"), restored.fee.tax)
        assertEquals(Money.of("100.00", "ILS"), restored.fee.clientTotal)
        assertEquals(original, restored)
    }

    @Test
    fun `each project keeps its own currency through persistence`() {
        listOf("ILS", "USD", "EUR", "JPY").forEach { code ->
            val restored = project(currency = code).toEntity().toDomain()
            assertEquals(code, restored.currencyCode)
            assertEquals(code, restored.fee.base.currencyCode)
            assertEquals(code, restored.targetHourlyRate?.currencyCode)
        }
    }

    @Test
    fun `optional fields survive as nulls`() {
        val sparse = Project(
            id = "p2",
            userId = "u1",
            name = "Minimal",
            fee = ProjectFee.zero("USD"),
        )
        val restored = sparse.toEntity().toDomain()
        assertNull(restored.clientName)
        assertNull(restored.clientId)
        assertNull(restored.description)
        assertNull(restored.hourBudgetMinutes)
        assertNull(restored.targetHourlyRate)
        assertNull(restored.startDate)
        assertNull(restored.deadline)
        assertNull(restored.completionDate)
        assertNull(restored.notes)
        assertNull(restored.archivedAt)
        assertEquals(sparse, restored)
    }

    @Test
    fun `work status round-trips for every value`() {
        ProjectWorkStatus.entries.forEach { status ->
            val restored = project().copy(workStatus = status).toEntity().toDomain()
            assertEquals(status, restored.workStatus)
        }
    }

    @Test
    fun `an unknown persisted work status falls back to active`() {
        val entity = project().toEntity().copy(workStatus = "ON_HOLD_MAYBE")
        assertEquals(ProjectWorkStatus.ACTIVE, entity.toDomain().workStatus)
    }

    @Test
    fun `archiving is recorded as a timestamp and a status`() {
        val archived = project().copy(
            workStatus = ProjectWorkStatus.ARCHIVED,
            archivedAt = Instant.ofEpochMilli(9_000),
        )
        val restored = archived.toEntity().toDomain()
        assertEquals(Instant.ofEpochMilli(9_000), restored.archivedAt)
        assertEquals(true, restored.isArchived)
    }

    @Test
    fun `dates are stored as epoch days`() {
        val entity = project().toEntity()
        assertEquals(LocalDate.of(2026, 7, 1).toEpochDay(), entity.startDate)
        assertEquals(LocalDate.of(2026, 8, 31).toEpochDay(), entity.deadline)
    }

    @Test
    fun `project backup row round-trips`() {
        val entity = project().toEntity(syncStatus = SyncStatus.PENDING_CREATE)
        val restored = entity.toBackupRow().toEntity("u1")
        assertEquals(entity.copy(lastSyncError = null), restored)
    }

    // ── Billing records ───────────────────────────────────────────────────────

    private fun billingRecord(currency: String = "ILS") = ProjectBillingRecord(
        id = "bill-1",
        userId = "u1",
        projectId = "p1",
        fee = ProjectFee.from(BigDecimal("10000"), currency, TaxMode.EXCLUSIVE, BigDecimal("18"), "VAT"),
        externalReference = "2026-014",
        billedOn = LocalDate.of(2026, 7, 20),
        dueOn = LocalDate.of(2026, 8, 19),
        cancelledAt = null,
        createdAt = Instant.ofEpochMilli(1_000),
        updatedAt = Instant.ofEpochMilli(1_000),
    )

    @Test
    fun `billing record round-trips through the entity unchanged`() {
        val original = billingRecord()
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `billing record snapshots every money field`() {
        val entity = billingRecord().toEntity()
        assertEquals(BigDecimal("10000.00"), entity.baseAmount)
        assertEquals(BigDecimal("1800.00"), entity.taxAmount)
        assertEquals(BigDecimal("11800.00"), entity.totalAmount)
        assertEquals("VAT", entity.taxLabel)
        assertEquals(BigDecimal("18"), entity.taxRatePercent)
        assertEquals("EXCLUSIVE", entity.taxMode)
        assertEquals("ILS", entity.currencyCode)
        assertEquals("2026-014", entity.externalReference)
    }

    @Test
    fun `cancelled state round-trips`() {
        val cancelled = billingRecord().copy(cancelledAt = Instant.ofEpochMilli(5_000))
        val restored = cancelled.toEntity().toDomain()
        assertEquals(Instant.ofEpochMilli(5_000), restored.cancelledAt)
        assertEquals(true, restored.isCancelled)
        assertEquals(false, restored.isActive)
    }

    @Test
    fun `billing record backup row round-trips`() {
        val entity = billingRecord().toEntity(syncStatus = SyncStatus.PENDING_UPDATE)
        assertEquals(entity.copy(lastSyncError = null), entity.toBackupRow().toEntity("u1"))
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    private fun payment(currency: String = "ILS") = ProjectPayment(
        id = "pay-1",
        userId = "u1",
        projectId = "p1",
        billingRecordId = "bill-1",
        paidOn = LocalDate.of(2026, 7, 25),
        amount = Money.of("5900", currency),
        method = "Bank transfer",
        externalReference = "TXN-99",
        notes = "First half",
        createdAt = Instant.ofEpochMilli(1_000),
        updatedAt = Instant.ofEpochMilli(1_000),
    )

    @Test
    fun `payment round-trips through the entity unchanged`() {
        val original = payment()
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `payment keeps its currency and exact amount`() {
        val entity = payment("USD").toEntity()
        assertEquals(BigDecimal("5900.00"), entity.amount)
        assertEquals("USD", entity.currencyCode)
        assertEquals("USD", entity.toDomain().amount.currencyCode)
    }

    @Test
    fun `payment backup row round-trips`() {
        val entity = payment().toEntity(syncStatus = SyncStatus.PENDING_CREATE)
        assertEquals(entity.copy(lastSyncError = null), entity.toBackupRow().toEntity("u1"))
    }

    // ── Shift project link ────────────────────────────────────────────────────

    @Test
    fun `an existing shift has no project link`() {
        val shift = Shift(
            id = "s1",
            userId = "u1",
            startTime = Instant.ofEpochMilli(1_000),
            endTime = Instant.ofEpochMilli(5_000),
        )
        val entity = shift.toEntity()
        assertNull(entity.projectId)
        assertNull(entity.projectNameSnapshot)
        assertNull(entity.toDomain().projectId)
    }

    @Test
    fun `a shift project link round-trips`() {
        val shift = Shift(
            id = "s1",
            userId = "u1",
            startTime = Instant.ofEpochMilli(1_000),
            endTime = Instant.ofEpochMilli(5_000),
            projectId = "p1",
            projectNameSnapshot = "Website rebuild",
        )
        val restored = shift.toEntity().toDomain()
        assertEquals("p1", restored.projectId)
        assertEquals("Website rebuild", restored.projectNameSnapshot)
    }

    @Test
    fun `a shift carries a project and a task independently`() {
        val shift = Shift(
            id = "s1",
            userId = "u1",
            startTime = Instant.ofEpochMilli(1_000),
            endTime = Instant.ofEpochMilli(5_000),
            taskId = "t1",
            taskNameSnapshot = "Design",
            taskHourlyRateSnapshot = 120.0,
            projectId = "p1",
            projectNameSnapshot = "Website rebuild",
        )
        val restored = shift.toEntity().toDomain()
        assertEquals("t1", restored.taskId)
        assertEquals("p1", restored.projectId)
        // The task rate is untouched by the project link.
        assertEquals(120.0, restored.taskHourlyRateSnapshot)
    }
}
