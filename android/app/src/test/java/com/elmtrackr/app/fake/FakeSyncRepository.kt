package com.elmtrackr.app.fake

import com.elmtrackr.app.data.sync.BackupImportSummary
import com.elmtrackr.app.data.sync.SyncDetails
import com.elmtrackr.app.data.sync.SyncDetailsBuilder
import com.elmtrackr.app.data.sync.SyncHealth
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeSyncRepository : SyncRepository {
    private val health = MutableStateFlow(SyncHealth(0, 0))
    private val lastStatus = MutableStateFlow<String?>(null)
    private val details = MutableStateFlow(
        SyncDetailsBuilder.build(
            tasks = emptyList(),
            shifts = emptyList(),
            claims = emptyList(),
            settings = null,
            profiles = emptyList(),
            lastSyncStatus = null,
        ),
    )

    fun setHealth(pending: Int, failed: Int) {
        health.value = SyncHealth(pending, failed)
    }

    override suspend fun syncAll(userId: String): SyncResult = SyncResult.Success

    override suspend fun hasPendingWork(userId: String): Boolean = health.value.pendingCount > 0

    override suspend fun exportLocalBackup(userId: String): String = """{"userId":"$userId"}"""

    override suspend fun importLocalBackup(userId: String, json: String): BackupImportSummary =
        BackupImportSummary()

    override fun observePendingCount(userId: String): Flow<Int> =
        flowOf(health.value.pendingCount)

    override fun observeSyncHealth(userId: String): Flow<SyncHealth> = health

    override fun observeLastSyncStatus(): Flow<String?> = lastStatus

    override fun observeSyncDetails(userId: String): Flow<SyncDetails> = details
}
