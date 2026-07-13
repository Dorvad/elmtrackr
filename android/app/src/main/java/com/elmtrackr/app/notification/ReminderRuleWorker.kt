package com.elmtrackr.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/**
 * Fires one configured [ReminderRule]. Repeating rules (after-overtime
 * intervals) re-schedule themselves until clock-out or the feature is
 * disabled; everything else fires once per shift.
 */
@HiltWorker
class ReminderRuleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val currentUserProvider: CurrentUserProvider,
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val rule = ReminderRulesCodec.decode(inputData.getString(KEY_RULE_JSON))
            ?.firstOrNull()
            ?: return Result.success()
        val contextData = OvertimeReminderSupport.loadContext(
            currentUserProvider,
            shiftsRepository,
            settingsRepository,
        ) ?: return Result.success()
        val shift = contextData.shift
        val threshold = contextData.thresholdMinutes
        val notifications = ActiveShiftNotificationManager(applicationContext)

        when (rule.kind) {
            ReminderTriggerKind.BEFORE_OVERTIME -> {
                // Stale by the time it ran (device dozed past the threshold) — stay quiet.
                if (!ActiveShiftNotificationManager.isShiftOverThreshold(shift.startTime, threshold.toLong())) {
                    notifications.showOvertimePreWarning(shift, rule.offsetMinutes)
                }
            }

            ReminderTriggerKind.AFTER_OVERTIME -> {
                if (rule.offsetMinutes <= 0) {
                    notifications.showOvertimeAtThreshold(shift)
                } else {
                    if (ActiveShiftNotificationManager.isShiftOverThreshold(shift.startTime, threshold.toLong())) {
                        // Actual time in overtime, not a 60-minute bucket count:
                        // with a 30-minute repeat interval the old hour-count
                        // said "1 hour" at +30 and again at +60.
                        val minutes = OvertimeReminderPolicy.overtimeMinutesElapsed(
                            threshold,
                            shift.startTime,
                            Instant.now(),
                        ).coerceAtLeast(rule.offsetMinutes.toLong())
                        val label = String.format(java.util.Locale.US, "%d:%02d", minutes / 60, minutes % 60)
                        notifications.showOvertimeHourlyReminder(shift, label)
                    }
                    OvertimeReminderScheduler.enqueueRule(applicationContext, rule, shift, threshold)
                }
            }

            ReminderTriggerKind.AT_TIME -> {
                notifications.showScheduledTimeReminder(shift)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_RULE_JSON = "rule_json"
    }
}
