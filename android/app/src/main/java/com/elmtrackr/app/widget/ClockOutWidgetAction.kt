package com.elmtrackr.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.elmtrackr.app.ElmTrackrApp

class ClockOutWidgetAction : ActionCallback {

    companion object {
        val SHIFT_ID_KEY = ActionParameters.Key<String>("shift_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val shiftId = parameters[SHIFT_ID_KEY] ?: return
        val app = context.applicationContext as ElmTrackrApp
        app.shiftsRepository.clockOut(shiftId)
    }
}
