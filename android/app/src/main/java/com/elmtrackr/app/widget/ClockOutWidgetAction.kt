package com.elmtrackr.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper

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
        val userId = app.currentUserProvider.currentUserId() ?: return
        val shift = app.shiftsRepository.getShiftById(shiftId) ?: return
        val settings = app.settingsRepository.getSettings(userId) ?: return
        val profiles = app.compensationProfilesRepository.getProfiles(userId)
        val snapshot = ShiftCompensationHelper.buildClockOutSnapshot(shift, settings, profiles)
        app.shiftsRepository.clockOut(shiftId, compensationSnapshot = snapshot)
    }
}
