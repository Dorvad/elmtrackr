package com.elmtrackr.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.DailyInsightsBuilder
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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
    private val refundReceiptStorage: RefundReceiptStorage?,
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(YearMonth.now(ZoneId.systemDefault()).year)
    private val _selectedMonth = MutableStateFlow(YearMonth.now(ZoneId.systemDefault()).monthValue)
    private val _refreshNonce = MutableStateFlow(0)

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
                combine(
                    combine(
                        reportsRepository.observeMonthlyReport(userId, year, month),
                        settingsRepository.observeSettings(userId),
                    ) { report, settings ->
                        report to settings
                    }.flatMapLatest { (report, settings) ->
                        val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneOffset.UTC
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
                    tasksRepository.observeAllTasks(userId),
                ) { inputs, profiles, tasks ->
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
                            }?.let { PayrollCalculator.sumMonthlyPay(completedShifts, it, profiles) }
                            val prevCompleted = inputs.previousShifts.filter { it.isCompleted }
                            val insights = settings.takeIf { it.featuresInsights }
                                ?.let { ReportInsightsBuilder.build(completedShifts, it, profiles) }
                            val dailyInsights = settings.takeIf { it.featuresInsights }
                                ?.let { DailyInsightsBuilder.build(completedShifts, it, safeReport.totalMinutes, profiles) }
                                ?: emptyList()
                            val taskBreakdown = TaskMonthlyReportBuilder.build(
                                shifts = completedShifts,
                                settings = settings,
                                tasks = tasks.filter { !it.isArchived },
                                profiles = profiles,
                            )
                            ReportsUiState.Ready(
                                year = year,
                                month = month,
                                report = safeReport,
                                weeklyTotals = WeeklyBreakdownBuilder.groupByWeek(
                                    shifts = completedShifts,
                                    settings = settings,
                                    profiles = profiles,
                                    prevMonthShifts = prevCompleted,
                                ),
                                paySummary = paySummary,
                                rawShifts = completedShifts,
                                settings = settings,
                                profiles = profiles,
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
                }
            }
        }
        .catch { e -> emit(ReportsUiState.Error(e.message ?: "Unknown error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState.Loading,
        )

    fun previousMonth() {
        val prev = YearMonth.of(_selectedYear.value, _selectedMonth.value).minusMonths(1)
        _selectedYear.value = prev.year
        _selectedMonth.value = prev.monthValue
    }

    fun nextMonth() {
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
        lines += listOf(
            "TOTAL - $year-${month.toString().padStart(2, '0')}", "", "", "",
            formatHoursDecimal(breakdowns.sumOf { it.totalMinutes }),
            formatHoursDecimal(breakdowns.sumOf { it.regularMinutes }),
            formatHoursDecimal(breakdowns.sumOf { it.overtimeMinutes }),
            formatHoursDecimal(breakdowns.sumOf { it.weekendMinutes }),
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

    internal fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
