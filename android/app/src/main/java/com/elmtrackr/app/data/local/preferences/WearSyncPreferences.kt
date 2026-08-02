package com.elmtrackr.app.data.local.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Whether shift state is mirrored to a paired Wear OS watch.
 *
 * Device-local on purpose: which watch is paired with *this phone* is not user
 * data, and a second device should make its own choice.
 */
interface WearSyncPreferences {
    val preferences: Flow<AppPreferenceValues>
    suspend fun setWearSyncEnabled(enabled: Boolean)
}
