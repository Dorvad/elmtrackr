package com.elmtrackr.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.elmtrackr.app.language.withAppLocale
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
        // Resolve the placed widgets first. The minute-by-minute refresh chain
        // used to be scheduled purely on "a shift is running", so a user who had
        // never placed a widget still paid for a worker, a database load and a
        // state write every minute of every shift, repainting nothing.
        val targets = glanceTargets(context)
        if (targets.isEmpty()) {
            WidgetTimerScheduler.cancel(context)
            return
        }

        val locale = context.withAppLocale().resources.configuration.locales[0] ?: java.util.Locale.getDefault()
        val state = WidgetStateMapper.map(contextData, locale)
        if (state.isActive) {
            WidgetTimerScheduler.schedule(context, state.shiftStartEpochMillis)
        } else {
            WidgetTimerScheduler.cancel(context)
        }
        pushState(context, state, targets)
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

    /** Every placed instance of every widget type, paired with its type. */
    private suspend fun glanceTargets(context: Context): List<Pair<GlanceAppWidget, GlanceId>> {
        val manager = GlanceAppWidgetManager(context)
        return widgetTypes.flatMap { widget ->
            manager.getGlanceIds(widget.javaClass).map { widget to it }
        }
    }

    private suspend fun pushState(
        context: Context,
        state: WidgetShiftState,
        targets: List<Pair<GlanceAppWidget, GlanceId>>,
    ) {
        for ((widget, glanceId) in targets) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) {
                WidgetPreferences.writeFromShift(state)(it)
            }
            widget.update(context, glanceId)
        }
    }

    /**
     * Flip the in-flight flag on a single widget and re-render it immediately,
     * so the tapped button shows a spinner while the punch executes.
     */
    suspend fun setActionInFlight(context: Context, glanceId: GlanceId, inFlight: Boolean) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetPreferences.KEY_ACTION_IN_FLIGHT] = inFlight
            }
        }
        rerender(context, glanceId)
    }

    private suspend fun rerender(context: Context, glanceId: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        for (widget in widgetTypes) {
            if (manager.getGlanceIds(widget.javaClass).contains(glanceId)) {
                widget.update(context, glanceId)
                return
            }
        }
    }
}
