package com.elmtrackr.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.WeeklyBreakdownBuilder
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.ReportsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ReportsViewModel(
    private val reportsRepository: ReportsRepository,
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val today = LocalDate.now(ZoneOffset.UTC)
    private val _selectedYear = MutableStateFlow(today.year)
    private val _selectedMonth = MutableStateFlow(today.monthValue)

    val selectedYearMonth: StateFlow<Pair<Int, Int>> = combine(
        _selectedYear,
        _selectedMonth,
    ) { year, month -> year to month }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = today.year to today.monthValue,
        )

    val canGoNext: StateFlow<Boolean> = combine(_selectedYear, _selectedMonth) { y, m ->
        YearMonth.of(y, m) < YearMonth.now(ZoneOffset.UTC)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val uiState: StateFlow<ReportsUiState> = combine(
        _selectedYear,
        _selectedMonth,
    ) { year, month -> year to month }
        .flatMapLatest { (year, month) ->
            combine(
                reportsRepository.observeMonthlyReport(LOCAL_USER_ID, year, month),
                shiftsRepository.observeShiftsByMonth(LOCAL_USER_ID, year, month),
                settingsRepository.observeSettings(LOCAL_USER_ID),
            ) { report, shifts, settings ->
                val completedShifts = shifts.filter { it.isCompleted }
                when {
                    report == null -> ReportsUiState.Loading
                    report.shiftCount == 0 -> ReportsUiState.Empty
                    else -> {
                        val paySummary = settings?.hourlyRate?.takeIf { it > 0 }?.let {
                            PayrollCalculator.sumMonthlyPay(completedShifts, settings)
                        }
                        ReportsUiState.Ready(
                            year = year,
                            month = month,
                            report = report,
                            weeklyTotals = WeeklyBreakdownBuilder.groupByWeek(completedShifts),
                            paySummary = paySummary,
                            rawShifts = completedShifts,
                            settings = settings,
                            featuresTravelRefunds = settings?.featuresTravelRefunds ?: false,
                        )
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
        if (current >= YearMonth.now(ZoneOffset.UTC)) return
        val next = current.plusMonths(1)
        _selectedYear.value = next.year
        _selectedMonth.value = next.monthValue
    }

    fun buildCsvContent(shifts: List<Shift>, settings: UserSettings?): String {
        val hasRate = settings?.hourlyRate?.let { it > 0 } == true
        val sb = StringBuilder()
        sb.appendLine(
            if (hasRate) "Date,Start Time,End Time,Gross Min,Break Min,Net Min,Special Day,Notes,Gross Pay"
            else "Date,Start Time,End Time,Gross Min,Break Min,Net Min,Special Day,Notes"
        )
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        for (shift in shifts.sortedBy { it.startTime }) {
            val date = shift.startTime.atOffset(ZoneOffset.UTC).toLocalDate()
            val start = shift.startTime.atOffset(ZoneOffset.UTC).format(timeFmt)
            val end = shift.endTime?.atOffset(ZoneOffset.UTC)?.format(timeFmt) ?: ""
            val gross = ShiftDurationCalculator.grossMinutes(shift)?.toString() ?: ""
            val net = ShiftDurationCalculator.netMinutes(shift)?.toString() ?: ""
            val notes = csvEscape(shift.notes ?: "")
            val row = if (hasRate && settings != null) {
                val pay = PayrollCalculator.calculateShiftPay(shift, settings)
                    ?.totalGross?.let { "%.2f".format(it) } ?: ""
                "$date,$start,$end,$gross,${shift.breakMinutes},$net,${shift.isSpecialDay},$notes,$pay"
            } else {
                "$date,$start,$end,$gross,${shift.breakMinutes},$net,${shift.isSpecialDay},$notes"
            }
            sb.appendLine(row)
        }
        return sb.toString()
    }

    fun csvFilename(year: Int, month: Int): String =
        "elmtrackr-$year-${month.toString().padStart(2, '0')}.csv"

    internal fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                ReportsViewModel(app.reportsRepository, app.shiftsRepository, app.settingsRepository)
            }
        }
    }
}
