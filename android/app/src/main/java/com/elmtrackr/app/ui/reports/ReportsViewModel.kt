package com.elmtrackr.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.di.ComputationDispatcher
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.DailyInsightsBuilder
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.repository.PremiumProfilesRepository
import com.elmtrackr.app.domain.HoursFormatter
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.OvernightShiftDetector
import com.elmtrackr.app.domain.WeeklyBreakdownBuilder
import com.elmtrackr.app.domain.ReportInsightsBuilder
import com.elmtrackr.app.domain.TaskMonthlyReportBuilder
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.domain.model.CompensationProfile
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
import kotlinx.coroutines.flow.flowOf
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
    private val projectsRepository: com.elmtrackr.app.domain.repository.ProjectsRepository,
    private val reviewPromptRecorder: com.elmtrackr.app.review.ReviewPromptRecorder,
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

    /**
     * The project report's own filters. Held apart from the month selector so
     * narrowing to one client does not disturb the hourly report beside it.
     */
    private val _projectFilter =
        MutableStateFlow(com.elmtrackr.app.domain.projects.ProjectReportFilter())

    fun onProjectFilterChange(filter: com.elmtrackr.app.domain.projects.ProjectReportFilter) {
        _projectFilter.value = filter
    }

    /**
     * The project report for the selected month, or null while Paid Projects is
     * off — which is what leaves the hourly report untouched.
     *
     * Project money is never merged into [ReportsUiState.Ready.paySummary]: hourly
     * earnings are work performed in the month while project payments are cash
     * received, and the two are reported as separate lines.
     */
    private val projectReport: kotlinx.coroutines.flow.Flow<ProjectReportBundle?> =
        combine(_selectedYear, _selectedMonth, _projectFilter) { year, month, filter ->
            Triple(year, month, filter)
        }.flatMapLatest { (year, month, filter) ->
            currentUserProvider.userId.flatMapLatest { userId ->
                if (userId == null) return@flatMapLatest flowOf(null)
                settingsRepository.observeSettings(userId).flatMapLatest { settings ->
                    if (settings?.featuresPaidProjects != true) {
                        flowOf(null)
                    } else {
                        val zone = WorkTimezone.zoneFor(settings)
                        combine(
                            projectsRepository.observeProjects(userId),
                            projectsRepository.observeAllBillingRecords(userId),
                            projectsRepository.observeAllPayments(userId),
                            shiftsRepository.observeShiftsByMonthInZone(userId, year, month, zone),
                        ) { projects, records, payments, monthShifts ->
                            buildProjectBundle(
                                projects = projects,
                                records = records,
                                payments = payments,
                                monthShifts = monthShifts,
                                filter = filter.copy(
                                    from = filter.from ?: LocalDate.of(year, month, 1),
                                    to = filter.to
                                        ?: LocalDate.of(year, month, 1).plusMonths(1).minusDays(1),
                                ),
                                zone = zone,
                            )
                        }
                    }
                }
            }
        }.catch { emit(null) }

    private data class ProjectReportBundle(
        val report: com.elmtrackr.app.domain.projects.ProjectReport,
        val filter: com.elmtrackr.app.domain.projects.ProjectReportFilter,
        val clients: List<String>,
        val currencies: List<String>,
        val projects: List<com.elmtrackr.app.domain.model.Project>,
    )

    private fun buildProjectBundle(
        projects: List<com.elmtrackr.app.domain.model.Project>,
        records: List<com.elmtrackr.app.domain.model.ProjectBillingRecord>,
        payments: List<com.elmtrackr.app.domain.model.ProjectPayment>,
        monthShifts: List<Shift>,
        filter: com.elmtrackr.app.domain.projects.ProjectReportFilter,
        zone: ZoneId,
    ): ProjectReportBundle {
        val today = LocalDate.now(zone)
        // Project time only: an employee-paid shift is not project hours.
        val projectShifts = monthShifts.filter { it.isProjectTime && it.isCompleted }
        val minutesByProject = com.elmtrackr.app.domain.projects.ProjectMetrics
            .timeSummaries(projectShifts)
            .mapValues { it.value.trackedMinutes }
        val activeDays = projectShifts
            .map { WorkTimezone.shiftLocalDate(it, zone) }
            .distinct()
            .size

        val summaries = projects.map { project ->
            com.elmtrackr.app.domain.projects.ProjectMetrics.summarize(
                project = project,
                shifts = emptyList(),
                billingRecords = records,
                payments = payments,
                today = today,
                timeSummary = com.elmtrackr.app.domain.projects.ProjectTimeSummary(
                    trackedMinutes = minutesByProject[project.id] ?: 0,
                    shiftCount = projectShifts.count { it.projectId == project.id },
                ),
            )
        }

        return ProjectReportBundle(
            report = com.elmtrackr.app.domain.projects.ProjectReportBuilder.build(
                summaries = summaries,
                billingRecords = records,
                payments = payments,
                filter = filter,
                projectMinutes = minutesByProject,
                activeDays = activeDays,
                today = today,
            ),
            filter = filter,
            clients = com.elmtrackr.app.domain.projects.ProjectReportFilterEngine
                .availableClients(summaries),
            currencies = com.elmtrackr.app.domain.projects.ProjectReportFilterEngine
                .availableCurrencies(summaries),
            projects = com.elmtrackr.app.domain.projects.ProjectReportFilterEngine
                .availableProjects(summaries),
        )
    }

    /** The project CSV for the current report. Hourly CSV export is untouched. */
    fun buildProjectCsv(report: com.elmtrackr.app.domain.projects.ProjectReport): String =
        com.elmtrackr.app.domain.projects.ProjectReportCsv.build(report)

    fun projectCsvFilename(report: com.elmtrackr.app.domain.projects.ProjectReport): String =
        com.elmtrackr.app.domain.projects.ProjectReportCsv.filename(report)

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
                                val prevCompleted = inputs.previousShifts.filter { it.isCompleted }
                                // The previous month is already loaded here for the
                                // week-over-week delta, so pay-week context across the
                                // 1st costs nothing extra. Only the month is reported;
                                // these fill the weekly overtime allowance.
                                val payContext = completedShifts + prevCompleted
                                val safeReport = inputs.report ?: MonthlyReportBuilder.buildMonthlyReport(
                                    year = year,
                                    month = month,
                                    shifts = inputs.shifts,
                                    settings = settings,
                                    profiles = profiles,
                                    contextShifts = payContext,
                                )
                                val paySummary = settings.takeIf {
                                    (it.hourlyRate ?: 0.0) > 0.0 ||
                                        profiles.any { p -> (p.baseHourlyRate ?: 0.0) > 0.0 }
                                }?.let {
                                    PayrollCalculator.sumMonthlyPay(
                                        completedShifts, it, profiles, premiumProfiles,
                                        contextShifts = payContext,
                                    )
                                }
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
        .catch { e ->
            // A broken report screen is exactly the wrong moment to ask for a
            // review; open the quiet period before surfacing the error.
            reviewPromptRecorder.noteDiscouragingEvent()
            emit(ReportsUiState.Error(e.message ?: "Unknown error"))
        }
        // The transform above runs the full month's payroll: sumMonthlyPay, the daily
        // insights builder (which sums pay again) and the weekly breakdown, all of which
        // walk each shift minute by minute in the IL engine. Without flowOn that ran in
        // the stateIn coroutine — Dispatchers.Main.immediate — so a heavy month janked
        // month navigation on mid-range devices.
        .flowOn(computationDispatcher)
        // Combined last so the project report cannot delay the hourly one: an
        // hourly-only user gets null here and the state is exactly as before.
        .combine(projectReport) { state, bundle ->
            when (state) {
                is ReportsUiState.Ready -> if (bundle == null) {
                    state
                } else {
                    state.copy(
                        projectReport = bundle.report,
                        projectFilter = bundle.filter,
                        projectClients = bundle.clients,
                        projectCurrencies = bundle.currencies,
                        availableProjects = bundle.projects,
                    )
                }
                else -> state
            }
        }
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

    /**
     * Feeds the review-prompt milestone "viewed a completed monthly report".
     * Only a fully past month with real shifts counts — the current month is
     * still accruing and an empty month is not a report the user got value from.
     */
    fun onMonthlyReportViewed(year: Int, month: Int, completedShiftCount: Int, zone: ZoneId) {
        if (completedShiftCount <= 0) return
        if (YearMonth.of(year, month) >= YearMonth.now(zone)) return
        reviewPromptRecorder.noteMonthlyReportViewed()
    }

    /** Feeds the review-prompt milestone "exported a report successfully". */
    fun onReportExported() {
        reviewPromptRecorder.noteReportExported()
    }

    fun buildCsvContent(
        shifts: List<Shift>,
        settings: UserSettings?,
        // Passed in rather than read from a field: the CSV's hour columns have to
        // resolve overtime thresholds through the same profiles the on-screen report
        // used, or the export disagrees with the screen it was exported from.
        profiles: List<CompensationProfile> = emptyList(),
        year: Int = selectedYearMonth.value.first,
        month: Int = selectedYearMonth.value.second,
    ): String {
        val reportSettings = settings ?: UserSettings(id = "export", userId = "export")
        val completed = shifts.filter { it.isCompleted }.sortedByDescending { it.startTime }
        val breakdowns = completed.map { MonthlyReportBuilder.buildShiftBreakdown(it, reportSettings, profiles) }
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
                HoursFormatter.csv(breakdown.totalMinutes),
                HoursFormatter.csv(breakdown.regularMinutes),
                HoursFormatter.csv(breakdown.overtimeMinutes),
                HoursFormatter.csv(breakdown.weekendMinutes),
                if (OvernightShiftDetector.isOvernight(shift, zone)) "Yes" else "No",
                csvEscape(shift.notes ?: ""),
            ).joinToString(",")
        }
        lines += ""
        // The totals row must agree with the on-screen monthly report, which
        // applies max(daily, weekly) overtime per week. Summing the per-shift
        // rows counts daily overtime only, so a month whose overtime was
        // weekly-threshold-driven exported 0.00 while the screen showed hours.
        val report = MonthlyReportBuilder.buildMonthlyReport(year, month, completed, reportSettings, profiles)
        lines += listOf(
            "TOTAL - $year-${month.toString().padStart(2, '0')}", "", "", "",
            HoursFormatter.csv(report.totalMinutes),
            HoursFormatter.csv(report.regularMinutes),
            HoursFormatter.csv(report.overtimeMinutes),
            HoursFormatter.csv(report.weekendMinutes),
            "", "${completed.size} shifts",
        ).joinToString(",")
        return lines.joinToString("\n")
    }

    private fun formatDatetime(instant: java.time.Instant?, zone: ZoneId): String = instant
        ?.atZone(zone)
        ?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        .orEmpty()


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
