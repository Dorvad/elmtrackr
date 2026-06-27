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
        WidgetActions.clockIn(context)
    }
}
