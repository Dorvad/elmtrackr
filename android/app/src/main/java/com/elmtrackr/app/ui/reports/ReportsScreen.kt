package com.elmtrackr.app.ui.reports

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.WeeklyTotals
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsState()
    val canGoNext by viewModel.canGoNext.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            MonthNavigator(
                year = selectedYearMonth.first,
                month = selectedYearMonth.second,
                onPrev = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                canGoNext = canGoNext,
            )
            Spacer(Modifier.height(16.dp))

            when (val state = uiState) {
                is ReportsUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is ReportsUiState.Empty -> ReportsEmptyContent()

                is ReportsUiState.Ready -> ReportsReadyContent(
                    state = state,
                    onExport = {
                        val csv = viewModel.buildCsvContent(state.rawShifts, state.settings)
                        val filename = viewModel.csvFilename(state.year, state.month)
                        shareTextAsCsv(context, csv, filename)
                    },
                )

                is ReportsUiState.Error -> Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

// ── Month navigator ──────────────────────────────────────────────────────────

@Composable
private fun MonthNavigator(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
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
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun ReportsEmptyContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        Text(
            text = "Complete a shift to see your summary.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

// ── Ready content ────────────────────────────────────────────────────────────

@Composable
private fun ReportsReadyContent(
    state: ReportsUiState.Ready,
    onExport: () -> Unit,
) {
    // Monthly summary
    SectionHeader("This Month")
    ReportRow("Total hours", ShiftDurationCalculator.formatMinutes(state.report.totalMinutes))
    ReportRow("Regular hours", ShiftDurationCalculator.formatMinutes(state.report.regularMinutes))
    ReportRow("Overtime", ShiftDurationCalculator.formatMinutes(state.report.overtimeMinutes))
    ReportRow("Weekend / Special", ShiftDurationCalculator.formatMinutes(state.report.weekendMinutes))
    ReportRow("Completed shifts", state.report.shiftCount.toString())

    // Payroll section
    SectionDivider()
    SectionHeader("Payroll Estimate")
    if (state.paySummary != null) {
        ReportRow("Total gross (before tax)", formatPay(state.paySummary.totalGross))
        ReportRow("Regular pay", formatPay(state.paySummary.regularGross))
        if (state.paySummary.overtimeGross > 0) {
            ReportRow("Overtime pay", formatPay(state.paySummary.overtimeGross))
        }
        if (state.paySummary.specialGross > 0) {
            ReportRow("Special / holiday pay", formatPay(state.paySummary.specialGross))
        }
    } else {
        Text(
            text = "Set an hourly rate in Settings to see pay estimates.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }

    // Weekly breakdown
    SectionDivider()
    SectionHeader("Weekly Breakdown")
    if (state.weeklyTotals.isEmpty()) {
        Text(
            text = "No weekly data available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    } else {
        state.weeklyTotals.forEach { week -> WeekRow(week) }
    }

    // Travel refunds section
    if (state.featuresTravelRefunds) {
        SectionDivider()
        RefundsSection(state.rawShifts)
    }

    // Export CSV
    SectionDivider()
    Spacer(Modifier.height(4.dp))
    OutlinedButton(
        onClick = onExport,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Share, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Export CSV")
    }
    Spacer(Modifier.height(16.dp))
}

// ── Section helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
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

// ── Weekly row ───────────────────────────────────────────────────────────────

@Composable
private fun WeekRow(week: WeeklyTotals) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Week of ${formatWeekStart(week.weekStart)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = ShiftDurationCalculator.formatMinutes(week.totalMinutes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${week.shifts.size} shift${if (week.shifts.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

// ── Travel refunds section ───────────────────────────────────────────────────

@Composable
private fun RefundsSection(shifts: List<Shift>) {
    SectionHeader("Travel Refunds")
    val pending = shifts.filter {
        it.refundAction == null || it.refundAction == RefundAction.REMIND_LATER
    }
    if (pending.isEmpty()) {
        Text(
            text = "No unresolved refunds this month.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    } else {
        Text(
            text = "${pending.size} shift${if (pending.size == 1) "" else "s"} with unresolved refunds:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        pending.forEach { shift ->
            val dateFmt = DateTimeFormatter.ofPattern("MMM d")
            val date = shift.startTime.atOffset(ZoneOffset.UTC).toLocalDate()
                .format(dateFmt)
            val status = when (shift.refundAction) {
                RefundAction.REMIND_LATER -> "Remind later"
                null -> "Not reviewed"
                else -> shift.refundAction.name.lowercase().replace('_', ' ')
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = date, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        Text(
            text = "Open the Shifts tab to update refund status.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ── Utilities ────────────────────────────────────────────────────────────────

private fun formatWeekStart(weekStart: String): String {
    val date = LocalDate.parse(weekStart)
    return date.format(DateTimeFormatter.ofPattern("MMM d"))
}

private fun formatPay(amount: Double): String = "%.2f".format(amount)

private fun shareTextAsCsv(context: Context, text: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Export CSV"))
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ReportsScreenPreview() {
    ElmTrackrTheme { ReportsEmptyContent() }
}
