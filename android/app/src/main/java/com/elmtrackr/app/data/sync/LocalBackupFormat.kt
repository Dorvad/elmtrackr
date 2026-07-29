package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import kotlinx.serialization.Serializable

/**
 * Versioned full-fidelity local backup format. Every entity column is included
 * so a backup can be re-imported without data loss. Bump [BACKUP_FORMAT_VERSION]
 * whenever a field is added; import tolerates unknown keys and accepts any
 * version back to [MIN_SUPPORTED_BACKUP_FORMAT_VERSION] (older documents simply
 * lack the newer optional fields), so bumping the version never strands a
 * user's existing backup files.
 */
const val BACKUP_FORMAT_VERSION = 5

/** Version 1 predates full-fidelity rows and cannot be restored safely. */
const val MIN_SUPPORTED_BACKUP_FORMAT_VERSION = 2

@Serializable
data class LocalBackupDocument(
    val formatVersion: Int = 1, // documents written before versioning parse as 1
    val exportedAt: String,
    val userId: String,
    val appVersion: String,
    val tasks: List<TaskBackupRow> = emptyList(),
    val shifts: List<ShiftBackupRow> = emptyList(),
    val refundClaims: List<RefundClaimBackupRow> = emptyList(),
    val userSettings: List<UserSettingsBackupRow> = emptyList(),
    val compensationProfiles: List<CompensationProfileBackupRow> = emptyList(),
    val premiumProfiles: List<PremiumProfileBackupRow> = emptyList(),
    val receipts: List<ReceiptBackupRow> = emptyList(),
    val projects: List<ProjectBackupRow> = emptyList(),
    val projectBillingRecords: List<ProjectBillingRecordBackupRow> = emptyList(),
    val projectPayments: List<ProjectPaymentBackupRow> = emptyList(),
)

@Serializable
data class TaskBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val name: String,
    val icon: String,
    val color: String? = null,
    val hourlyRate: Double,
    val isArchived: Boolean,
    val lastUsedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

@Serializable
data class ShiftBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val breakMinutes: Int,
    val notes: String? = null,
    val isSpecialDay: Boolean,
    val forceRegularRate: Boolean = false,
    val refundAction: String? = null,
    val compensationProfileId: String? = null,
    val premiumProfileId: String? = null,
    val compensationSnapshotJson: String? = null,
    val taskId: String? = null,
    val taskNameSnapshot: String? = null,
    val taskIconSnapshot: String? = null,
    val taskHourlyRateSnapshot: Double? = null,
    val projectId: String? = null,
    val projectNameSnapshot: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

@Serializable
data class RefundClaimBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val shiftLocalId: String,
    val direction: String,
    val provider: String,
    val amount: Double,
    val rideAt: Long,
    val notes: String? = null,
    val receiptPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

@Serializable
data class UserSettingsBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val timezone: String,
    val dailyOvertimeThresholdMinutes: Int,
    val weeklyOvertimeThresholdMinutes: Int,
    val weekendDays: String,
    val hourlyRate: Double? = null,
    val currency: String = "ILS",
    val regionCode: String? = null,
    val currencyCode: String? = null,
    val defaultCompensationProfileId: String? = null,
    val onboardingCompleted: Boolean,
    val onboardingCompletedAt: Long? = null,
    val featuresTravelRefunds: Boolean,
    val featuresPaidProjects: Boolean,
    val featuresInsights: Boolean,
    val featuresClockStyles: Boolean,
    val featuresOvertimeReminders: Boolean,
    val projectsDefaultRegionCode: String? = null,
    val projectsDefaultCurrencyCode: String? = null,
    val projectsTaxLabel: String? = null,
    val projectsTaxRateBasisPoints: Int = 0,
    val projectsTaxInclusive: Boolean = false,
    val clockStyle: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

@Serializable
data class CompensationProfileBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val name: String,
    val regionCode: String,
    val currencyCode: String,
    val timezone: String,
    val baseHourlyRate: Double? = null,
    val rulesJson: String,
    val stackingPolicy: String,
    val effectiveFrom: Long,
    val effectiveUntil: Long? = null,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

@Serializable
data class PremiumProfileBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val name: String,
    val multiplier: Double,
    val premiumType: String,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

/**
 * Receipts are local-only (never synced), so the backup row is their sole
 * recovery path. The image binary itself is not embedded — after a restore
 * the structured fields (amount, merchant, date, OCR text) survive even when
 * [localImageUri] no longer resolves to a file.
 */
@Serializable
data class ReceiptBackupRow(
    val id: String,
    val refundClaimId: String? = null,
    val localImageUri: String,
    val merchantName: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val receiptDate: Long? = null,
    val rawOcrText: String? = null,
    val parserVersion: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Paid Projects rows. Money is carried as a canonical decimal string, matching
 * how it is stored, so a backup round trip cannot introduce floating-point
 * drift into a fee or a payment.
 */
@Serializable
data class ProjectBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val name: String,
    val clientName: String? = null,
    val clientId: String? = null,
    val description: String? = null,
    val workStatus: String,
    val currencyCode: String,
    val baseFee: String,
    val taxLabel: String? = null,
    val taxRatePercent: String,
    val taxMode: String,
    val taxAmount: String,
    val clientTotal: String,
    val hourBudgetMinutes: Int? = null,
    val targetHourlyRate: String? = null,
    val startDate: Long? = null,
    val deadline: Long? = null,
    val completionDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

@Serializable
data class ProjectBillingRecordBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val projectLocalId: String,
    val baseAmount: String,
    val taxLabel: String? = null,
    val taxRatePercent: String,
    val taxMode: String,
    val taxAmount: String,
    val totalAmount: String,
    val currencyCode: String,
    val externalReference: String? = null,
    val billedOn: Long,
    val dueOn: Long? = null,
    val cancelledAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

@Serializable
data class ProjectPaymentBackupRow(
    val localId: String,
    val remoteId: String? = null,
    val projectLocalId: String,
    val billingRecordLocalId: String,
    val paidOn: Long,
    val amount: String,
    val currencyCode: String,
    val method: String? = null,
    val externalReference: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String,
    val lastSyncedAt: Long? = null,
)

internal fun syncStatusFromBackup(value: String): SyncStatus = SyncStatus.fromPersisted(value)

internal fun TaskEntity.toBackupRow() = TaskBackupRow(
    localId = localId, remoteId = remoteId, name = name, icon = icon, color = color,
    hourlyRate = hourlyRate, isArchived = isArchived, lastUsedAt = lastUsedAt,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = syncStatus.name, lastSyncedAt = lastSyncedAt,
)

internal fun TaskBackupRow.toEntity(userId: String) = TaskEntity(
    localId = localId, remoteId = remoteId, userId = userId, name = name, icon = icon,
    color = color, hourlyRate = hourlyRate, isArchived = isArchived, lastUsedAt = lastUsedAt,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = syncStatusFromBackup(syncStatus), lastSyncError = null, lastSyncedAt = lastSyncedAt,
)

internal fun ShiftEntity.toBackupRow() = ShiftBackupRow(
    localId = localId, remoteId = remoteId, startTime = startTime, endTime = endTime,
    breakMinutes = breakMinutes, notes = notes, isSpecialDay = isSpecialDay,
    forceRegularRate = forceRegularRate,
    refundAction = refundAction, compensationProfileId = compensationProfileId,
    premiumProfileId = premiumProfileId,
    compensationSnapshotJson = compensationSnapshotJson, taskId = taskId,
    taskNameSnapshot = taskNameSnapshot, taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot,
    projectId = projectId, projectNameSnapshot = projectNameSnapshot, createdAt = createdAt,
    updatedAt = updatedAt, deletedAt = deletedAt, syncStatus = syncStatus.name,
    lastSyncedAt = lastSyncedAt,
)

internal fun ShiftBackupRow.toEntity(userId: String) = ShiftEntity(
    localId = localId, remoteId = remoteId, userId = userId, startTime = startTime,
    endTime = endTime, breakMinutes = breakMinutes, notes = notes, isSpecialDay = isSpecialDay,
    forceRegularRate = forceRegularRate,
    refundAction = refundAction, compensationProfileId = compensationProfileId,
    premiumProfileId = premiumProfileId,
    compensationSnapshotJson = compensationSnapshotJson, taskId = taskId,
    taskNameSnapshot = taskNameSnapshot, taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot,
    projectId = projectId, projectNameSnapshot = projectNameSnapshot, createdAt = createdAt,
    updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = syncStatusFromBackup(syncStatus), lastSyncError = null, lastSyncedAt = lastSyncedAt,
)

internal fun RefundClaimEntity.toBackupRow() = RefundClaimBackupRow(
    localId = localId, remoteId = remoteId, shiftLocalId = shiftLocalId, direction = direction,
    provider = provider, amount = amount, rideAt = rideAt, notes = notes,
    receiptPath = receiptPath, createdAt = createdAt, updatedAt = updatedAt,
    deletedAt = deletedAt, syncStatus = syncStatus.name, lastSyncedAt = lastSyncedAt,
)

internal fun RefundClaimBackupRow.toEntity(userId: String) = RefundClaimEntity(
    localId = localId, remoteId = remoteId, shiftLocalId = shiftLocalId, userId = userId,
    direction = direction, provider = provider, amount = amount, rideAt = rideAt,
    notes = notes, receiptPath = receiptPath, createdAt = createdAt, updatedAt = updatedAt,
    deletedAt = deletedAt, syncStatus = syncStatusFromBackup(syncStatus),
    lastSyncError = null, lastSyncedAt = lastSyncedAt,
)

internal fun UserSettingsEntity.toBackupRow() = UserSettingsBackupRow(
    localId = localId, remoteId = remoteId, timezone = timezone,
    dailyOvertimeThresholdMinutes = dailyOvertimeThresholdMinutes,
    weeklyOvertimeThresholdMinutes = weeklyOvertimeThresholdMinutes,
    weekendDays = weekendDays, hourlyRate = hourlyRate, currency = currency,
    regionCode = regionCode, currencyCode = currencyCode,
    defaultCompensationProfileId = defaultCompensationProfileId,
    onboardingCompleted = onboardingCompleted, onboardingCompletedAt = onboardingCompletedAt,
    featuresTravelRefunds = featuresTravelRefunds, featuresPaidProjects = featuresPaidProjects,
    featuresInsights = featuresInsights, featuresClockStyles = featuresClockStyles,
    featuresOvertimeReminders = featuresOvertimeReminders,
    projectsDefaultRegionCode = projectsDefaultRegionCode,
    projectsDefaultCurrencyCode = projectsDefaultCurrencyCode,
    projectsTaxLabel = projectsTaxLabel,
    projectsTaxRateBasisPoints = projectsTaxRateBasisPoints,
    projectsTaxInclusive = projectsTaxInclusive,
    clockStyle = clockStyle,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = syncStatus.name, lastSyncedAt = lastSyncedAt,
)

internal fun UserSettingsBackupRow.toEntity(userId: String) = UserSettingsEntity(
    localId = localId, remoteId = remoteId, userId = userId, timezone = timezone,
    dailyOvertimeThresholdMinutes = dailyOvertimeThresholdMinutes,
    weeklyOvertimeThresholdMinutes = weeklyOvertimeThresholdMinutes,
    weekendDays = weekendDays, hourlyRate = hourlyRate, currency = currency,
    regionCode = regionCode, currencyCode = currencyCode,
    defaultCompensationProfileId = defaultCompensationProfileId,
    onboardingCompleted = onboardingCompleted, onboardingCompletedAt = onboardingCompletedAt,
    featuresTravelRefunds = featuresTravelRefunds, featuresPaidProjects = featuresPaidProjects,
    featuresInsights = featuresInsights, featuresClockStyles = featuresClockStyles,
    featuresOvertimeReminders = featuresOvertimeReminders,
    projectsDefaultRegionCode = projectsDefaultRegionCode,
    projectsDefaultCurrencyCode = projectsDefaultCurrencyCode,
    projectsTaxLabel = projectsTaxLabel,
    projectsTaxRateBasisPoints = projectsTaxRateBasisPoints,
    projectsTaxInclusive = projectsTaxInclusive,
    clockStyle = clockStyle,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = syncStatusFromBackup(syncStatus), lastSyncError = null, lastSyncedAt = lastSyncedAt,
)

internal fun CompensationProfileEntity.toBackupRow() = CompensationProfileBackupRow(
    localId = localId, remoteId = remoteId, name = name, regionCode = regionCode,
    currencyCode = currencyCode, timezone = timezone, baseHourlyRate = baseHourlyRate,
    rulesJson = rulesJson, stackingPolicy = stackingPolicy, effectiveFrom = effectiveFrom,
    effectiveUntil = effectiveUntil, isDefault = isDefault, isArchived = isArchived,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = syncStatus.name, lastSyncedAt = lastSyncedAt,
)

internal fun CompensationProfileBackupRow.toEntity(userId: String) = CompensationProfileEntity(
    localId = localId, remoteId = remoteId, userId = userId, name = name,
    regionCode = regionCode, currencyCode = currencyCode, timezone = timezone,
    baseHourlyRate = baseHourlyRate, rulesJson = rulesJson, stackingPolicy = stackingPolicy,
    effectiveFrom = effectiveFrom, effectiveUntil = effectiveUntil, isDefault = isDefault,
    isArchived = isArchived, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = syncStatusFromBackup(syncStatus), lastSyncError = null, lastSyncedAt = lastSyncedAt,
)

internal fun com.elmtrackr.app.data.local.entity.PremiumProfileEntity.toBackupRow() = PremiumProfileBackupRow(
    localId = localId, remoteId = remoteId, name = name, multiplier = multiplier,
    premiumType = premiumType, isDefault = isDefault, isArchived = isArchived,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = syncStatus.name, lastSyncedAt = lastSyncedAt,
)

internal fun PremiumProfileBackupRow.toEntity(userId: String) = com.elmtrackr.app.data.local.entity.PremiumProfileEntity(
    localId = localId, remoteId = remoteId, userId = userId, name = name,
    multiplier = multiplier, premiumType = premiumType, isDefault = isDefault,
    isArchived = isArchived, createdAt = createdAt, updatedAt = updatedAt,
    deletedAt = deletedAt, syncStatus = syncStatusFromBackup(syncStatus),
    lastSyncError = null, lastSyncedAt = lastSyncedAt,
)

internal fun com.elmtrackr.app.data.local.entity.ReceiptEntity.toBackupRow() = ReceiptBackupRow(
    id = id, refundClaimId = refundClaimId, localImageUri = localImageUri,
    merchantName = merchantName, amount = amount, currency = currency,
    receiptDate = receiptDate, rawOcrText = rawOcrText, parserVersion = parserVersion,
    createdAt = createdAt, updatedAt = updatedAt,
)

internal fun ReceiptBackupRow.toEntity(userId: String, refundClaimId: String?) = com.elmtrackr.app.data.local.entity.ReceiptEntity(
    id = id, userId = userId, refundClaimId = refundClaimId, localImageUri = localImageUri,
    merchantName = merchantName, amount = amount, currency = currency,
    receiptDate = receiptDate, rawOcrText = rawOcrText, parserVersion = parserVersion,
    createdAt = createdAt, updatedAt = updatedAt,
)

internal fun ProjectEntity.toBackupRow() = ProjectBackupRow(
    localId = localId, remoteId = remoteId, name = name, clientName = clientName,
    clientId = clientId, description = description, workStatus = workStatus,
    currencyCode = currencyCode, baseFee = baseFee.toPlainString(), taxLabel = taxLabel,
    taxRatePercent = taxRatePercent.toPlainString(), taxMode = taxMode,
    taxAmount = taxAmount.toPlainString(), clientTotal = clientTotal.toPlainString(),
    hourBudgetMinutes = hourBudgetMinutes, targetHourlyRate = targetHourlyRate?.toPlainString(),
    startDate = startDate, deadline = deadline, completionDate = completionDate, notes = notes,
    createdAt = createdAt, updatedAt = updatedAt, archivedAt = archivedAt, deletedAt = deletedAt,
    syncStatus = syncStatus.name, lastSyncedAt = lastSyncedAt,
)

internal fun ProjectBackupRow.toEntity(userId: String) = ProjectEntity(
    localId = localId, remoteId = remoteId, userId = userId, name = name,
    clientName = clientName, clientId = clientId, description = description,
    workStatus = workStatus, currencyCode = currencyCode, baseFee = decimalFromBackup(baseFee),
    taxLabel = taxLabel, taxRatePercent = decimalFromBackup(taxRatePercent), taxMode = taxMode,
    taxAmount = decimalFromBackup(taxAmount), clientTotal = decimalFromBackup(clientTotal),
    hourBudgetMinutes = hourBudgetMinutes,
    targetHourlyRate = targetHourlyRate?.let { decimalFromBackup(it) },
    startDate = startDate, deadline = deadline, completionDate = completionDate, notes = notes,
    createdAt = createdAt, updatedAt = updatedAt, archivedAt = archivedAt, deletedAt = deletedAt,
    syncStatus = syncStatusFromBackup(syncStatus), lastSyncError = null, lastSyncedAt = lastSyncedAt,
)

internal fun ProjectBillingRecordEntity.toBackupRow() = ProjectBillingRecordBackupRow(
    localId = localId, remoteId = remoteId, projectLocalId = projectLocalId,
    baseAmount = baseAmount.toPlainString(), taxLabel = taxLabel,
    taxRatePercent = taxRatePercent.toPlainString(), taxMode = taxMode,
    taxAmount = taxAmount.toPlainString(), totalAmount = totalAmount.toPlainString(),
    currencyCode = currencyCode, externalReference = externalReference, billedOn = billedOn,
    dueOn = dueOn, cancelledAt = cancelledAt, createdAt = createdAt, updatedAt = updatedAt,
    deletedAt = deletedAt, syncStatus = syncStatus.name, lastSyncedAt = lastSyncedAt,
)

internal fun ProjectBillingRecordBackupRow.toEntity(userId: String) = ProjectBillingRecordEntity(
    localId = localId, remoteId = remoteId, userId = userId, projectLocalId = projectLocalId,
    baseAmount = decimalFromBackup(baseAmount), taxLabel = taxLabel,
    taxRatePercent = decimalFromBackup(taxRatePercent), taxMode = taxMode,
    taxAmount = decimalFromBackup(taxAmount), totalAmount = decimalFromBackup(totalAmount),
    currencyCode = currencyCode, externalReference = externalReference, billedOn = billedOn,
    dueOn = dueOn, cancelledAt = cancelledAt, createdAt = createdAt, updatedAt = updatedAt,
    deletedAt = deletedAt, syncStatus = syncStatusFromBackup(syncStatus), lastSyncError = null,
    lastSyncedAt = lastSyncedAt,
)

internal fun ProjectPaymentEntity.toBackupRow() = ProjectPaymentBackupRow(
    localId = localId, remoteId = remoteId, projectLocalId = projectLocalId,
    billingRecordLocalId = billingRecordLocalId, paidOn = paidOn,
    amount = amount.toPlainString(), currencyCode = currencyCode, method = method,
    externalReference = externalReference, notes = notes, createdAt = createdAt,
    updatedAt = updatedAt, deletedAt = deletedAt, syncStatus = syncStatus.name,
    lastSyncedAt = lastSyncedAt,
)

internal fun ProjectPaymentBackupRow.toEntity(userId: String) = ProjectPaymentEntity(
    localId = localId, remoteId = remoteId, userId = userId, projectLocalId = projectLocalId,
    billingRecordLocalId = billingRecordLocalId, paidOn = paidOn,
    amount = decimalFromBackup(amount), currencyCode = currencyCode, method = method,
    externalReference = externalReference, notes = notes, createdAt = createdAt,
    updatedAt = updatedAt, deletedAt = deletedAt, syncStatus = syncStatusFromBackup(syncStatus),
    lastSyncError = null, lastSyncedAt = lastSyncedAt,
)

/** Zero rather than a throw, so one bad amount cannot fail a whole restore. */
private fun decimalFromBackup(value: String): java.math.BigDecimal =
    runCatching { java.math.BigDecimal(value.trim()) }.getOrDefault(java.math.BigDecimal.ZERO)
