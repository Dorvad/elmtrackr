package com.elmtrackr.app.wear

import android.content.Context
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.security.AppLockActionGuard
import com.elmtrackr.app.shortcuts.ClockOutActions
import com.elmtrackr.app.domain.tasks.TaskClockInHelper
import com.elmtrackr.app.wear.WearSyncPublisher
import com.elmtrackr.wear.sync.PunchResult

object WearActions {

    suspend fun clockIn(context: Context): PunchResult {
        if (AppLockActionGuard.blockIfLocked(context)) {
            return PunchResult(success = false, errorCode = "app_locked")
        }
        val app = context.applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId()
            ?: return PunchResult(success = false, errorCode = "not_signed_in")
        val settings = app.settingsRepository.getSettings(userId)
        return runCatching {
            val task = TaskClockInHelper.resolveAutoTask(app.tasksRepository, app.shiftsRepository, userId)
            val params = TaskClockInHelper.paramsFromTask(task)
            app.shiftsRepository.clockIn(
                userId = userId,
                compensationProfileId = settings?.defaultCompensationProfileId,
                taskId = params.taskId,
                taskNameSnapshot = params.taskNameSnapshot,
                taskIconSnapshot = params.taskIconSnapshot,
                taskHourlyRateSnapshot = params.taskHourlyRateSnapshot,
            )
            task?.let { app.tasksRepository.markTaskUsed(userId, it.id) }
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
