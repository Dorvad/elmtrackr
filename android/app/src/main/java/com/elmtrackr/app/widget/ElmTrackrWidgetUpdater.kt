package com.elmtrackr.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings

object ElmTrackrWidgetUpdater {

    val KEY_IS_ACTIVE = WidgetPreferences.KEY_IS_ACTIVE
    val KEY_SHIFT_ID = WidgetPreferences.KEY_SHIFT_ID
    val KEY_START_TIME_LABEL = WidgetPreferences.KEY_START_TIME_LABEL
    val KEY_DATE_LABEL = WidgetPreferences.KEY_DATE_LABEL
    val KEY_LAST_PUNCH_LABEL = WidgetPreferences.KEY_LAST_PUNCH_LABEL
    val KEY_PENDING_COUNT = WidgetPreferences.KEY_PENDING_COUNT

    private val widgetTypes: List<GlanceAppWidget> = listOf(
        ElmTrackrWidget(),
        ElmTrackrMinimalWidget(),
        ElmTrackrAuroraWidget(),
        ElmTrackrRingWidget(),
        ElmTrackrBigActionWidget(),
    )

    suspend fun update(context: Context, contextData: WidgetContext) {
        val state = WidgetStateMapper.map(contextData)
        if (state.isActive) {
            WidgetTimerScheduler.schedule(context)
        } else {
            WidgetTimerScheduler.cancel(context)
        }
        pushState(context, state)
    }

    suspend fun update(
        context: Context,
        shift: Shift?,
        lastCompletedShift: Shift? = null,
        todayShifts: List<Shift> = emptyList(),
        settings: UserSettings? = null,
        pendingCount: Int = 0,
    ) {
        update(
            context,
            WidgetContext(
                activeShift = shift,
                lastCompletedShift = lastCompletedShift,
                todayShifts = todayShifts,
                settings = settings,
                pendingCount = pendingCount,
            ),
        )
    }

    private suspend fun pushState(context: Context, state: WidgetShiftState) {
        val manager = GlanceAppWidgetManager(context)
        for (widget in widgetTypes) {
            for (glanceId in manager.getGlanceIds(widget.javaClass)) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) {
                    WidgetPreferences.writeFromShift(state)(it)
                }
                widget.update(context, glanceId)
            }
        }
    }
}
