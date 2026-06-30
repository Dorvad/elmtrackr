package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.RegionCode
import java.util.UUID

fun UserSettingsEntity.toRemoteUpsert(
    defaultCompensationProfileRemoteId: String? = defaultCompensationProfileId,
): RemoteUserSettingsUpsert = RemoteUserSettingsUpsert(
    userId = userId,
    timezone = timezone,
    dailyOvertimeThresholdMinutes = dailyOvertimeThresholdMinutes,
    weeklyOvertimeThresholdMinutes = weeklyOvertimeThresholdMinutes,
    weekendDays = parseWeekendDays(weekendDays),
    hourlyRate = hourlyRate,
    currency = currency,
    regionCode = regionCode?.let(::regionToWire),
    currencyCode = currencyCode,
    defaultCompensationProfileId = defaultCompensationProfileRemoteId,
    onboardingCompleted = onboardingCompleted,
    onboardingCompletedAt = onboardingCompletedAt?.let(::epochToIso),
    featuresTravelRefunds = featuresTravelRefunds,
    featuresPaidProjects = featuresPaidProjects,
    featuresInsights = featuresInsights,
    featuresClockStyles = featuresClockStyles,
    featuresOvertimeReminders = featuresOvertimeReminders,
    clockStyle = clockStyleToWire(clockStyle),
)

fun UserSettingsEntity.toRemoteUpdate(
    defaultCompensationProfileRemoteId: String? = defaultCompensationProfileId,
): RemoteUserSettingsUpdate = RemoteUserSettingsUpdate(
    timezone = timezone,
    dailyOvertimeThresholdMinutes = dailyOvertimeThresholdMinutes,
    weeklyOvertimeThresholdMinutes = weeklyOvertimeThresholdMinutes,
    weekendDays = parseWeekendDays(weekendDays),
    hourlyRate = hourlyRate,
    currency = currency,
    regionCode = regionCode?.let(::regionToWire),
    currencyCode = currencyCode,
    defaultCompensationProfileId = defaultCompensationProfileRemoteId,
    onboardingCompleted = onboardingCompleted,
    onboardingCompletedAt = onboardingCompletedAt?.let(::epochToIso),
    featuresTravelRefunds = featuresTravelRefunds,
    featuresPaidProjects = featuresPaidProjects,
    featuresInsights = featuresInsights,
    featuresClockStyles = featuresClockStyles,
    featuresOvertimeReminders = featuresOvertimeReminders,
    clockStyle = clockStyleToWire(clockStyle),
)

fun RemoteUserSettingsRow.toLocalEntity(
    existingLocalId: String? = null,
    defaultCompensationProfileLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): UserSettingsEntity {
    val created = isoToEpoch(createdAt)
    val updated = isoToEpoch(updatedAt)
    return UserSettingsEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        timezone = timezone,
        dailyOvertimeThresholdMinutes = dailyOvertimeThresholdMinutes,
        weeklyOvertimeThresholdMinutes = weeklyOvertimeThresholdMinutes,
        weekendDays = weekendDays.joinToString(","),
        hourlyRate = hourlyRate,
        currency = resolvedCurrency(),
        regionCode = regionCode?.let { RegionCode.fromPersisted(it).name },
        currencyCode = resolvedCurrencyCode(),
        defaultCompensationProfileId = defaultCompensationProfileLocalId,
        onboardingCompleted = onboardingCompleted,
        onboardingCompletedAt = onboardingCompletedAt?.let(::isoToEpoch),
        featuresTravelRefunds = featuresTravelRefunds,
        featuresPaidProjects = featuresPaidProjects,
        featuresInsights = featuresInsights,
        featuresClockStyles = featuresClockStyles,
        featuresOvertimeReminders = featuresOvertimeReminders,
        clockStyle = ClockStyle.fromPersisted(clockStyle).name,
        createdAt = created,
        updatedAt = updated,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

private fun RemoteUserSettingsRow.resolvedCurrency(): String =
    currency?.trim()?.takeIf { it.isNotEmpty() }
        ?: currencyCode?.trim()?.takeIf { it.isNotEmpty() }
        ?: "ILS"

private fun RemoteUserSettingsRow.resolvedCurrencyCode(): String =
    currencyCode?.trim()?.takeIf { it.isNotEmpty() } ?: resolvedCurrency()

private fun parseWeekendDays(raw: String): List<Int> =
    if (raw.isBlank()) emptyList() else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
