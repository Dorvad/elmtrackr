package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import java.time.Instant
import java.time.LocalDate

// ── Projects ──────────────────────────────────────────────────────────────────

fun ProjectEntity.toDomain(): Project = Project(
    id = localId,
    userId = userId,
    name = name,
    clientName = clientName,
    clientId = clientId,
    description = description,
    workStatus = ProjectWorkStatus.fromPersisted(workStatus),
    fee = ProjectFee.fromPersisted(
        base = Money.of(baseFee, currencyCode),
        tax = Money.of(taxAmount, currencyCode),
        clientTotal = Money.of(clientTotal, currencyCode),
        taxMode = TaxMode.fromPersisted(taxMode),
        taxRatePercent = taxRatePercent,
        taxLabel = taxLabel,
    ),
    hourBudgetMinutes = hourBudgetMinutes,
    targetHourlyRate = targetHourlyRate?.let { Money.of(it, currencyCode) },
    startDate = startDate?.toLocalDateOrNull(),
    deadline = deadline?.toLocalDateOrNull(),
    completionDate = completionDate?.toLocalDateOrNull(),
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    archivedAt = archivedAt?.let { Instant.ofEpochMilli(it) },
    remoteId = remoteId,
)

/**
 * The only writer of `taxAmount` and `clientTotal`: both come straight off
 * [ProjectFee], so the stored triple can never drift from the calculation.
 */
fun Project.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): ProjectEntity = ProjectEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    clientName = clientName,
    clientId = clientId,
    description = description,
    workStatus = workStatus.name,
    currencyCode = fee.currencyCode,
    baseFee = fee.base.amount,
    taxLabel = fee.taxLabel,
    taxRatePercent = fee.taxRatePercent,
    taxMode = fee.taxMode.name,
    taxAmount = fee.tax.amount,
    clientTotal = fee.clientTotal.amount,
    hourBudgetMinutes = hourBudgetMinutes,
    targetHourlyRate = targetHourlyRate?.amount,
    startDate = startDate?.toEpochDay(),
    deadline = deadline?.toEpochDay(),
    completionDate = completionDate?.toEpochDay(),
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    archivedAt = archivedAt?.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)

// ── Billing records ───────────────────────────────────────────────────────────

fun ProjectBillingRecordEntity.toDomain(): ProjectBillingRecord = ProjectBillingRecord(
    id = localId,
    userId = userId,
    projectId = projectLocalId,
    fee = ProjectFee.fromPersisted(
        base = Money.of(baseAmount, currencyCode),
        tax = Money.of(taxAmount, currencyCode),
        clientTotal = Money.of(totalAmount, currencyCode),
        taxMode = TaxMode.fromPersisted(taxMode),
        taxRatePercent = taxRatePercent,
        taxLabel = taxLabel,
    ),
    externalReference = externalReference,
    billedOn = LocalDate.ofEpochDay(billedOn),
    dueOn = dueOn?.toLocalDateOrNull(),
    cancelledAt = cancelledAt?.let { Instant.ofEpochMilli(it) },
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun ProjectBillingRecord.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): ProjectBillingRecordEntity = ProjectBillingRecordEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    projectLocalId = projectId,
    baseAmount = fee.base.amount,
    taxLabel = fee.taxLabel,
    taxRatePercent = fee.taxRatePercent,
    taxMode = fee.taxMode.name,
    taxAmount = fee.tax.amount,
    totalAmount = fee.clientTotal.amount,
    currencyCode = fee.currencyCode,
    externalReference = externalReference,
    billedOn = billedOn.toEpochDay(),
    dueOn = dueOn?.toEpochDay(),
    cancelledAt = cancelledAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)

// ── Payments ──────────────────────────────────────────────────────────────────

fun ProjectPaymentEntity.toDomain(): ProjectPayment = ProjectPayment(
    id = localId,
    userId = userId,
    projectId = projectLocalId,
    billingRecordId = billingRecordLocalId,
    paidOn = LocalDate.ofEpochDay(paidOn),
    amount = Money.of(amount, currencyCode),
    method = method,
    externalReference = externalReference,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun ProjectPayment.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): ProjectPaymentEntity = ProjectPaymentEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    projectLocalId = projectId,
    billingRecordLocalId = billingRecordId,
    paidOn = paidOn.toEpochDay(),
    amount = amount.amount,
    currencyCode = amount.currencyCode,
    method = method,
    externalReference = externalReference,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)

private fun Long.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.ofEpochDay(this) }.getOrNull()
