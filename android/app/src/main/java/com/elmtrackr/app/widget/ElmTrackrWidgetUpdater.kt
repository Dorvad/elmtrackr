package com.elmtrackr.app.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.elmtrackr.app.domain.model.Shift

object ElmTrackrWidgetUpdater {

    val KEY_IS_ACTIVE = booleanPreferencesKey("widget_is_active")
    val KEY_SHIFT_ID = stringPreferencesKey("widget_shift_id")
    val KEY_START_TIME_LABEL = stringPreferencesKey("widget_start_time_label")
    val KEY_DATE_LABEL = stringPreferencesKey("widget_date_label")
    val KEY_LAST_PUNCH_LABEL = stringPreferencesKey("widget_last_punch_label")
    val KEY_PENDING_COUNT = intPreferencesKey("widget_pending_count")

    suspend fun update(context: Context, shift: Shift?, pendingCount: Int = 0) {
        val state = WidgetStateMapper.map(shift, pendingCount)
        val manager = GlanceAppWidgetManager(context)
        val widget = ElmTrackrWidget()
        for (glanceId in manager.getGlanceIds(ElmTrackrWidget::class.java)) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[KEY_IS_ACTIVE] = state.isActive
                    this[KEY_SHIFT_ID] = state.shiftId
                    this[KEY_START_TIME_LABEL] = state.startTimeLabel
                    this[KEY_DATE_LABEL] = state.dateLabel
                    this[KEY_LAST_PUNCH_LABEL] = state.lastPunchLabel
                    this[KEY_PENDING_COUNT] = state.pendingCount
                }
            }
            widget.update(context, glanceId)
        }
    }
}
