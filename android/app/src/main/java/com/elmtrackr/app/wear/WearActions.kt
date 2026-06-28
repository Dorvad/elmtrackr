package com.elmtrackr.app.wear

import android.content.Context
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.shortcuts.ClockOutActions
import com.elmtrackr.wear.sync.PunchResult

object WearActions {

    suspend fun clockIn(context: Context): PunchResult {
        val app = context.applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId()
            ?: return PunchResult(success = false, errorCode = "not_signed_in")
        val settings = app.settingsRepository.getSettings(userId)
        return runCatching {
            app.shiftsRepository.clockIn(userId, settings?.defaultCompensationProfileId)
            WearSyncPublisher.refresh(context)
            PunchResult(success = true)
        }.getOrElse {
            PunchResult(success = false, errorCode = "clock_in_failed")
        }
    }

    suspend fun clockOut(context: Context): PunchResult {
        return when (ClockOutActions.clockOutActiveShift(context)) {
            ClockOutActions.Result.CLOCKED_OUT -> {
                WearSyncPublisher.refresh(context)
                PunchResult(success = true)
            }
            ClockOutActions.Result.NO_ACTIVE_SHIFT ->
                PunchResult(success = false, errorCode = "no_active_shift")
        }
    }
}
