package com.elmtrackr.app.widget

import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.first
import java.time.LocalDate

object WidgetContextLoader {

    suspend fun load(app: ElmTrackrApp, userId: String): WidgetContext {
        val settings = app.settingsRepository.getSettings(userId)
        val zone = settings?.let { WorkTimezone.zoneFor(it) }
            ?: java.time.ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val activeShift = app.shiftsRepository.observeActiveShift(userId).first()
        val lastCompletedShift = app.shiftsRepository
            .observeRecentCompletedShifts(userId, limit = 1)
            .first()
            .firstOrNull()
        val todayShifts = app.shiftsRepository
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
