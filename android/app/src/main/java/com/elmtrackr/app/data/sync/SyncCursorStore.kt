package com.elmtrackr.app.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.elmtrackr.app.data.local.preferences.appPreferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Per-user, per-entity incremental pull cursors (epoch millis). */
interface SyncCursorStore {
    suspend fun lastPulledAt(userId: String, entity: String): Long?
    suspend fun setLastPulledAt(userId: String, entity: String, epochMillis: Long)
    fun sinceIso(epochMillis: Long?): String?
}

class PreferenceSyncCursorStore(context: Context) : SyncCursorStore {
    private val dataStore = context.applicationContext.appPreferencesDataStore

    override suspend fun lastPulledAt(userId: String, entity: String): Long? {
        val key = longPreferencesKey(cursorKey(userId, entity))
        val prefs = dataStore.data.first()
        return prefs[key]
    }

    override suspend fun setLastPulledAt(userId: String, entity: String, epochMillis: Long) {
        val key = longPreferencesKey(cursorKey(userId, entity))
        dataStore.edit { it[key] = epochMillis }
    }

    override fun sinceIso(epochMillis: Long?): String? =
        epochMillis?.let { Instant.ofEpochMilli(it).toString() }

    private fun cursorKey(userId: String, entity: String): String =
        "sync_last_pull_${userId}_$entity"
}
