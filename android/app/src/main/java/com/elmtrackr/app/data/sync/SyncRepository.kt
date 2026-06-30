package com.elmtrackr.app.data.sync

import kotlinx.coroutines.flow.Flow

data class SyncHealth(
    val pendingCount: Int,
    val failedCount: Int,
)

interface SyncRepository {
    suspend fun syncAll(userId: String): SyncResult
    suspend fun hasPendingWork(userId: String): Boolean
    suspend fun exportLocalBackup(userId: String): String
    fun observePendingCount(userId: String): Flow<Int>
    fun observeSyncHealth(userId: String): Flow<SyncHealth>
    fun observeLastSyncStatus(): Flow<String?>
    fun observeSyncDetails(userId: String): Flow<SyncDetails>
}
