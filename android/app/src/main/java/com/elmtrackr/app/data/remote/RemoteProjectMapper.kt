package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import java.math.BigDecimal
import java.util.UUID

/**
 * Paid Projects, local ↔ wire.
 *
 * Money crosses as `BigDecimal.toPlainString()` and comes back through
 * `BigDecimal(String)`. Both directions are exact and neither goes near a
 * `Double`: these are the amounts that say what a client owes, and a fee that
 * does not round-trip byte for byte is a bug the user finds in their invoice.
 *
 * `toPlainString` rather than `toString` because the latter can produce
 * scientific notation for small or large scales, which would fail the numeric
 * check constraint on the server and read back as a different string.
 */
fun ProjectEntity.toRemoteInsert(): RemoteProjectInsert = RemoteProjectInsert(
    id = localId,
    userId = userId,
    name = name,
    clientName = clientName,
    clientId = clientId,
    description = description,
    workStatus = workStatus,
    currencyCode = currencyCode,
    baseFee = baseFee.toPlainString(),
    taxLabel = taxLabel,
    taxRatePercent = taxRatePercent.toPlainString(),
    taxMode = taxMode,
    taxAmount = taxAmount.toPlainString(),
    clientTotal = clientTotal.toPlainString(),
    hourBudgetMinutes = hourBudgetMinutes,
    targetHourlyRate = targetHourlyRate?.toPlainString(),
    startDate = startDate,
    deadline = deadline,
    completionDate = completionDate,
    notes = notes,
    archivedAt = archivedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun ProjectEntity.toRemoteUpdate(): RemoteProjectUpdate = RemoteProjectUpdate(
    name = name,
    clientName = clientName,
    clientId = clientId,
    description = description,
    workStatus = workStatus,
    currencyCode = currencyCode,
    baseFee = baseFee.toPlainString(),
    taxLabel = taxLabel,
    taxRatePercent = taxRatePercent.toPlainString(),
    taxMode = taxMode,
    taxAmount = taxAmount.toPlainString(),
    clientTotal = clientTotal.toPlainString(),
    hourBudgetMinutes = hourBudgetMinutes,
    targetHourlyRate = targetHourlyRate?.toPlainString(),
    startDate = startDate,
    deadline = deadline,
    completionDate = completionDate,
    notes = notes,
    archivedAt = archivedAt?.let(::epochToIso),
    deletedAt = deletedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun RemoteProjectRow.toLocalEntity(
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): ProjectEntity {
    val updated = isoToEpoch(updatedAt)
    return ProjectEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        name = name,
        clientName = clientName,
        clientId = clientId,
        description = description,
        workStatus = workStatus,
        currencyCode = currencyCode,
        baseFee = BigDecimal(baseFee),
        taxLabel = taxLabel,
        taxRatePercent = BigDecimal(taxRatePercent),
        taxMode = taxMode,
        taxAmount = BigDecimal(taxAmount),
        clientTotal = BigDecimal(clientTotal),
        hourBudgetMinutes = hourBudgetMinutes,
        targetHourlyRate = targetHourlyRate?.let(::BigDecimal),
        startDate = startDate,
        deadline = deadline,
        completionDate = completionDate,
        notes = notes,
        createdAt = isoToEpoch(createdAt),
        updatedAt = updated,
        archivedAt = archivedAt?.let(::isoToEpoch),
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

fun ProjectBillingRecordEntity.toRemoteInsert(
    projectRemoteId: String,
): RemoteProjectBillingRecordInsert = RemoteProjectBillingRecordInsert(
    id = localId,
    userId = userId,
    projectId = projectRemoteId,
    baseAmount = baseAmount.toPlainString(),
    taxLabel = taxLabel,
    taxRatePercent = taxRatePercent.toPlainString(),
    taxMode = taxMode,
    taxAmount = taxAmount.toPlainString(),
    totalAmount = totalAmount.toPlainString(),
    currencyCode = currencyCode,
    externalReference = externalReference,
    notes = notes,
    billedOn = billedOn,
    dueOn = dueOn,
    cancelledAt = cancelledAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun ProjectBillingRecordEntity.toRemoteUpdate(): RemoteProjectBillingRecordUpdate =
    RemoteProjectBillingRecordUpdate(
        baseAmount = baseAmount.toPlainString(),
        taxLabel = taxLabel,
        taxRatePercent = taxRatePercent.toPlainString(),
        taxMode = taxMode,
        taxAmount = taxAmount.toPlainString(),
        totalAmount = totalAmount.toPlainString(),
        currencyCode = currencyCode,
        externalReference = externalReference,
        notes = notes,
        billedOn = billedOn,
        dueOn = dueOn,
        cancelledAt = cancelledAt?.let(::epochToIso),
        deletedAt = deletedAt?.let(::epochToIso),
        clientUpdatedAt = epochToIso(updatedAt),
    )

fun RemoteProjectBillingRecordRow.toLocalEntity(
    projectLocalId: String,
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): ProjectBillingRecordEntity {
    val updated = isoToEpoch(updatedAt)
    return ProjectBillingRecordEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        projectLocalId = projectLocalId,
        baseAmount = BigDecimal(baseAmount),
        taxLabel = taxLabel,
        taxRatePercent = BigDecimal(taxRatePercent),
        taxMode = taxMode,
        taxAmount = BigDecimal(taxAmount),
        totalAmount = BigDecimal(totalAmount),
        currencyCode = currencyCode,
        externalReference = externalReference,
        notes = notes,
        billedOn = billedOn,
        dueOn = dueOn,
        cancelledAt = cancelledAt?.let(::isoToEpoch),
        createdAt = isoToEpoch(createdAt),
        updatedAt = updated,
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

fun ProjectPaymentEntity.toRemoteInsert(
    projectRemoteId: String,
    billingRecordRemoteId: String,
): RemoteProjectPaymentInsert = RemoteProjectPaymentInsert(
    id = localId,
    userId = userId,
    projectId = projectRemoteId,
    billingRecordId = billingRecordRemoteId,
    paidOn = paidOn,
    amount = amount.toPlainString(),
    currencyCode = currencyCode,
    method = method,
    externalReference = externalReference,
    notes = notes,
    clientUpdatedAt = epochToIso(updatedAt),
)

fun ProjectPaymentEntity.toRemoteUpdate(): RemoteProjectPaymentUpdate = RemoteProjectPaymentUpdate(
    paidOn = paidOn,
    amount = amount.toPlainString(),
    currencyCode = currencyCode,
    method = method,
    externalReference = externalReference,
    notes = notes,
    deletedAt = deletedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun RemoteProjectPaymentRow.toLocalEntity(
    projectLocalId: String,
    billingRecordLocalId: String,
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): ProjectPaymentEntity {
    val updated = isoToEpoch(updatedAt)
    return ProjectPaymentEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        projectLocalId = projectLocalId,
        billingRecordLocalId = billingRecordLocalId,
        paidOn = paidOn,
        amount = BigDecimal(amount),
        currencyCode = currencyCode,
        method = method,
        externalReference = externalReference,
        notes = notes,
        createdAt = isoToEpoch(createdAt),
        updatedAt = updated,
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}
