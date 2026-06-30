package com.elmtrackr.app.fake

import com.elmtrackr.app.data.sync.SyncHealth
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeSyncRepository : SyncRepository {
    private val health = MutableStateFlow(SyncHealth(0, 0))
    private val lastStatus = MutableStateFlow<String?>(null)

    override suspend fun syncAll(userId: String): SyncResult = SyncResult.Success

    override suspend fun hasPendingWork(userId: String): Boolean = health.value.pendingCount > 0

    override fun observePendingCount(userId: String): Flow<Int> =
        flowOf(health.value.pendingCount)

    override fun observeSyncHealth(userId: String): Flow<SyncHealth> = health

    override fun observeLastSyncStatus(): Flow<String?> = lastStatus
}
