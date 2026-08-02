package com.elmtrackr.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * These preferences are read during startup, so a corrupt file used to be an
 * unrecoverable crash loop: the read threw, the throw reached the application
 * scope's root `launch`, and the process died again on every relaunch. The
 * corruption handler trades the stored values — theme, onboarding flag,
 * dismissal state, all of them re-derivable or re-settable — for the app being
 * able to start. None of this is user data; the shifts live in Room.
 */
val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
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
    val PAID_PROJECTS_DISCOVERY_DISMISSED =
        booleanPreferencesKey("paid_projects_discovery_dismissed")

    /**
     * The month whose refund reminder the user dismissed, as `YYYY-MM`.
     *
     * A month rather than a boolean because the reminder is recurring: unresolved
     * refund claims are settled per month, so a flag would silence the nudge for
     * every future month as well. Storing which month was dismissed lets the
     * reminder re-arm on its own when the next one comes round.
     *
     * A string rather than an int pair because that is what [java.time.YearMonth]
     * parses and prints, so no format lives in two places.
     */
    val REFUND_REMINDER_DISMISSED_MONTH =
        stringPreferencesKey("refund_reminder_dismissed_month")

    /**
     * Newest-first clock face names, newline-separated.
     *
     * A string rather than a string set because the order *is* the data — a set
     * would return the four faces in arbitrary order and the row would reshuffle
     * itself on every read. Newline as the separator because enum names cannot
     * contain one, so the parse needs no escaping.
     */
    val RECENT_CLOCK_FACES = stringPreferencesKey("recent_clock_faces")

    /**
     * Names of the face packs the user has added. A set, not an ordered string:
     * unlike the history, nothing about presence depends on order.
     *
     * Absent is not the same as empty and both are fine — the bundled pack and the
     * pack holding the selected face are added at read time, so a missing or lost
     * value degrades to "just the defaults, plus whatever you have selected".
     */
    val INSTALLED_CLOCK_FACE_PACKS = stringSetPreferencesKey("installed_clock_face_packs")
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
    val paidProjectsDiscoveryDismissed: Boolean = false,
    /** Raw `YYYY-MM`, or null if the reminder was never dismissed. Parsed by the domain layer. */
    val refundReminderDismissedMonth: String? = null,
    /** Raw face names, newest first. Resolved to the enum by the UI layer. */
    val recentClockFaces: List<String> = emptyList(),
    /** Raw pack names. Resolved, and widened to include the defaults, by the UI layer. */
    val installedClockFacePacks: Set<String> = emptySet(),
)

class AppPreferencesRepository(private val context: Context) :
    AppPreferencesStore,
    AppLockPreferencesStore,
    OnboardingPreferences,
    SetupChecklistPreferences,
    FeatureDiscoveryPreferences,
    ClockFacePreferences {

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
                paidProjectsDiscoveryDismissed =
                    prefs[AppPreferenceKeys.PAID_PROJECTS_DISCOVERY_DISMISSED] ?: false,
                refundReminderDismissedMonth =
                    prefs[AppPreferenceKeys.REFUND_REMINDER_DISMISSED_MONTH],
                recentClockFaces =
                    prefs[AppPreferenceKeys.RECENT_CLOCK_FACES].orEmpty()
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList(),
                installedClockFacePacks =
                    prefs[AppPreferenceKeys.INSTALLED_CLOCK_FACE_PACKS] ?: emptySet(),
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

    override suspend fun setPaidProjectsDiscoveryDismissed(dismissed: Boolean) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.PAID_PROJECTS_DISCOVERY_DISMISSED] = dismissed
        }
    }

    override suspend fun setRefundReminderDismissedMonth(month: String) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.REFUND_REMINDER_DISMISSED_MONTH] = month
        }
    }

    override suspend fun setRecentClockFaces(styleNames: List<String>) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.RECENT_CLOCK_FACES] = styleNames.joinToString("\n")
        }
    }

    override suspend fun setInstalledClockFacePacks(packNames: Set<String>) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.INSTALLED_CLOCK_FACE_PACKS] = packNames
        }
    }

    /**
     * Clears the first-run nudge bookkeeping on sign-out so the next account on
     * this device gets its own onboarding, checklist, celebrations and feature
     * discovery instead of inheriting the previous user's. Deliberately keeps
     * genuinely device-scoped state: theme, reduce motion, app lock, device id,
     * legacy-adoption marker, and the once-per-install notification education.
     */
    suspend fun resetFirstRunNudges() {
        context.appPreferencesDataStore.edit {
            it.remove(AppPreferenceKeys.ONBOARDING_COMPLETED)
            it.remove(AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATED)
            it.remove(AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATION_PENDING)
            it.remove(AppPreferenceKeys.SETUP_CHECKLIST_DISMISSED)
            it.remove(AppPreferenceKeys.SETUP_CHECKLIST_VISITED_STEPS)
            it.remove(AppPreferenceKeys.SETUP_CHECKLIST_CELEBRATED)
            it.remove(AppPreferenceKeys.PAID_PROJECTS_DISCOVERY_DISMISSED)
            // Added when the two changes met: the refund reminder is a per-user
            // nudge dismissal like the others above, so leaving it would silence a
            // new account's reminder for whatever month the previous user dismissed.
            it.remove(AppPreferenceKeys.REFUND_REMINDER_DISMISSED_MONTH)
        }
    }
}
