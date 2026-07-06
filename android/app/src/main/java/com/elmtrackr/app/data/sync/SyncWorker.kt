package com.elmtrackr.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.domain.CurrentUserProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = currentUserProvider.currentUserId() ?: return Result.success()
        return when (syncRepository.syncAll(userId)) {
            is SyncResult.Success -> {
                if (syncRepository.hasPendingWork(userId)) {
                    syncScheduler.scheduleFollowUp()
                }
                Result.success()
            }
            // Without a remote backend there is nothing a follow-up run could do;
            // rescheduling here would spin sync workers forever.
            SyncResult.NotConfigured -> Result.success()
            // Retrying cannot help until the user signs in again; periodic sync
            // picks the queue back up once a fresh session exists.
            SyncResult.AuthExpired -> Result.failure()
            // Backoff retries are capped; periodic sync keeps retrying after that
            // without hammering the backend from an ever-growing retry chain.
            is SyncResult.Error ->
                if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 5
    }
}
