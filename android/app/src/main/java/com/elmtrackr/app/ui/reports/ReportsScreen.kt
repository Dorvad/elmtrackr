package com.elmtrackr.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.ShiftStats.MonthlyReport
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import java.time.Month

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            when (val state = uiState) {
                is ReportsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ReportsUiState.Empty -> ReportsEmpty(
                    onPrev = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
                is ReportsUiState.Ready -> ReportsContent(
                    state = state,
                    onPrev = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
                is ReportsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = "${Month.of(month).name.lowercase().replaceFirstChar { it.uppercase() }} $year",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun ReportsEmpty(onPrev: () -> Unit, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MonthNavigator(0, 1, onPrev, onNext)
        Spacer(Modifier.height(32.dp))
        Icon(
            imageVector = Icons.Filled.Analytics,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No shifts this month",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ReportsContent(
    state: ReportsUiState.Ready,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val report: MonthlyReport = state.report

    Column {
        MonthNavigator(state.year, state.month, onPrev, onNext)
        Spacer(Modifier.height(24.dp))

        ReportRow("Total hours", ShiftDurationCalculator.formatMinutes(report.totalMinutes))
        ReportRow("Regular hours", ShiftDurationCalculator.formatMinutes(report.regularMinutes))
        ReportRow("Overtime", ShiftDurationCalculator.formatMinutes(report.overtimeMinutes))
        ReportRow("Weekend", ShiftDurationCalculator.formatMinutes(report.weekendMinutes))
        ReportRow("Shifts", report.shiftCount.toString())
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportsScreenPreview() {
    ElmTrackrTheme { ReportsEmpty({}, {}) }
}
