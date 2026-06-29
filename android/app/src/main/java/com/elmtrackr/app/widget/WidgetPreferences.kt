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
    ) {
        val elapsedHms: String
            get() = if (isActive && shiftStartEpochMillis > 0L) {
                WidgetTimeFormat.elapsedHms(shiftStartEpochMillis)
            } else {
                ""
            }

        val todayHms: String
            get() = WidgetTimeFormat.minutesToHms(todayMinutes)

        val todayShort: String
            get() = WidgetTimeFormat.minutesToShort(todayMinutes)

        val goalHoursLabel: String
            get() = "${dailyGoalMinutes / 60}h"

        val progressPercent: Int
            get() = if (dailyGoalMinutes <= 0) 0
            else ((todayMinutes.toFloat() / dailyGoalMinutes) * 100f).toInt().coerceIn(0, 100)

        val progressRemainderMinutes: Int
            get() = max(0, dailyGoalMinutes - todayMinutes)

        val statusLabel: String
            get() = if (isActive) "CLOCKED IN" else "CLOCKED OUT"

        val primaryTimeLabel: String
            get() = when {
                isActive -> elapsedHms.ifEmpty { startTimeLabel }
                startTimeLabel != "--:--" -> startTimeLabel
                else -> todayHms
            }

        val progressSubLabel: String
            get() = if (isActive) {
                "$progressPercent% of an ${goalHoursLabel} day · started $startTimeLabel"
            } else {
                val remain = WidgetTimeFormat.minutesToShort(progressRemainderMinutes)
                "$progressPercent% of an ${goalHoursLabel} day · ${remain} to goal"
            }

        val singleToggleSecondaryTop: String
            get() = if (isActive) "on shift" else "last punch"

        val singleToggleSecondaryBottom: String
            get() = when {
                isActive -> "since $startTimeLabel"
                lastPunchLabel.isNotBlank() -> lastPunchLabel.removePrefix("Last out • ")
                todayMinutes > 0 -> "Today $todayShort"
                else -> "tap to start"
            }

        val tallLoggedLabel: String
            get() = if (todayMinutes > 0) "Today $todayShort logged" else "No time logged today"

        val tallActiveSubLabel: String
            get() = "Since $startTimeLabel · Today $todayShort"

        val actionLabel: String
            get() = if (isActive) "PUNCH OUT" else "PUNCH IN"

        val actionHint: String
            get() = if (isActive) "tap to end shift" else "tap to start shift"
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
