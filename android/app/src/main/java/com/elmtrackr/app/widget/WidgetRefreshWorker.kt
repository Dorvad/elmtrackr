package com.elmtrackr.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.ElmTrackrApp

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId() ?: return Result.success()
        val widgetContext = WidgetContextLoader.load(app, userId)
        if (widgetContext.activeShift == null) {
            WidgetTimerScheduler.cancel(applicationContext)
            return Result.success()
        }

        ElmTrackrWidgetUpdater.update(applicationContext, widgetContext)
        WidgetTimerScheduler.schedule(applicationContext)
        return Result.success()
    }
}
