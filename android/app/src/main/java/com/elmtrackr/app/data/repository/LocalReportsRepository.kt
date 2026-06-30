package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.WeeklyBreakdownBuilder
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.WeeklyTotals
import com.elmtrackr.app.domain.repository.ReportsRepository
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LocalReportsRepository(
    private val shiftDao: ShiftDao,
    private val settingsDao: SettingsDao,
) : ReportsRepository {

    override fun observeMonthlyReport(userId: String, year: Int, month: Int): Flow<MonthlyReport?> =
        combine(
            settingsDao.observeSettings(userId),
            shiftDao.observeShifts(userId),
        ) { settingsEntity, allShifts ->
            val settings = settingsEntity?.toDomain() ?: return@combine null
            val zone = WorkTimezone.zoneFor(settings)
            val (from, to) = WorkTimezone.monthRangeEpochMillis(year, month, zone)
            val shifts = allShifts
                .filter { it.startTime in from until to }
                .map { it.toDomain() }
            MonthlyReportBuilder.buildMonthlyReport(year, month, shifts, settings)
        }

    override fun observeWeeklyTotals(userId: String): Flow<List<WeeklyTotals>> =
        combine(
            settingsDao.observeSettings(userId),
            shiftDao.observeShifts(userId),
        ) { settingsEntity, shiftEntities ->
            val settings = settingsEntity?.toDomain()
            val zone = settings?.let { WorkTimezone.zoneFor(it) }
                ?: java.time.ZoneOffset.UTC
            val now = java.time.LocalDate.now(zone)
            val twelveWeeksAgo = now.minusWeeks(12)
            val from = twelveWeeksAgo.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val shifts = shiftEntities
                .filter { it.startTime in from until to }
                .map { it.toDomain() }
            WeeklyBreakdownBuilder.groupByWeek(shifts, settings = settings)
        }
}
