package com.elmtrackr.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.repository.ReportsRepository
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

class ReportsViewModel(
    private val reportsRepository: ReportsRepository,
) : ViewModel() {

    private val today = LocalDate.now(ZoneOffset.UTC)
    private val _selectedYear = MutableStateFlow(today.year)
    private val _selectedMonth = MutableStateFlow(today.monthValue)

    val uiState: StateFlow<ReportsUiState> = combine(
        _selectedYear,
        _selectedMonth,
    ) { year, month -> year to month }
        .flatMapLatest { (year, month) ->
            combine(
                reportsRepository.observeMonthlyReport(LOCAL_USER_ID, year, month),
                reportsRepository.observeWeeklyTotals(LOCAL_USER_ID),
            ) { report, weekly ->
                when {
                    report == null -> ReportsUiState.Loading
                    report.shiftCount == 0 -> ReportsUiState.Empty
                    else -> ReportsUiState.Ready(year, month, report, weekly)
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
        val next = YearMonth.of(_selectedYear.value, _selectedMonth.value).plusMonths(1)
        _selectedYear.value = next.year
        _selectedMonth.value = next.monthValue
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                ReportsViewModel(app.reportsRepository)
            }
        }
    }
}
