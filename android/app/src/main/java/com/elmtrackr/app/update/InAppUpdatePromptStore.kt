package com.elmtrackr.app.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.inAppUpdatePromptDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "in_app_update_prompt",
)

/**
 * Persists when the user last dismissed a flexible update or saw the Play Store
 * fallback, so we can re-prompt with a cooldown instead of nagging every resume
 * or staying silent for an entire session.
 */
class InAppUpdatePromptStore(private val context: Context) {

    suspend fun lastFlexibleDismissedAt(): Long? =
        context.inAppUpdatePromptDataStore.data
            .map { it[KEY_FLEXIBLE_DISMISSED_AT] }
            .first()

    suspend fun recordFlexibleDismissed(atMs: Long = System.currentTimeMillis()) {
        context.inAppUpdatePromptDataStore.edit { it[KEY_FLEXIBLE_DISMISSED_AT] = atMs }
    }

    suspend fun lastPlayStoreFallbackShownAt(): Long? =
        context.inAppUpdatePromptDataStore.data
            .map { it[KEY_PLAY_STORE_FALLBACK_AT] }
            .first()

    suspend fun recordPlayStoreFallbackShown(atMs: Long = System.currentTimeMillis()) {
        context.inAppUpdatePromptDataStore.edit { it[KEY_PLAY_STORE_FALLBACK_AT] = atMs }
    }

    private companion object {
        val KEY_FLEXIBLE_DISMISSED_AT = longPreferencesKey("flexible_dismissed_at")
        val KEY_PLAY_STORE_FALLBACK_AT = longPreferencesKey("play_store_fallback_at")
    }
}
