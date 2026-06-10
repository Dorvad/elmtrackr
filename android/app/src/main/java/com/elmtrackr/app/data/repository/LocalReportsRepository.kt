package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.WeeklyBreakdownBuilder
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.WeeklyTotals
import com.elmtrackr.app.domain.repository.ReportsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.ZoneOffset

class LocalReportsRepository(
    private val shiftDao: ShiftDao,
    private val settingsDao: SettingsDao,
) : ReportsRepository {

    override fun observeMonthlyReport(userId: String, year: Int, month: Int): Flow<MonthlyReport?> {
        val ym = YearMonth.of(year, month)
        val from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return combine(
            shiftDao.observeShiftsByDateRange(userId, from, to),
            settingsDao.observeSettings(userId),
        ) { shiftEntities, settingsEntity ->
            val settings = settingsEntity?.toDomain() ?: return@combine null
            val shifts = shiftEntities.map { it.toDomain() }
            MonthlyReportBuilder.buildMonthlyReport(year, month, shifts, settings)
        }
    }

    override fun observeWeeklyTotals(userId: String): Flow<List<WeeklyTotals>> {
        // Observe shifts from the last 12 weeks
        val now = java.time.LocalDate.now(ZoneOffset.UTC)
        val twelveWeeksAgo = now.minusWeeks(12)
        val from = twelveWeeksAgo.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val to = now.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return shiftDao.observeShiftsByDateRange(userId, from, to).combine(
            settingsDao.observeSettings(userId)
        ) { shiftEntities, _ ->
            val shifts = shiftEntities.map { it.toDomain() }
            WeeklyBreakdownBuilder.groupByWeek(shifts)
        }
    }
}
