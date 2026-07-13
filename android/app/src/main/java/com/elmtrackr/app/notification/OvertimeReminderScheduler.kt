package com.elmtrackr.app.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Schedules the user's configured reminder rules (see [ReminderRulesStore])
 * for the active shift. Each rule gets its own unique one-time work item;
 * repeating rules re-enqueue themselves from [ReminderRuleWorker].
 */
object OvertimeReminderScheduler {
    const val RULE_TAG = "overtime_reminder_rules"
    private const val RULE_WORK_PREFIX = "overtime_rule_"

    // Pre-rules work names, cancelled so pending jobs from older app versions don't linger.
    private val LEGACY_WORK_NAMES = listOf(
        "overtime_pre_warning",
        "overtime_at_threshold",
        "overtime_hourly",
        "long_shift_reminder",
    )

    suspend fun scheduleForActiveShift(context: Context, shift: Shift, settings: UserSettings?) {
        cancelAll(context)

        if (settings?.featuresOvertimeReminders != true) return

        val threshold = settings.dailyOvertimeThresholdMinutes
            .takeIf { it > 0 }
            ?: OvertimeReminderPolicy.FALLBACK_THRESHOLD_MINUTES.toInt()
        val zone = WorkTimezone.zoneFor(settings)
        val now = Instant.now()

        ReminderRulesStore.current(context).forEach { rule ->
            enqueueRule(context, rule, shift, threshold, now, zone)
        }
    }

    fun enqueueRule(
        context: Context,
        rule: ReminderRule,
        shift: Shift,
        thresholdMinutes: Int,
        now: Instant = Instant.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ) {
        val delay = OvertimeReminderPolicy.delayMinutesForRule(rule, thresholdMinutes, shift.startTime, now, zone)
        if (delay < 0) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            RULE_WORK_PREFIX + rule.id,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderRuleWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setInputData(
                    Data.Builder()
                        .putString(ReminderRuleWorker.KEY_RULE_JSON, ReminderRulesCodec.encode(listOf(rule)))
                        .build(),
                )
                .addTag(RULE_TAG)
                .build(),
        )
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(RULE_TAG)
        LEGACY_WORK_NAMES.forEach(workManager::cancelUniqueWork)
    }
}
