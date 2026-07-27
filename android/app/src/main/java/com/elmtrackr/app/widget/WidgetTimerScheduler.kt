package com.elmtrackr.app.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WidgetTimerScheduler {

    private const val WORK_NAME = "elmtrackr_widget_timer_refresh"

    /**
     * Schedules the next repaint aligned to the shift's next minute rollover.
     * The widgets show an h:mm count-up, so one run per minute — landing just
     * after the label actually changes — replaces the previous 10-second
     * polling (six DB loads and full repaints per minute, all shift long).
     */
    fun schedule(context: Context, shiftStartEpochMillis: Long = 0L) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInitialDelay(alignedDelaySeconds(shiftStartEpochMillis), TimeUnit.SECONDS)
                .build(),
        )
    }

    /** Seconds until just after the count-up's next minute flip. */
    internal fun alignedDelaySeconds(
        shiftStartEpochMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        if (shiftStartEpochMillis <= 0L) return 60L
        val elapsedSeconds = ((nowMillis - shiftStartEpochMillis) / 1000L).coerceAtLeast(0L)
        val untilRollover = 60L - (elapsedSeconds % 60L)
        // +1s margin lands the run after the flip; the floor keeps a run
        // scheduled right at a boundary from re-firing immediately.
        return (untilRollover + 1L).coerceIn(5L, 61L)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
