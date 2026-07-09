package com.elmtrackr.app.wear

import android.content.Context
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.security.AppLockActionGuard
import com.elmtrackr.app.shortcuts.ClockOutActions
import com.elmtrackr.app.shortcuts.ClockInActions
import com.elmtrackr.wear.sync.PunchResult

object WearActions {

    suspend fun clockIn(context: Context): PunchResult {
        if (AppLockActionGuard.blockIfLocked(context)) {
            return PunchResult(success = false, errorCode = "app_locked")
        }
        val deps = AppEntryPoints.background(context)
        deps.currentUserProvider().currentUserId()
            ?: return PunchResult(success = false, errorCode = "not_signed_in")
        return runCatching {
            ClockInActions.clockInHeadless(context)
                ?: return PunchResult(success = false, errorCode = "not_signed_in")
            WearSyncPublisher.refresh(context)
            PunchResult(success = true)
        }.getOrElse {
            PunchResult(success = false, errorCode = "clock_in_failed")
        }
    }

    suspend fun clockOut(context: Context): PunchResult {
        if (AppLockActionGuard.blockIfLocked(context)) {
            return PunchResult(success = false, errorCode = "app_locked")
        }
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
