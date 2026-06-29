package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.UserSettings
import java.time.Instant

fun UserSettingsEntity.toDomain(): UserSettings = UserSettings(
    id = localId,
    userId = userId,
    timezone = timezone,
    dailyOvertimeThresholdMinutes = dailyOvertimeThresholdMinutes,
    weeklyOvertimeThresholdMinutes = weeklyOvertimeThresholdMinutes,
    weekendDays = if (weekendDays.isBlank()) {
        emptyList()
    } else {
        weekendDays.split(",").mapNotNull { it.trim().toIntOrNull() }
    },
    hourlyRate = hourlyRate,
    currency = CurrencyCode.from(currency),
    regionCode = regionCode?.let { RegionCode.fromPersisted(it) },
    currencyCode = currencyCode,
    defaultCompensationProfileId = defaultCompensationProfileId,
    onboardingCompleted = onboardingCompleted,
    onboardingCompletedAt = onboardingCompletedAt?.let { Instant.ofEpochMilli(it) },
    featuresTravelRefunds = featuresTravelRefunds,
    featuresPaidProjects = featuresPaidProjects,
    featuresInsights = featuresInsights,
    featuresClockStyles = featuresClockStyles,
    featuresOvertimeReminders = featuresOvertimeReminders,
    clockStyle = ClockStyle.fromPersisted(clockStyle),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun UserSettings.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
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
    currency = currency.name,
    regionCode = regionCode?.name,
    currencyCode = currencyCode ?: currency.name,
    defaultCompensationProfileId = defaultCompensationProfileId,
    onboardingCompleted = onboardingCompleted,
    onboardingCompletedAt = onboardingCompletedAt?.toEpochMilli(),
    featuresTravelRefunds = featuresTravelRefunds,
    featuresPaidProjects = featuresPaidProjects,
    featuresInsights = featuresInsights,
    featuresClockStyles = featuresClockStyles,
    featuresOvertimeReminders = featuresOvertimeReminders,
    clockStyle = clockStyle.name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)
