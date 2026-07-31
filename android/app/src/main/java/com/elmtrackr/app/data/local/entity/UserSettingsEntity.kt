package com.elmtrackr.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_settings",
    indices = [Index(value = ["userId"], unique = true)],
)
data class UserSettingsEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val timezone: String,
    val dailyOvertimeThresholdMinutes: Int,
    val weeklyOvertimeThresholdMinutes: Int,
    /** Comma-separated JS-day integers, e.g. "5,6" = Fri + Sat */
    val weekendDays: String,
    val hourlyRate: Double?,
    val currency: String = "ILS",
    val regionCode: String? = null,
    val currencyCode: String? = null,
    val defaultCompensationProfileId: String? = null,
    val onboardingCompleted: Boolean,
    val onboardingCompletedAt: Long?,
    val featuresTravelRefunds: Boolean,
    val featuresPaidProjects: Boolean,
    val featuresInsights: Boolean,
    val featuresClockStyles: Boolean,
    val featuresOvertimeReminders: Boolean,
    val projectsDefaultRegionCode: String? = null,
    val projectsDefaultCurrencyCode: String? = null,
    val projectsTaxLabel: String? = null,
    @ColumnInfo(defaultValue = "0")
    val projectsTaxRateBasisPoints: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val projectsTaxInclusive: Boolean = false,
    val clockStyle: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
