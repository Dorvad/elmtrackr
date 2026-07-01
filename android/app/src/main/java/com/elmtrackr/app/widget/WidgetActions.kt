package com.elmtrackr.app.widget

import android.content.Context
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.security.AppLockActionGuard
import com.elmtrackr.app.shortcuts.ClockOutActions
import com.elmtrackr.app.domain.tasks.TaskClockInHelper
import com.elmtrackr.app.wear.WearSyncPublisher

object WidgetActions {

    suspend fun clockIn(context: Context): Boolean {
        if (AppLockActionGuard.blockIfLocked(context)) return false
        val app = context.applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId() ?: return false
        return runCatching {
            val settings = app.settingsRepository.getSettings(userId)
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
            WearSyncPublisher.refresh(context.applicationContext)
            true
        }.getOrDefault(false)
    }

    suspend fun clockOut(context: Context): ClockOutActions.Result {
        if (AppLockActionGuard.blockIfLocked(context)) return ClockOutActions.Result.NO_ACTIVE_SHIFT
        val result = ClockOutActions.clockOutActiveShift(context)
        ClockOutActions.showShortcutFeedback(context, result)
        return result
    }
}
