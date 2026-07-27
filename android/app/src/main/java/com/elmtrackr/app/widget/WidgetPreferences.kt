package com.elmtrackr.app.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elmtrackr.app.domain.model.Shift
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

object WidgetPreferences {

    val KEY_IS_ACTIVE = booleanPreferencesKey("widget_is_active")
    val KEY_SHIFT_ID = stringPreferencesKey("widget_shift_id")
    val KEY_START_TIME_LABEL = stringPreferencesKey("widget_start_time_label")
    val KEY_DATE_LABEL = stringPreferencesKey("widget_date_label")
    val KEY_LAST_PUNCH_LABEL = stringPreferencesKey("widget_last_punch_label")
    val KEY_PENDING_COUNT = intPreferencesKey("widget_pending_count")
    val KEY_SHIFT_START_EPOCH = longPreferencesKey("widget_shift_start_epoch")
    val KEY_LAST_PUNCH_END_EPOCH = longPreferencesKey("widget_last_punch_end_epoch")
    val KEY_TODAY_MINUTES = intPreferencesKey("widget_today_minutes")
    val KEY_DAILY_GOAL_MINUTES = intPreferencesKey("widget_daily_goal_minutes")
    val KEY_ACTION_IN_FLIGHT = booleanPreferencesKey("widget_action_in_flight")
    val KEY_SIGNED_IN = booleanPreferencesKey("widget_signed_in")

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    data class DisplayState(
        val isActive: Boolean,
        val shiftId: String,
        val startTimeLabel: String,
        val dateLabel: String,
        val lastPunchLabel: String,
        val pendingCount: Int,
        val shiftStartEpochMillis: Long,
        val lastPunchEndEpochMillis: Long,
        val todayMinutes: Int,
        val dailyGoalMinutes: Int,
        /** True while a punch tapped on this widget is still executing — buttons show a spinner. */
        val isBusy: Boolean = false,
        /** False when no user is signed in — punch taps open the app instead of silently failing. */
        val isSignedIn: Boolean = true,
    ) {
        val elapsedHms: String
            get() = if (isActive && shiftStartEpochMillis > 0L) {
                WidgetTimeFormat.elapsedHm(shiftStartEpochMillis)
            } else {
                ""
            }

        val todayHms: String
            get() = WidgetTimeFormat.minutesToHm(todayMinutes)

        val todayShort: String
            get() = WidgetTimeFormat.minutesToShort(todayMinutes)

        val goalHoursLabel: String
            get() = "${dailyGoalMinutes / 60}h"

        val progressPercent: Int
            get() = if (dailyGoalMinutes <= 0) 0
            else ((todayMinutes.toFloat() / dailyGoalMinutes) * 100f).toInt().coerceIn(0, 100)

        val progressRemainderMinutes: Int
            get() = max(0, dailyGoalMinutes - todayMinutes)

        // User-facing sentences (status, action labels, hints) are resolved at
        // render time in WidgetLayouts via string resources with the in-app
        // locale — never computed here, where they cannot be localized.
        val primaryTimeLabel: String
            get() = when {
                isActive -> elapsedHms.ifEmpty { startTimeLabel }
                startTimeLabel != "--:--" -> startTimeLabel
                else -> todayHms
            }
    }

    fun read(prefs: Preferences): DisplayState = DisplayState(
        isActive = prefs[KEY_IS_ACTIVE] ?: false,
        shiftId = prefs[KEY_SHIFT_ID] ?: "",
        startTimeLabel = prefs[KEY_START_TIME_LABEL] ?: "--:--",
        dateLabel = prefs[KEY_DATE_LABEL] ?: "",
        lastPunchLabel = prefs[KEY_LAST_PUNCH_LABEL] ?: "",
        pendingCount = prefs[KEY_PENDING_COUNT] ?: 0,
        shiftStartEpochMillis = prefs[KEY_SHIFT_START_EPOCH] ?: 0L,
        lastPunchEndEpochMillis = prefs[KEY_LAST_PUNCH_END_EPOCH] ?: 0L,
        todayMinutes = prefs[KEY_TODAY_MINUTES] ?: 0,
        dailyGoalMinutes = prefs[KEY_DAILY_GOAL_MINUTES] ?: WidgetShiftState.DEFAULT_DAILY_GOAL_MINUTES,
        isBusy = prefs[KEY_ACTION_IN_FLIGHT] ?: false,
        isSignedIn = prefs[KEY_SIGNED_IN] ?: true,
    )

    fun writeFromShift(state: WidgetShiftState): (Preferences) -> Preferences = { prefs ->
        prefs.toMutablePreferences().apply {
            this[KEY_IS_ACTIVE] = state.isActive
            this[KEY_SHIFT_ID] = state.shiftId
            this[KEY_START_TIME_LABEL] = state.startTimeLabel
            this[KEY_DATE_LABEL] = state.dateLabel
            this[KEY_LAST_PUNCH_LABEL] = state.lastPunchLabel
            this[KEY_PENDING_COUNT] = state.pendingCount
            this[KEY_SHIFT_START_EPOCH] = state.shiftStartEpochMillis
            this[KEY_LAST_PUNCH_END_EPOCH] = state.lastPunchEndEpochMillis
            this[KEY_TODAY_MINUTES] = state.todayMinutes
            this[KEY_DAILY_GOAL_MINUTES] = state.dailyGoalMinutes
            // Fresh state means any in-flight punch has landed.
            this[KEY_ACTION_IN_FLIGHT] = false
            this[KEY_SIGNED_IN] = state.isSignedIn
        }
    }

    fun formatLastPunch(endTime: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val zdt = endTime.atZone(zone)
        val today = Instant.now().atZone(zone).toLocalDate()
        val dayLabel = when (zdt.toLocalDate()) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> zdt.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        }
        return "Last out • $dayLabel ${zdt.format(timeFormatter)}"
    }

    fun formatShiftStart(startTime: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        startTime.atZone(zone).format(timeFormatter)
}
