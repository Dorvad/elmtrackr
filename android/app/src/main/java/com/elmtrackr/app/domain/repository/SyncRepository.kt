package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.SyncResult
import kotlinx.coroutines.flow.Flow

interface SyncRepository {

    fun observePendingCount(userId: String): Flow<Int>

    fun observeLastSyncStatus(): Flow<String?>

    suspend fun syncAll(userId: String): SyncResult
}
