package com.elmtrackr.app.widget

import android.content.Context
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.security.AppLockActionGuard
import com.elmtrackr.app.shortcuts.ClockOutActions
import com.elmtrackr.app.domain.tasks.TaskClockInHelper
import com.elmtrackr.app.wear.WearSyncPublisher

object WidgetActions {

    suspend fun clockIn(context: Context): Boolean {
        if (AppLockActionGuard.blockIfLocked(context)) return false
        val deps = AppEntryPoints.background(context)
        val userId = deps.currentUserProvider().currentUserId() ?: return false
        return runCatching {
            val settings = deps.settingsRepository().getSettings(userId)
            val task = TaskClockInHelper.resolveAutoTask(
                deps.tasksRepository(),
                deps.shiftsRepository(),
                userId,
            )
            val params = TaskClockInHelper.paramsFromTask(task)
            deps.shiftsRepository().clockIn(
                userId = userId,
                compensationProfileId = settings?.defaultCompensationProfileId,
                taskId = params.taskId,
                taskNameSnapshot = params.taskNameSnapshot,
                taskIconSnapshot = params.taskIconSnapshot,
                taskHourlyRateSnapshot = params.taskHourlyRateSnapshot,
            )
            task?.let { deps.tasksRepository().markTaskUsed(userId, it.id) }
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
