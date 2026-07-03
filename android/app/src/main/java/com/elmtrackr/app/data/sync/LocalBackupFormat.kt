package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import kotlinx.serialization.Serializable

/**
 * Versioned full-fidelity local backup format. Every entity column is included
 * so a backup can be re-imported without data loss. Bump [BACKUP_FORMAT_VERSION]
 * whenever a field is added; import tolerates unknown keys, so older app
 * versions' backups only fail when [LocalBackupDocument.formatVersion] predates
 * full-fidelity rows.
 */
const val BACKUP_FORMAT_VERSION = 2

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
    val refundAction: String? = null,
    val compensationProfileId: String? = null,
    val compensationSnapshotJson: String? = null,
    val taskId: String? = null,
    val taskNameSnapshot: String? = null,
    val taskIconSnapshot: String? = null,
    val taskHourlyRateSnapshot: Double? = null,
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

internal fun syncStatusFromBackup(value: String): SyncStatus =
    runCatching { SyncStatus.valueOf(value) }.getOrDefault(SyncStatus.PENDING_UPDATE)

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
    refundAction = refundAction, compensationProfileId = compensationProfileId,
    compensationSnapshotJson = compensationSnapshotJson, taskId = taskId,
    taskNameSnapshot = taskNameSnapshot, taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot, createdAt = createdAt,
    updatedAt = updatedAt, deletedAt = deletedAt, syncStatus = syncStatus.name,
    lastSyncedAt = lastSyncedAt,
)

internal fun ShiftBackupRow.toEntity(userId: String) = ShiftEntity(
    localId = localId, remoteId = remoteId, userId = userId, startTime = startTime,
    endTime = endTime, breakMinutes = breakMinutes, notes = notes, isSpecialDay = isSpecialDay,
    refundAction = refundAction, compensationProfileId = compensationProfileId,
    compensationSnapshotJson = compensationSnapshotJson, taskId = taskId,
    taskNameSnapshot = taskNameSnapshot, taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot, createdAt = createdAt,
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
    featuresOvertimeReminders = featuresOvertimeReminders, clockStyle = clockStyle,
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
    featuresOvertimeReminders = featuresOvertimeReminders, clockStyle = clockStyle,
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
