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

        // The at-threshold alert — AFTER_OVERTIME with no repeat interval — goes to
        // AlarmManager instead, because Doze can defer WorkManager by hours and this
        // is the notification the feature exists for. Only when the alarm is actually
        // set do we skip the work item; otherwise both paths would fire, or neither.
        if (rule.kind == ReminderTriggerKind.AFTER_OVERTIME && rule.offsetMinutes <= 0) {
            val scheduled = OvertimeThresholdAlarm.schedule(
                context = context,
                shiftStart = shift.startTime,
                thresholdMinutes = thresholdMinutes,
                now = now,
            )
            if (scheduled) return
        }

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
        // The alarm is not WorkManager's to cancel. Missing this would leave a
        // pending alert after clock-out — the receiver would find no active shift and
        // stay quiet, but relying on that is relying on a second bug not to bite.
        OvertimeThresholdAlarm.cancel(context)
    }
}
