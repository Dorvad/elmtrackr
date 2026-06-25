package com.elmtrackr.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.model.SyncResult
import kotlinx.coroutines.flow.first

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ElmTrackrApp
        val userId = app.appPreferences.preferences.first().lastActiveUserId
            ?: return Result.success()

        return when (app.syncRepository.syncAll(userId)) {
            SyncResult.Success -> Result.success()
            SyncResult.NotConfigured -> Result.success()
            is SyncResult.PartialSuccess -> Result.success()
            is SyncResult.Failure -> Result.retry()
        }
    }
}
