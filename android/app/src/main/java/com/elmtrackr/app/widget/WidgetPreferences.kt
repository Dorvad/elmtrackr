package com.elmtrackr.app.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.Shift
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WidgetPreferences {

    val KEY_IS_ACTIVE = booleanPreferencesKey("widget_is_active")
    val KEY_SHIFT_ID = stringPreferencesKey("widget_shift_id")
    val KEY_START_TIME_LABEL = stringPreferencesKey("widget_start_time_label")
    val KEY_DATE_LABEL = stringPreferencesKey("widget_date_label")
    val KEY_LAST_PUNCH_LABEL = stringPreferencesKey("widget_last_punch_label")
    val KEY_PENDING_COUNT = intPreferencesKey("widget_pending_count")
    val KEY_SHIFT_START_EPOCH = longPreferencesKey("widget_shift_start_epoch")
    val KEY_LAST_PUNCH_END_EPOCH = longPreferencesKey("widget_last_punch_end_epoch")

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
    ) {
        val elapsedLabel: String
            get() {
                if (!isActive || shiftStartEpochMillis <= 0L) return ""
                val minutes = ((System.currentTimeMillis() - shiftStartEpochMillis) / 60_000L)
                    .toInt()
                    .coerceAtLeast(0)
                return ShiftDurationCalculator.formatMinutes(minutes)
            }

        val primaryTimeLabel: String
            get() = if (isActive) elapsedLabel.ifEmpty { startTimeLabel } else startTimeLabel

        val statusLabel: String
            get() = if (isActive) "CLOCKED IN" else "CLOCKED OUT"

        val subtitleLabel: String
            get() = when {
                isActive -> "Since $startTimeLabel"
                lastPunchLabel.isNotBlank() -> lastPunchLabel
                else -> "Tap Clock In to start"
            }

        val actionLabel: String
            get() = if (isActive) "Clock Out" else "Clock In"
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
