package com.elmtrackr.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    val FIRST_CLOCK_IN_CELEBRATION_PENDING = booleanPreferencesKey("first_clock_in_celebration_pending")
    val LAST_ACTIVE_USER_ID = stringPreferencesKey("last_active_user_id")
    val DEVICE_ID = stringPreferencesKey("device_id")
    val LEGACY_DATA_ADOPTED = booleanPreferencesKey("legacy_data_adopted")
    val NOTIFICATION_PERMISSION_PROMPTED = booleanPreferencesKey("notification_permission_prompted")
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val SETUP_CHECKLIST_DISMISSED = booleanPreferencesKey("setup_checklist_dismissed")
    val SETUP_CHECKLIST_VISITED_STEPS = stringSetPreferencesKey("setup_checklist_visited_steps")
    val SETUP_CHECKLIST_CELEBRATED = booleanPreferencesKey("setup_checklist_celebrated")
}

data class AppPreferenceValues(
    val selectedTheme: String = "system",
    val onboardingCompleted: Boolean = false,
    val firstClockInCelebrated: Boolean = false,
    val firstClockInCelebrationPending: Boolean = false,
    val lastActiveUserId: String? = null,
    val deviceId: String? = null,
    val legacyDataAdopted: Boolean = false,
    val appLockEnabled: Boolean = false,
    val reduceMotionEnabled: Boolean = false,
    val setupChecklistDismissed: Boolean = false,
    val setupChecklistVisitedSteps: Set<String> = emptySet(),
    val setupChecklistCelebrated: Boolean = false,
)

class AppPreferencesRepository(private val context: Context) :
    AppPreferencesStore,
    AppLockPreferencesStore,
    OnboardingPreferences,
    SetupChecklistPreferences {

    override val preferences: Flow<AppPreferenceValues> =
        context.appPreferencesDataStore.data.map { prefs ->
            AppPreferenceValues(
                selectedTheme = prefs[AppPreferenceKeys.SELECTED_THEME] ?: "system",
                onboardingCompleted = prefs[AppPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
                firstClockInCelebrated = prefs[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATED] ?: false,
                firstClockInCelebrationPending = prefs[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATION_PENDING] ?: false,
                lastActiveUserId = prefs[AppPreferenceKeys.LAST_ACTIVE_USER_ID],
                deviceId = prefs[AppPreferenceKeys.DEVICE_ID],
                legacyDataAdopted = prefs[AppPreferenceKeys.LEGACY_DATA_ADOPTED] ?: false,
                appLockEnabled = prefs[AppPreferenceKeys.APP_LOCK_ENABLED] ?: false,
                reduceMotionEnabled = prefs[AppPreferenceKeys.REDUCE_MOTION] ?: false,
                setupChecklistDismissed = prefs[AppPreferenceKeys.SETUP_CHECKLIST_DISMISSED] ?: false,
                setupChecklistVisitedSteps = prefs[AppPreferenceKeys.SETUP_CHECKLIST_VISITED_STEPS] ?: emptySet(),
                setupChecklistCelebrated = prefs[AppPreferenceKeys.SETUP_CHECKLIST_CELEBRATED] ?: false,
            )
        }

    suspend fun setSelectedTheme(theme: String) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.SELECTED_THEME] = theme }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setFirstClockInCelebrated(celebrated: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATED] = celebrated }
    }

    override suspend fun setFirstClockInCelebrationPending(pending: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATION_PENDING] = pending }
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

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.APP_LOCK_ENABLED] = enabled }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.REDUCE_MOTION] = enabled }
    }

    override suspend fun setSetupChecklistDismissed(dismissed: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.SETUP_CHECKLIST_DISMISSED] = dismissed }
    }

    override suspend fun markSetupStepVisited(stepKey: String) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.SETUP_CHECKLIST_VISITED_STEPS] =
                (it[AppPreferenceKeys.SETUP_CHECKLIST_VISITED_STEPS] ?: emptySet()) + stepKey
        }
    }

    override suspend fun setSetupChecklistCelebrated(celebrated: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.SETUP_CHECKLIST_CELEBRATED] = celebrated }
    }
}
