package com.elmtrackr.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class ClockOutWidgetAction : ActionCallback {

    companion object {
        val SHIFT_ID_KEY = ActionParameters.Key<String>("shift_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetActions.clockOut(context)
        WidgetActions.refreshWidgets(context)
    }
}
