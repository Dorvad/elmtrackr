package com.elmtrackr.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class ClockInWidgetAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        ElmTrackrWidgetUpdater.setActionInFlight(context, glanceId, true)
        try {
            WidgetActions.clockIn(context)
            WidgetActions.refreshWidgets(context)
        } finally {
            // refreshWidgets normally clears the flag with fresh state; this is
            // the safety net for early returns (no user) and failures.
            ElmTrackrWidgetUpdater.setActionInFlight(context, glanceId, false)
        }
    }
}
