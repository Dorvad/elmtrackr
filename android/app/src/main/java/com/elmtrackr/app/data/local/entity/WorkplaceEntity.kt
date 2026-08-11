package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A job, as distinct from the pay profile that happens to be effective for it.
 *
 * Leave balances and entitlement follow the employer through wage changes and
 * new compensation profiles, so they hang off this row rather than off a
 * profile.
 */
@Entity(
    tableName = "workplaces",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class WorkplaceEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val name: String,
    val regionCode: String,
    val currencyCode: String,
    val timezone: String,
    /** Epoch day, not millis: an employment start date is a date. */
    val employmentStartDate: Long?,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
