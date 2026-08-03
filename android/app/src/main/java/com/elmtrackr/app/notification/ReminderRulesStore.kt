package com.elmtrackr.app.notification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderRulesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminder_rules",
    // Same reasoning as appPreferencesDataStore: none of this is user
    // data, so a corrupt file should reset rather than crash the app.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Device-local persistence for the configurable reminder schedule. Reminder
 * rules deliberately do not sync: notifications are a per-device concern
 * (like the notification permission itself), and keeping them out of
 * user_settings avoids a Supabase contract change. The synced
 * featuresOvertimeReminders flag remains the master on/off switch.
 */
object ReminderRulesStore {

    private val rulesKey = stringPreferencesKey("rules_json_v1")

    fun rulesFlow(context: Context): Flow<List<ReminderRule>> =
        context.reminderRulesDataStore.data.map { prefs ->
            ReminderRulesCodec.decode(prefs[rulesKey]) ?: ReminderRulesCodec.DEFAULT_RULES
        }

    suspend fun current(context: Context): List<ReminderRule> = rulesFlow(context).first()

    suspend fun save(context: Context, rules: List<ReminderRule>) {
        context.reminderRulesDataStore.edit { prefs ->
            prefs[rulesKey] = ReminderRulesCodec.encode(rules.take(ReminderRulesCodec.MAX_RULES))
        }
    }
}
