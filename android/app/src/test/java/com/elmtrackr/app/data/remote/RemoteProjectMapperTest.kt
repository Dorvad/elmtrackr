package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Project money surviving the trip to the cloud and back.
 *
 * These are the amounts that say what a client owes. A fee that comes back a
 * fraction different from what was stored is not a rounding curiosity — it is a
 * wrong invoice, found by the user rather than by us.
 */
class RemoteProjectMapperTest {

    @Test
    fun `a fee round-trips exactly, including its scale`() {
        val project = project(baseFee = BigDecimal("1234.56"))

        val restored = project.toRemoteInsert()
            .let { insert -> row(insert.baseFee, insert.taxAmount, insert.clientTotal) }
            .toLocalEntity(existingLocalId = project.localId)

        assertEquals(project.baseFee, restored.baseFee)
        // compareTo would pass on 1234.56 vs 1234.560; equals does not, and the
        // scale is part of what was stored.
        assertEquals(project.baseFee.scale(), restored.baseFee.scale())
    }

    /**
     * Amounts with more precision than a double can hold. These are the values
     * that would quietly change if the wire format were a JSON number.
     */
    @Test
    fun `high-precision amounts are not rounded on the wire`() {
        listOf("0.1", "12345678901234567890.12", "0.005", "999999999.999")
            .forEach { raw ->
                val project = project(baseFee = BigDecimal(raw))
                val onWire = project.toRemoteInsert().baseFee

                assertEquals(raw, onWire)
                assertEquals(
                    BigDecimal(raw),
                    row(onWire, "0", "0").toLocalEntity().baseFee,
                )
            }
    }

    /**
     * `toString` switches to scientific notation at some scales, which would
     * fail the numeric check constraint server-side and read back as a different
     * string. `toPlainString` never does.
     */
    @Test
    fun `small scales are written in plain notation, not scientific`() {
        val project = project(baseFee = BigDecimal("0.0000001"))

        assertEquals("0.0000001", project.toRemoteInsert().baseFee)
    }

    @Test
    fun `a deleted project pushes a tombstone`() {
        val update = project(baseFee = BigDecimal("10")).copy(deletedAt = 1_700_000_000_000L)
            .toRemoteUpdate()

        assertNotNull(update.deletedAt)
    }

    @Test
    fun `a billing record round-trips its amounts and its project link`() {
        val record = billingRecord()

        val restored = record.toRemoteInsert(projectRemoteId = "remote-project-1")
            .let {
                RemoteProjectBillingRecordRow(
                    id = it.id,
                    userId = it.userId,
                    projectId = it.projectId,
                    baseAmount = it.baseAmount,
                    taxLabel = it.taxLabel,
                    taxRatePercent = it.taxRatePercent,
                    taxMode = it.taxMode,
                    taxAmount = it.taxAmount,
                    totalAmount = it.totalAmount,
                    currencyCode = it.currencyCode,
                    externalReference = it.externalReference,
                    notes = it.notes,
                    billedOn = it.billedOn,
                    dueOn = it.dueOn,
                    cancelledAt = it.cancelledAt,
                    createdAt = "2026-01-01T00:00:00Z",
                    updatedAt = "2026-01-01T00:00:00Z",
                    clientUpdatedAt = it.clientUpdatedAt,
                )
            }
            .toLocalEntity(projectLocalId = record.projectLocalId, existingLocalId = record.localId)

        assertEquals(record.baseAmount, restored.baseAmount)
        assertEquals(record.totalAmount, restored.totalAmount)
        assertEquals(record.projectLocalId, restored.projectLocalId)
        // Epoch day, not a timestamp: a billing date must not shift by a day
        // through a timezone conversion.
        assertEquals(record.billedOn, restored.billedOn)
    }

    private fun project(baseFee: BigDecimal) = ProjectEntity(
        localId = "project-1",
        remoteId = null,
        userId = "user-1",
        name = "Rebrand",
        clientName = "Acme",
        clientId = null,
        description = null,
        workStatus = "ACTIVE",
        currencyCode = "ILS",
        baseFee = baseFee,
        taxLabel = null,
        taxRatePercent = BigDecimal("18"),
        taxMode = "EXCLUSIVE",
        taxAmount = BigDecimal("0"),
        clientTotal = BigDecimal("0"),
        hourBudgetMinutes = null,
        targetHourlyRate = null,
        startDate = null,
        deadline = null,
        completionDate = null,
        notes = null,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        archivedAt = null,
        deletedAt = null,
        syncStatus = SyncStatus.PENDING_CREATE,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    private fun billingRecord() = ProjectBillingRecordEntity(
        localId = "record-1",
        remoteId = null,
        userId = "user-1",
        projectLocalId = "project-1",
        baseAmount = BigDecimal("4200.50"),
        taxLabel = "VAT",
        taxRatePercent = BigDecimal("18"),
        taxMode = "EXCLUSIVE",
        taxAmount = BigDecimal("756.09"),
        totalAmount = BigDecimal("4956.59"),
        currencyCode = "ILS",
        externalReference = "INV-2026-004",
        notes = null,
        billedOn = 20_400L,
        dueOn = 20_430L,
        cancelledAt = null,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        deletedAt = null,
        syncStatus = SyncStatus.PENDING_CREATE,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    private fun row(baseFee: String, taxAmount: String, clientTotal: String) = RemoteProjectRow(
        id = "remote-project-1",
        userId = "user-1",
        name = "Rebrand",
        workStatus = "ACTIVE",
        currencyCode = "ILS",
        baseFee = baseFee,
        taxRatePercent = "18",
        taxMode = "EXCLUSIVE",
        taxAmount = taxAmount,
        clientTotal = clientTotal,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )
}
