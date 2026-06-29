package com.elmtrackr.app.notification

import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import kotlinx.coroutines.flow.firstOrNull

internal data class OvertimeReminderContext(
    val shift: Shift,
    val settings: UserSettings,
    val thresholdMinutes: Int,
)

internal object OvertimeReminderSupport {
    suspend fun loadContext(app: ElmTrackrApp): OvertimeReminderContext? {
        val userId = app.currentUserProvider.currentUserId() ?: return null
        val shift = app.shiftsRepository.observeActiveShift(userId).firstOrNull() ?: return null
        val settings = app.settingsRepository.getSettings(userId) ?: return null
        if (!settings.featuresOvertimeReminders) return null
        val threshold = settings.dailyOvertimeThresholdMinutes
            .takeIf { it > 0 }
            ?: OvertimeReminderPolicy.FALLBACK_THRESHOLD_MINUTES.toInt()
        return OvertimeReminderContext(shift, settings, threshold)
    }
}
