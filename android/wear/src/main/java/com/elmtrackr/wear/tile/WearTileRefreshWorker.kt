package com.elmtrackr.wear.tile

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elmtrackr.wear.ElmTrackrWearApp
import com.elmtrackr.wear.sync.ElmTrackrComplicationBridge
import java.util.concurrent.TimeUnit

class WearTileRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ElmTrackrWearApp
        app.wearStateRepository.refreshFromDataLayer()
        ElmTrackrComplicationBridge.requestUpdateAll(applicationContext)
        if (app.wearStateRepository.snapshot.value.isActive) {
            schedule(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "elmtrackr_wear_tile_refresh"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequestBuilder<WearTileRefreshWorker>(1, TimeUnit.MINUTES).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
