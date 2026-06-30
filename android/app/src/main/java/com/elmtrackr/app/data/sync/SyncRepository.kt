package com.elmtrackr.app.data.sync

import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    suspend fun syncAll(userId: String): SyncResult
    fun observePendingCount(userId: String): Flow<Int>
    fun observeLastSyncStatus(): Flow<String?>
}
