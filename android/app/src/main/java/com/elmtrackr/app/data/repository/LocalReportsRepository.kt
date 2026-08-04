package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.PremiumProfileDao
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports read straight from Room.
 *
 * Both builders take compensation profiles and premium profiles. Calling them
 * without was the live remnant of the "premium profiles are inert" defect: any
 * surface fed by this repository resolved through the legacy settings rules
 * instead of the shift's own profile, so a premium shift reverted to the 150%
 * special-day rate and overtime hours were counted against the wrong threshold.
 * The ViewModels had been fixed; this path had not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocalReportsRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val settingsDao: SettingsDao,
    private val compensationProfileDao: CompensationProfileDao,
    private val premiumProfileDao: PremiumProfileDao,
) : ReportsRepository {

    override fun observeMonthlyReport(userId: String, year: Int, month: Int): Flow<MonthlyReport?> =
        settingsDao.observeSettings(userId).flatMapLatest { settingsEntity ->
            val settings = settingsEntity?.toDomain() ?: return@flatMapLatest flowOf(null)
            val zone = WorkTimezone.zoneFor(settings)
            val (from, to) = WorkTimezone.monthRangeEpochMillis(year, month, zone)
            combine(
                shiftDao.observeShiftsByDateRange(userId, from, to),
                compensationProfileDao.observeProfiles(userId),
            ) { entities, profileEntities ->
                MonthlyReportBuilder.buildMonthlyReport(
                    year = year,
                    month = month,
                    shifts = entities.map { it.toDomain() },
                    settings = settings,
                    profiles = profileEntities.map { it.toDomain() },
                )
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
            combine(
                shiftDao.observeShiftsByDateRange(userId, from, to),
                compensationProfileDao.observeProfiles(userId),
                premiumProfileDao.observeProfiles(userId),
            ) { entities, profileEntities, premiumEntities ->
                WeeklyBreakdownBuilder.groupByWeek(
                    shifts = entities.map { it.toDomain() },
                    settings = settings,
                    profiles = profileEntities.map { it.toDomain() },
                    premiumProfiles = premiumEntities.map { it.toDomain() },
                )
            }
        }
}
