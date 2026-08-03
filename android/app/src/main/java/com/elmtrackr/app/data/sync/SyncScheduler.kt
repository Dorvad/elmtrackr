package com.elmtrackr.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncScheduler(
    private val context: Context,
) : SyncTrigger {

    // KEEP: a burst of local edits enqueues one sync instead of chaining one per edit.
    // Edits that land while a sync is already running are picked up by the follow-up
    // sync the worker schedules when pending work remains.
    override fun schedule() {
        enqueueOneTime(ExistingWorkPolicy.KEEP)
    }

    // APPEND_OR_REPLACE: called from inside the running worker, where KEEP would be
    // dropped because the current work is still active.
    //
    // The delay is the second half of the loop guard. SyncWorker only reaches here
    // when retryable work remains, but "retryable" is a snapshot: a row that the
    // server is currently rejecting with a transient error is retryable on every
    // pass. Without a delay those passes ran back to back at network speed. A
    // minute keeps a genuine follow-up (edits made while the sync was running)
    // prompt while making a stuck chain cheap rather than a battery drain.
    fun scheduleFollowUp() {
        enqueueOneTime(
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            initialDelaySeconds = FOLLOW_UP_DELAY_SECONDS,
        )
    }

    private fun enqueueOneTime(policy: ExistingWorkPolicy, initialDelaySeconds: Long = 0L) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .apply {
                if (initialDelaySeconds > 0L) {
                    setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                }
            }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            policy,
            request,
        )
    }

    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val ONE_TIME_WORK_NAME = "elmtrackr-sync-once"
        const val PERIODIC_WORK_NAME = "elmtrackr-sync-periodic"
        const val WORK_TAG = "elmtrackr-sync"

        // 30s, 60s, 120s… caps retries well under SyncWorker.MAX_RETRY_ATTEMPTS
        // while staying gentle on the backend during outages.
        const val RETRY_BACKOFF_SECONDS = 30L

        const val FOLLOW_UP_DELAY_SECONDS = 60L
    }
}
