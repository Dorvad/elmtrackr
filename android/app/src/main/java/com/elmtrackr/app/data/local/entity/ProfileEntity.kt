package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["userId"], unique = true)],
)
data class ProfileEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val email: String,
    val fullName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
