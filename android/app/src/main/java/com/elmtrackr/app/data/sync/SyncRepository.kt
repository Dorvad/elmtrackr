package com.elmtrackr.app.data.sync

import kotlinx.coroutines.flow.Flow

data class SyncHealth(
    val pendingCount: Int,
    val failedCount: Int,
)

interface SyncRepository {
    suspend fun syncAll(userId: String): SyncResult

    /** Anything unsynced at all, including rows that already failed. Drives the badge. */
    suspend fun hasPendingWork(userId: String): Boolean

    /**
     * Unsynced work that has not already been tried and recorded as failed.
     *
     * The distinction matters only to the scheduler: [hasPendingWork] is the
     * honest answer to "do I have local changes", and using it to decide whether
     * to sync again turns a permanently unsyncable row into an endless chain of
     * follow-up workers.
     */
    suspend fun hasRetryablePendingWork(userId: String): Boolean

    suspend fun exportLocalBackup(userId: String): String
    suspend fun importLocalBackup(userId: String, json: String): BackupImportSummary
    fun observePendingCount(userId: String): Flow<Int>
    fun observeSyncHealth(userId: String): Flow<SyncHealth>
    fun observeLastSyncStatus(): Flow<String?>
    fun observeSyncDetails(userId: String): Flow<SyncDetails>
}
