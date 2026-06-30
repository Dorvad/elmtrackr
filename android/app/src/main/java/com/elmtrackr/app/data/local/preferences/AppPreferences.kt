package com.elmtrackr.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
)

object AppPreferenceKeys {
    val SELECTED_THEME = stringPreferencesKey("selected_theme")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val FIRST_CLOCK_IN_CELEBRATED = booleanPreferencesKey("first_clock_in_celebrated")
    val LAST_ACTIVE_USER_ID = stringPreferencesKey("last_active_user_id")
    val DEVICE_ID = stringPreferencesKey("device_id")
    val LEGACY_DATA_ADOPTED = booleanPreferencesKey("legacy_data_adopted")
    val NOTIFICATION_PERMISSION_PROMPTED = booleanPreferencesKey("notification_permission_prompted")
}

data class AppPreferenceValues(
    val selectedTheme: String = "system",
    val onboardingCompleted: Boolean = false,
    val firstClockInCelebrated: Boolean = false,
    val lastActiveUserId: String? = null,
    val deviceId: String? = null,
    val legacyDataAdopted: Boolean = false,
)

class AppPreferencesRepository(private val context: Context) : AppPreferencesStore {

    val preferences: Flow<AppPreferenceValues> =
        context.appPreferencesDataStore.data.map { prefs ->
            AppPreferenceValues(
                selectedTheme = prefs[AppPreferenceKeys.SELECTED_THEME] ?: "system",
                onboardingCompleted = prefs[AppPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
                firstClockInCelebrated = prefs[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATED] ?: false,
                lastActiveUserId = prefs[AppPreferenceKeys.LAST_ACTIVE_USER_ID],
                deviceId = prefs[AppPreferenceKeys.DEVICE_ID],
                legacyDataAdopted = prefs[AppPreferenceKeys.LEGACY_DATA_ADOPTED] ?: false,
            )
        }

    suspend fun setSelectedTheme(theme: String) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.SELECTED_THEME] = theme }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setFirstClockInCelebrated(celebrated: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATED] = celebrated }
    }

    suspend fun setLastActiveUserId(userId: String?) {
        context.appPreferencesDataStore.edit {
            if (userId != null) it[AppPreferenceKeys.LAST_ACTIVE_USER_ID] = userId
            else it.remove(AppPreferenceKeys.LAST_ACTIVE_USER_ID)
        }
    }

    suspend fun setDeviceId(deviceId: String) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.DEVICE_ID] = deviceId }
    }

    override suspend fun currentPreferences(): AppPreferenceValues = preferences.first()

    suspend fun setLegacyDataAdopted(adopted: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.LEGACY_DATA_ADOPTED] = adopted }
    }
}
