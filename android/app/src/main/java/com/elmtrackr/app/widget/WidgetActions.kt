package com.elmtrackr.app.widget

import android.content.Context
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.security.AppLockActionGuard
import com.elmtrackr.app.shortcuts.ClockInActions
import com.elmtrackr.app.shortcuts.ClockOutActions
import com.elmtrackr.app.wear.WearSyncPublisher

object WidgetActions {

    suspend fun clockIn(context: Context): Boolean {
        if (AppLockActionGuard.blockIfLocked(context)) return false
        return runCatching {
            val shift = ClockInActions.clockInHeadless(context) ?: return false
            WearSyncPublisher.refresh(context.applicationContext)
            shift.isActive
        }.getOrDefault(false)
    }

    suspend fun clockOut(context: Context): ClockOutActions.Result {
        if (AppLockActionGuard.blockIfLocked(context)) return ClockOutActions.Result.NO_ACTIVE_SHIFT
        val result = ClockOutActions.clockOutActiveShift(context)
        ClockOutActions.showShortcutFeedback(context, result)
        return result
    }

    /**
     * Push fresh state to every widget right away. The app-scope shift
     * observer refreshes them too, but doing it here means the tap feedback
     * doesn't wait on that pipeline.
     */
    suspend fun refreshWidgets(context: Context) {
        val deps = AppEntryPoints.background(context)
        val userId = deps.currentUserProvider().currentUserId() ?: return
        runCatching {
            ElmTrackrWidgetUpdater.update(context, WidgetContextLoader.load(deps, userId))
        }
    }
}
