package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.SyncResult
import com.elmtrackr.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncRepository : SyncRepository {

    private val _pendingCount = MutableStateFlow(0)
    private val _lastSyncStatus = MutableStateFlow<String?>(null)

    var syncResult: SyncResult = SyncResult.NotConfigured
    var syncCallCount = 0

    fun setPendingCount(count: Int) { _pendingCount.value = count }

    override fun observePendingCount(): Flow<Int> = _pendingCount

    override fun observeLastSyncStatus(): Flow<String?> = _lastSyncStatus

    override suspend fun syncAll(userId: String): SyncResult {
        syncCallCount++
        return syncResult
    }
}
