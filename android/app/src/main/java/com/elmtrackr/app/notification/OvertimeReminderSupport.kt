package com.elmtrackr.app.notification

import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.flow.firstOrNull

internal data class OvertimeReminderContext(
    val shift: Shift,
    val settings: UserSettings,
    val thresholdMinutes: Int,
)

internal object OvertimeReminderSupport {
    suspend fun loadContext(
        currentUserProvider: CurrentUserProvider,
        shiftsRepository: ShiftsRepository,
        settingsRepository: SettingsRepository,
    ): OvertimeReminderContext? {
        val userId = currentUserProvider.currentUserId() ?: return null
        val shift = shiftsRepository.observeActiveShift(userId).firstOrNull() ?: return null
        val settings = settingsRepository.getSettings(userId) ?: return null
        if (!settings.featuresOvertimeReminders) return null
        val threshold = settings.dailyOvertimeThresholdMinutes
            .takeIf { it > 0 }
            ?: OvertimeReminderPolicy.FALLBACK_THRESHOLD_MINUTES.toInt()
        return OvertimeReminderContext(shift, settings, threshold)
    }
}
