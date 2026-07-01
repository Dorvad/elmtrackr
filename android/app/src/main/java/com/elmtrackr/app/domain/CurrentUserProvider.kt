package com.elmtrackr.app.domain

import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface CurrentUserProvider {
    val userId: Flow<String?>
    suspend fun currentUserId(): String? = userId.first()
}

@Singleton
class PreferencesCurrentUserProvider @Inject constructor(
    appPreferences: AppPreferencesRepository,
) : CurrentUserProvider {
    override val userId: Flow<String?> =
        appPreferences.preferences.map { it.lastActiveUserId }
}
