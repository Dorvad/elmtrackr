package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "compensation_profiles",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class CompensationProfileEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val name: String,
    val regionCode: String,
    val currencyCode: String,
    val timezone: String,
    val baseHourlyRate: Double?,
    val rulesJson: String,
    val stackingPolicy: String,
    val effectiveFrom: Long,
    val effectiveUntil: Long?,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
