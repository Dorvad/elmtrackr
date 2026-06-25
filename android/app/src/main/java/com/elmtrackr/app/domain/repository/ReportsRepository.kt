package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.WeeklyTotals
import kotlinx.coroutines.flow.Flow

interface ReportsRepository {

    fun observeMonthlyReport(userId: String, year: Int, month: Int): Flow<MonthlyReport?>

    fun observeWeeklyTotals(userId: String): Flow<List<WeeklyTotals>>
}
