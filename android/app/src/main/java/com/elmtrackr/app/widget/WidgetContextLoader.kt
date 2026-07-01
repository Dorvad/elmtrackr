package com.elmtrackr.app.widget

import com.elmtrackr.app.di.entrypoint.BackgroundEntryPoint
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.first
import java.time.LocalDate

object WidgetContextLoader {

    suspend fun load(deps: BackgroundEntryPoint, userId: String): WidgetContext =
        load(deps.shiftsRepository(), deps.settingsRepository(), userId)

    suspend fun load(
        shiftsRepository: ShiftsRepository,
        settingsRepository: SettingsRepository,
        userId: String,
    ): WidgetContext {
        val settings = settingsRepository.getSettings(userId)
        val zone = settings?.let { WorkTimezone.zoneFor(it) }
            ?: java.time.ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val activeShift = shiftsRepository.observeActiveShift(userId).first()
        val lastCompletedShift = shiftsRepository
            .observeRecentCompletedShifts(userId, limit = 1)
            .first()
            .firstOrNull()
        val todayShifts = shiftsRepository
            .observeShiftsForDay(userId, zone, today)
            .first()
        return WidgetContext(
            activeShift = activeShift,
            lastCompletedShift = lastCompletedShift,
            todayShifts = todayShifts,
            settings = settings,
        )
    }
}
