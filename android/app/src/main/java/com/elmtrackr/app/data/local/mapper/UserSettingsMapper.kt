package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.UserSettings
import java.time.Instant

fun UserSettingsEntity.toDomain(): UserSettings = UserSettings(
    id = localId,
    userId = userId,
    timezone = timezone,
    dailyOvertimeThresholdMinutes = dailyOvertimeThresholdMinutes,
    weeklyOvertimeThresholdMinutes = weeklyOvertimeThresholdMinutes,
    weekendDays = if (weekendDays.isBlank()) emptyList()
                  else weekendDays.split(",").map { it.trim().toInt() },
    hourlyRate = hourlyRate,
    onboardingCompleted = onboardingCompleted,
    onboardingCompletedAt = onboardingCompletedAt?.let { Instant.ofEpochMilli(it) },
    featuresTravelRefunds = featuresTravelRefunds,
    featuresPaidProjects = featuresPaidProjects,
    featuresInsights = featuresInsights,
    featuresClockStyles = featuresClockStyles,
    clockStyle = ClockStyle.valueOf(clockStyle),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun UserSettings.toEntity(
    syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    remoteId: String? = null,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): UserSettingsEntity = UserSettingsEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    timezone = timezone,
    dailyOvertimeThresholdMinutes = dailyOvertimeThresholdMinutes,
    weeklyOvertimeThresholdMinutes = weeklyOvertimeThresholdMinutes,
    weekendDays = weekendDays.joinToString(","),
    hourlyRate = hourlyRate,
    onboardingCompleted = onboardingCompleted,
    onboardingCompletedAt = onboardingCompletedAt?.toEpochMilli(),
    featuresTravelRefunds = featuresTravelRefunds,
    featuresPaidProjects = featuresPaidProjects,
    featuresInsights = featuresInsights,
    featuresClockStyles = featuresClockStyles,
    clockStyle = clockStyle.name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)
