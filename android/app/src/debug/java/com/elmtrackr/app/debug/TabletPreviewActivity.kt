package com.elmtrackr.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ReportInsightsBuilder
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.navigation.BottomNavItem
import com.elmtrackr.app.ui.dashboard.DashboardReadyPreview
import com.elmtrackr.app.ui.dashboard.DashboardUiState
import com.elmtrackr.app.ui.layout.DeviceFormFactor
import com.elmtrackr.app.ui.layout.ProvideDeviceFormFactor
import com.elmtrackr.app.ui.navigation.ElmSideNavigation
import com.elmtrackr.app.ui.reports.HoursReport
import com.elmtrackr.app.ui.reports.ReportsUiState
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import java.time.Instant

/**
 * Debug-only activity for capturing tablet layout screenshots on emulators/devices.
 * Launch: adb shell am start -n com.elmtrackr.app/.debug.TabletPreviewActivity
 */
class TabletPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_DASHBOARD
        setContent {
            ProvideDeviceFormFactor(DeviceFormFactor.Tablet) {
                ElmTrackrTheme {
                    TabletPreviewShell(screen = screen)
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_DASHBOARD = "dashboard"
        const val SCREEN_REPORTS = "reports"
    }
}

@Composable
private fun TabletPreviewShell(screen: String) {
    var selectedRoute = when (screen) {
        TabletPreviewActivity.SCREEN_REPORTS -> BottomNavItem.REPORTS.route
        else -> BottomNavItem.DASHBOARD.route
    }
    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ElmSideNavigation(
            currentRoute = selectedRoute,
            onNavigate = { selectedRoute = it },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (selectedRoute) {
                BottomNavItem.REPORTS.route -> TabletReportsPreview()
                else -> DashboardReadyPreview(state = sampleDashboardState())
            }
        }
    }
}

@Composable
private fun TabletReportsPreview() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "Reports",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Your performance overview",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        HoursReport(
            state = sampleReportsState(),
            onExportCsv = {},
            onExportPdf = {},
        )
    }
}

private fun sampleDashboardState(): DashboardUiState.Ready {
    val settings = UserSettings(
        id = "settings",
        userId = "user",
        hourlyRate = 50.0,
        currency = CurrencyCode.ILS,
    )
    val shifts = sampleShifts()
    return DashboardUiState.Ready(
        activeShift = null,
        monthlyReport = MonthlyReport(2025, 4, 5604, 2400, 3204, 1896, 12, emptyList()),
        settings = settings,
        recentShifts = shifts,
        displayName = "Dor",
        paySummary = PayrollCalculator.sumMonthlyPay(shifts, settings),
    )
}

private fun sampleReportsState(): ReportsUiState.Ready {
    val settings = UserSettings(
        id = "settings",
        userId = "user",
        hourlyRate = 50.0,
        featuresInsights = true,
    )
    val shifts = sampleShifts()
    return ReportsUiState.Ready(
        year = 2025,
        month = 4,
        report = MonthlyReport(2025, 4, 5604, 2400, 3204, 1896, 12, emptyList()),
        weeklyTotals = emptyList(),
        paySummary = PayrollCalculator.sumMonthlyPay(shifts, settings),
        rawShifts = shifts,
        settings = settings,
        previousMonthMinutes = 4620,
        insights = ReportInsightsBuilder.build(shifts, settings),
    )
}

private fun sampleShifts() = listOf(
    Shift("one", "user", Instant.parse("2025-04-08T06:00:00Z"), Instant.parse("2025-04-08T15:30:00Z"), breakMinutes = 30),
    Shift("two", "user", Instant.parse("2025-04-12T20:00:00Z"), Instant.parse("2025-04-13T04:00:00Z"), isSpecialDay = true),
    Shift("three", "user", Instant.parse("2025-04-19T07:00:00Z"), Instant.parse("2025-04-19T16:00:00Z"), breakMinutes = 30),
)
