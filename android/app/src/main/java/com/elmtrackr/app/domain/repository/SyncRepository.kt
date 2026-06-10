package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

/** Placeholder — populated when Supabase networking is added. */
interface SyncRepository {

    /** Emits the count of records that need to be pushed to the server. */
    fun observePendingCount(): Flow<Int>

    /** Emits the last sync result message, null if never synced. */
    fun observeLastSyncStatus(): Flow<String?>

    /** No-op until networking layer is added. */
    suspend fun syncAll(userId: String)
}
