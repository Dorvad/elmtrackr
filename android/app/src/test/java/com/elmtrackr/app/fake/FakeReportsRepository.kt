package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.WeeklyTotals
import com.elmtrackr.app.domain.repository.ReportsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReportsRepository : ReportsRepository {

    private val _report = MutableStateFlow<MonthlyReport?>(null)
    private val _weekly = MutableStateFlow<List<WeeklyTotals>>(emptyList())

    fun setReport(report: MonthlyReport?) { _report.value = report }
    fun setWeekly(weekly: List<WeeklyTotals>) { _weekly.value = weekly }

    override fun observeMonthlyReport(userId: String, year: Int, month: Int): Flow<MonthlyReport?> = _report

    override fun observeWeeklyTotals(userId: String): Flow<List<WeeklyTotals>> = _weekly
}
