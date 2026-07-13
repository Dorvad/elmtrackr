package com.elmtrackr.wear.tile

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.wear.tiles.TileService
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
        // The complication bridge doesn't cover the tile — without this the
        // tile's count-up freezes at whatever it showed when last rendered.
        TileService.getUpdater(applicationContext).requestUpdate(ElmTrackrTileService::class.java)
        if (app.wearStateRepository.snapshot.value.isActive) {
            schedule(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "elmtrackr_wear_tile_refresh"

        // Self-chaining one-shot: doWork re-schedules while a shift is active.
        // A PeriodicWorkRequest can't do this — WorkManager silently clamps
        // periods below 15 minutes, freezing the tile count-up between runs.
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WearTileRefreshWorker>()
                    .setInitialDelay(60, TimeUnit.SECONDS)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
