package com.elmtrackr.app.wear

import android.content.Context
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.security.AppLockActionGuard
import com.elmtrackr.app.shortcuts.ClockOutActions
import com.elmtrackr.app.shortcuts.ClockInActions
import com.elmtrackr.wear.sync.PunchResult

object WearActions {

    suspend fun clockIn(
        context: Context,
        startTimeMillis: Long? = null,
        expectedUserId: String = "",
    ): PunchResult {
        if (!WearSyncPublisher.isSyncEnabled(context)) {
            return PunchResult(success = false, errorCode = "sync_disabled")
        }
        if (AppLockActionGuard.blockIfLocked(context)) {
            return PunchResult(success = false, errorCode = "app_locked")
        }
        val deps = AppEntryPoints.background(context)
        val currentUserId = deps.currentUserProvider().currentUserId()
            ?: return PunchResult(success = false, errorCode = "not_signed_in")
        if (expectedUserId.isNotBlank() && expectedUserId != currentUserId) {
            return PunchResult(success = false, errorCode = "user_mismatch")
        }
        return runCatching {
            ClockInActions.clockInHeadless(context, startTimeMillis)
                ?: return PunchResult(success = false, errorCode = "not_signed_in")
            WearSyncPublisher.refresh(context)
            PunchResult(success = true)
        }.getOrElse {
            PunchResult(success = false, errorCode = "clock_in_failed")
        }
    }

    suspend fun clockOut(
        context: Context,
        endTimeMillis: Long? = null,
        expectedUserId: String = "",
    ): PunchResult {
        if (!WearSyncPublisher.isSyncEnabled(context)) {
            return PunchResult(success = false, errorCode = "sync_disabled")
        }
        if (AppLockActionGuard.blockIfLocked(context)) {
            return PunchResult(success = false, errorCode = "app_locked")
        }
        val currentUserId = AppEntryPoints.background(context).currentUserProvider().currentUserId()
            ?: return PunchResult(success = false, errorCode = "not_signed_in")
        if (expectedUserId.isNotBlank() && expectedUserId != currentUserId) {
            return PunchResult(success = false, errorCode = "user_mismatch")
        }
        return when (ClockOutActions.clockOutActiveShift(context, endTimeMillis)) {
            ClockOutActions.Result.CLOCKED_OUT -> {
                WearSyncPublisher.refresh(context)
                PunchResult(success = true)
            }
            ClockOutActions.Result.NO_ACTIVE_SHIFT ->
                PunchResult(success = false, errorCode = "no_active_shift")
        }
    }
}
