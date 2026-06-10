package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun observeSettings(userId: String): Flow<UserSettings?>

    suspend fun getSettings(userId: String): UserSettings?

    suspend fun saveSettings(settings: UserSettings)

    /** Creates default settings for a new user. */
    suspend fun createDefaultSettings(userId: String): UserSettings
}
