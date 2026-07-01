package com.elmtrackr.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elmtrackr.app.domain.PayrollCalculator
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TabletScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureTabletDashboard() = captureTablet("tablet-dashboard") {
        DashboardReadyPreview(state = sampleDashboardState())
    }

    @Test
    fun captureTabletReports() = captureTablet("tablet-reports", selectedRoute = BottomNavItem.REPORTS.route) {
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
                onPreviousMonth = {},
                onNextMonth = {},
                canGoNext = true,
                onExportCsv = {},
                onExportPdf = {},
            )
        }
    }

    private fun captureTablet(
        name: String,
        selectedRoute: String = BottomNavItem.DASHBOARD.route,
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ProvideDeviceFormFactor(DeviceFormFactor.Tablet) {
                CompositionLocalProvider(LocalDensity provides Density(1.5f, 1f)) {
                    ElmTrackrTheme {
                        Row(
                            Modifier
                                .size(1280.dp, 800.dp)
                                .background(MaterialTheme.colorScheme.background)
                                .testTag("golden"),
                        ) {
                            ElmSideNavigation(
                                currentRoute = selectedRoute,
                                onNavigate = {},
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val actual = compose.onNodeWithTag("golden").captureToImage().asAndroidBitmap()
        val outputDir = File("/sdcard/Download/elmtrackr-screenshots").also { it.mkdirs() }
        val output = File(outputDir, "$name.png")
        FileOutputStream(output).use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val local = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots/$name.png")
        local.parentFile?.mkdirs()
        FileOutputStream(local).use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
            insights = com.elmtrackr.app.domain.ReportInsightsBuilder.build(shifts, settings),
        )
    }

    private fun sampleShifts() = listOf(
        Shift("one", "user", Instant.parse("2025-04-08T06:00:00Z"), Instant.parse("2025-04-08T15:30:00Z"), breakMinutes = 30),
        Shift("two", "user", Instant.parse("2025-04-12T20:00:00Z"), Instant.parse("2025-04-13T04:00:00Z"), isSpecialDay = true),
        Shift("three", "user", Instant.parse("2025-04-19T07:00:00Z"), Instant.parse("2025-04-19T16:00:00Z"), breakMinutes = 30),
    )
}
