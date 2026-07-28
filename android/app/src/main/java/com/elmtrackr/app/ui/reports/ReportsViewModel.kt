package com.elmtrackr.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.di.ComputationDispatcher
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.DailyInsightsBuilder
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.repository.PremiumProfilesRepository
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.OvernightShiftDetector
import com.elmtrackr.app.domain.WeeklyBreakdownBuilder
import com.elmtrackr.app.domain.ReportInsightsBuilder
import com.elmtrackr.app.domain.TaskMonthlyReportBuilder
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.TaskMonthlyBreakdown
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.ReportsRepository
import com.elmtrackr.app.domain.repository.RefundsRepository
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportsRepository: ReportsRepository,
    private val shiftsRepository: ShiftsRepository,
    private val tasksRepository: TasksRepository,
    private val settingsRepository: SettingsRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val refundsRepository: RefundsRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val premiumProfilesRepository: PremiumProfilesRepository,
    private val refundReceiptStorage: RefundReceiptStorage?,
    // Injected so tests can run the payroll transform on their own test dispatcher;
    // production keeps it off the main thread (see flowOn below).
    @ComputationDispatcher
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    // Seeded with the device zone, then corrected to the work zone as soon as
    // settings load (see uiState); near a month boundary the two can disagree
    // by a whole month, which made Reports open on a different month than the
    // dashboard summarizes.
    private val _selectedYear = MutableStateFlow(YearMonth.now(ZoneId.systemDefault()).year)
    private val _selectedMonth = MutableStateFlow(YearMonth.now(ZoneId.systemDefault()).monthValue)
    private val _refreshNonce = MutableStateFlow(0)
    private var monthNavigated = false

    private data class ReportInputs(
        val report: MonthlyReport?,
        val shifts: List<Shift>,
        val settings: UserSettings?,
        val previousShifts: List<Shift>,
        val allShifts: List<Shift>,
        val claims: List<com.elmtrackr.app.domain.model.RefundClaim>,
    )

    val selectedYearMonth: StateFlow<Pair<Int, Int>> = combine(
        _selectedYear,
        _selectedMonth,
    ) { year, month -> year to month }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _selectedYear.value to _selectedMonth.value,
        )

    val canGoNext: StateFlow<Boolean> = combine(
        _selectedYear,
        _selectedMonth,
        currentUserProvider.userId.filterNotNull().flatMapLatest { userId ->
            settingsRepository.observeSettings(userId)
        },
    ) { y, m, settings ->
        val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneOffset.UTC
        YearMonth.of(y, m) < YearMonth.now(zone)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    val uiState: StateFlow<ReportsUiState> = combine(
        _selectedYear,
        _selectedMonth,
        _refreshNonce,
    ) { year, month, _ -> year to month }
        .flatMapLatest { (year, month) ->
            currentUserProvider.userId.filterNotNull().flatMapLatest { userId ->
                val previous = YearMonth.of(year, month).minusMonths(1)
                val refundData = combine(
                    shiftsRepository.observeShifts(userId),
                    refundsRepository.observeClaimsForUser(userId),
                ) { allShifts, claims -> allShifts to claims }
                flow {
                    emit(ReportsUiState.Loading)
                    combine(
                        combine(
                            reportsRepository.observeMonthlyReport(userId, year, month),
                            settingsRepository.observeSettings(userId),
                        ) { report, settings ->
                            report to settings
                        }.flatMapLatest { (report, settings) ->
                            val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneOffset.UTC
                            if (!monthNavigated && settings != null) {
                                val current = YearMonth.now(zone)
                                if (_selectedYear.value != current.year || _selectedMonth.value != current.monthValue) {
                                    _selectedYear.value = current.year
                                    _selectedMonth.value = current.monthValue
                                }
                            }
                            combine(
                                shiftsRepository.observeShiftsByMonthInZone(userId, year, month, zone),
                                shiftsRepository.observeShiftsByMonthInZone(
                                    userId,
                                    previous.year,
                                    previous.monthValue,
                                    zone,
                                ),
                                refundData,
                            ) { shifts, previousShifts, refundPair ->
                                val (allShifts, claims) = refundPair
                                ReportInputs(report, shifts, settings, previousShifts, allShifts, claims)
                            }
                        },
                        compensationProfilesRepository.observeProfiles(userId),
                        premiumProfilesRepository.observeProfiles(userId),
                        tasksRepository.observeAllTasks(userId),
                    ) { inputs, profiles, premiumProfiles, tasks ->
                        val completedShifts = inputs.shifts.filter { it.isCompleted }
                        when {
                            inputs.settings == null -> ReportsUiState.Loading
                            else -> {
                                val settings = inputs.settings
                                val safeReport = inputs.report ?: MonthlyReportBuilder.buildMonthlyReport(
                                    year = year,
                                    month = month,
                                    shifts = inputs.shifts,
                                    settings = settings,
                                )
                                val paySummary = settings.takeIf {
                                    (it.hourlyRate ?: 0.0) > 0.0 ||
                                        profiles.any { p -> (p.baseHourlyRate ?: 0.0) > 0.0 }
                                }?.let { PayrollCalculator.sumMonthlyPay(completedShifts, it, profiles, premiumProfiles) }
                                val prevCompleted = inputs.previousShifts.filter { it.isCompleted }
                                val insights = settings.takeIf { it.featuresInsights }
                                    ?.let { ReportInsightsBuilder.build(completedShifts, it, profiles, premiumProfiles) }
                                val dailyInsights = settings.takeIf { it.featuresInsights }
                                    ?.let {
                                        DailyInsightsBuilder.build(
                                            completedShifts,
                                            it,
                                            safeReport.totalMinutes,
                                            profiles,
                                            premiumProfiles,
                                        )
                                    }
                                    ?: emptyList()
                                val taskBreakdown = TaskMonthlyReportBuilder.build(
                                    shifts = completedShifts,
                                    settings = settings,
                                    tasks = tasks.filter { !it.isArchived },
                                    profiles = profiles,
                                    premiumProfiles = premiumProfiles,
                                )
                                ReportsUiState.Ready(
                                    year = year,
                                    month = month,
                                    report = safeReport,
                                    weeklyTotals = WeeklyBreakdownBuilder.groupByWeek(
                                        shifts = completedShifts,
                                        settings = settings,
                                        profiles = profiles,
                                        premiumProfiles = premiumProfiles,
                                        prevMonthShifts = prevCompleted,
                                    ),
                                    paySummary = paySummary,
                                    rawShifts = completedShifts,
                                    settings = settings,
                                    profiles = profiles,
                                    premiumProfiles = premiumProfiles,
                                    featuresTravelRefunds = settings.featuresTravelRefunds,
                                    insights = insights,
                                    dailyInsights = dailyInsights,
                                    previousMonthMinutes = prevCompleted.sumOf {
                                        ShiftDurationCalculator.netMinutes(it) ?: 0
                                    },
                                    allShifts = inputs.allShifts,
                                    refundClaims = inputs.claims,
                                    taskBreakdown = taskBreakdown,
                                    zone = WorkTimezone.zoneFor(settings),
                                )
                            }
                        }
                    }.collect { emit(it) }
                }
            }
        }
        .catch { e -> emit(ReportsUiState.Error(e.message ?: "Unknown error")) }
        // The transform above runs the full month's payroll: sumMonthlyPay, the daily
        // insights builder (which sums pay again) and the weekly breakdown, all of which
        // walk each shift minute by minute in the IL engine. Without flowOn that ran in
        // the stateIn coroutine — Dispatchers.Main.immediate — so a heavy month janked
        // month navigation on mid-range devices.
        .flowOn(computationDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState.Loading,
        )

    fun previousMonth() {
        monthNavigated = true
        val prev = YearMonth.of(_selectedYear.value, _selectedMonth.value).minusMonths(1)
        _selectedYear.value = prev.year
        _selectedMonth.value = prev.monthValue
    }

    fun nextMonth() {
        monthNavigated = true
        val current = YearMonth.of(_selectedYear.value, _selectedMonth.value)
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            val settings = settingsRepository.getSettings(userId)
            val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneOffset.UTC
            if (current >= YearMonth.now(zone)) return@launch
            val next = current.plusMonths(1)
            _selectedYear.value = next.year
            _selectedMonth.value = next.monthValue
        }
    }

    /** Re-subscribes to report data after a flow error. */
    fun retry() {
        _refreshNonce.value++
    }

    fun buildCsvContent(
        shifts: List<Shift>,
        settings: UserSettings?,
        year: Int = selectedYearMonth.value.first,
        month: Int = selectedYearMonth.value.second,
    ): String {
        val reportSettings = settings ?: UserSettings(id = "export", userId = "export")
        val completed = shifts.filter { it.isCompleted }.sortedByDescending { it.startTime }
        val breakdowns = completed.map { MonthlyReportBuilder.buildShiftBreakdown(it, reportSettings) }
        val lines = mutableListOf(
            "Date,Start Time,End Time,Break (min),Total Hours,Regular Hours,Overtime Hours,Weekend Hours,Overnight,Notes",
        )
        val zone = WorkTimezone.zoneFor(reportSettings)
        completed.forEachIndexed { index, shift ->
            val breakdown = breakdowns[index]
            lines += listOf(
                WorkTimezone.shiftLocalDate(shift, zone).toString(),
                formatDatetime(shift.startTime, zone),
                formatDatetime(shift.endTime, zone),
                shift.breakMinutes.toString(),
                formatHoursDecimal(breakdown.totalMinutes),
                formatHoursDecimal(breakdown.regularMinutes),
                formatHoursDecimal(breakdown.overtimeMinutes),
                formatHoursDecimal(breakdown.weekendMinutes),
                if (OvernightShiftDetector.isOvernight(shift, zone)) "Yes" else "No",
                csvEscape(shift.notes ?: ""),
            ).joinToString(",")
        }
        lines += ""
        // The totals row must agree with the on-screen monthly report, which
        // applies max(daily, weekly) overtime per week. Summing the per-shift
        // rows counts daily overtime only, so a month whose overtime was
        // weekly-threshold-driven exported 0.00 while the screen showed hours.
        val report = MonthlyReportBuilder.buildMonthlyReport(year, month, completed, reportSettings)
        lines += listOf(
            "TOTAL - $year-${month.toString().padStart(2, '0')}", "", "", "",
            formatHoursDecimal(report.totalMinutes),
            formatHoursDecimal(report.regularMinutes),
            formatHoursDecimal(report.overtimeMinutes),
            formatHoursDecimal(report.weekendMinutes),
            "", "${completed.size} shifts",
        ).joinToString(",")
        return lines.joinToString("\n")
    }

    private fun formatDatetime(instant: java.time.Instant?, zone: ZoneId): String = instant
        ?.atZone(zone)
        ?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        .orEmpty()

    private fun formatHoursDecimal(minutes: Int): String = "%.2f".format(java.util.Locale.US, minutes / 60.0)

    suspend fun receiptUrl(path: String): String? = refundReceiptStorage
        ?.let { storage -> runCatching { storage.createSignedUrl(path) }.getOrNull() }

    fun csvFilename(year: Int, month: Int): String =
        "elmtrackr-$year-${month.toString().padStart(2, '0')}.csv"

    /**
     * Quotes a CSV field and neutralises spreadsheet formula injection.
     *
     * Shift notes reach this file from the user, and via sync from any other device on the
     * account. A note beginning `=`, `+`, `-`, `@`, tab or CR is executed as a formula when the
     * export is opened in Excel or Sheets — the payroll recipient, not the author, runs it. The
     * OWASP mitigation is to prefix such values with an apostrophe so the cell stays text.
     * Everything is quoted unconditionally so the leading apostrophe cannot itself be read as
     * data, and `\r` joins the escape triggers (a bare CR otherwise breaks row alignment).
     */
    internal fun csvEscape(value: String): String {
        val neutralised = if (value.firstOrNull() in FORMULA_TRIGGERS) "'$value" else value
        return "\"${neutralised.replace("\"", "\"\"")}\""
    }

    private companion object {
        private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')
    }
}
