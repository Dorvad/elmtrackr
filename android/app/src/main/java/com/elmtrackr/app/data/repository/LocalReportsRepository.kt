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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LocalReportsRepository(
    private val shiftDao: ShiftDao,
    private val settingsDao: SettingsDao,
) : ReportsRepository {

    override fun observeMonthlyReport(userId: String, year: Int, month: Int): Flow<MonthlyReport?> =
        settingsDao.observeSettings(userId).flatMapLatest { settingsEntity ->
            val settings = settingsEntity?.toDomain() ?: return@flatMapLatest flowOf(null)
            val zone = WorkTimezone.zoneFor(settings)
            val (from, to) = WorkTimezone.monthRangeEpochMillis(year, month, zone)
            shiftDao.observeShiftsByDateRange(userId, from, to).map { entities ->
                val shifts = entities.map { it.toDomain() }
                MonthlyReportBuilder.buildMonthlyReport(year, month, shifts, settings)
            }
        }

    override fun observeWeeklyTotals(userId: String): Flow<List<WeeklyTotals>> =
        settingsDao.observeSettings(userId).flatMapLatest { settingsEntity ->
            val settings = settingsEntity?.toDomain()
            val zone = settings?.let { WorkTimezone.zoneFor(it) }
                ?: java.time.ZoneOffset.UTC
            val now = LocalDate.now(zone)
            val twelveWeeksAgo = now.minusWeeks(12)
            val from = twelveWeeksAgo.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            shiftDao.observeShiftsByDateRange(userId, from, to).map { entities ->
                val shifts = entities.map { it.toDomain() }
                WeeklyBreakdownBuilder.groupByWeek(shifts, settings = settings)
            }
        }
}
