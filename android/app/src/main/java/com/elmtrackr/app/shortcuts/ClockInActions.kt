package com.elmtrackr.app.shortcuts

import android.content.Context
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.tasks.TaskClockInHelper

/**
 * Shared clock-in for headless surfaces (widget, Wear, shortcuts) that applies
 * the same behavior as the dashboard: the default compensation profile is
 * created/resolved before punching, the auto-suggested task is attached, and a
 * genuine first-ever punch leaves a pending marker so the dashboard can still
 * show the one-time celebration on the next visit.
 */
object ClockInActions {

    suspend fun clockInHeadless(context: Context): Shift? {
        val deps = AppEntryPoints.background(context)
        val userId = deps.currentUserProvider().currentUserId() ?: return null
        val settings = deps.settingsRepository().getSettings(userId)
        // Parity with DashboardViewModel.clockIn: without this, a widget-first
        // user punches with a null profile and the default row is never created.
        val defaultProfile = runCatching {
            deps.compensationProfilesRepository().ensureMigrated(userId)
        }.getOrNull()
        val task = TaskClockInHelper.resolveAutoTask(
            deps.tasksRepository(),
            deps.shiftsRepository(),
            userId,
        )
        val params = TaskClockInHelper.paramsFromTask(task)
        val prefs = deps.appPreferences().currentPreferences()
        val isFirstClockIn = !deps.shiftsRepository().hasAnyShifts(userId) &&
            !prefs.firstClockInCelebrated
        val shift = deps.shiftsRepository().clockIn(
            userId = userId,
            compensationProfileId = settings?.defaultCompensationProfileId ?: defaultProfile?.id,
            taskId = params.taskId,
            taskNameSnapshot = params.taskNameSnapshot,
            taskIconSnapshot = params.taskIconSnapshot,
            taskHourlyRateSnapshot = params.taskHourlyRateSnapshot,
        )
        task?.let { deps.tasksRepository().markTaskUsed(userId, it.id) }
        if (isFirstClockIn) {
            deps.appPreferences().setFirstClockInCelebrationPending(true)
        }
        return shift
    }
}
