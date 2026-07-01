package com.elmtrackr.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.auth.SignedOutContent
import com.elmtrackr.app.ui.onboarding.FeaturesStep
import com.elmtrackr.app.ui.onboarding.PaySetupStep
import com.elmtrackr.app.ui.onboarding.OnboardingProgress
import com.elmtrackr.app.ui.onboarding.ProfileStep
import com.elmtrackr.app.ui.onboarding.ReviewStep
import com.elmtrackr.app.ui.onboarding.WorkWeekStep
import com.elmtrackr.app.ui.onboarding.WelcomeStep
import com.elmtrackr.app.ui.dashboard.DashboardSkeleton
import com.elmtrackr.app.ui.reports.HoursReport
import com.elmtrackr.app.ui.reports.ReportsUiState
import com.elmtrackr.app.ui.shifts.MonthShiftSummary
import com.elmtrackr.app.ui.shifts.RideProviderSelector
import com.elmtrackr.app.ui.shifts.ShiftRow
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ScreenshotRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun authSignIn() = verify("auth-sign-in") {
        SignedOutContent(false, null, { _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test
    fun onboardingWelcome() = verify("onboarding-welcome") {
        OnboardingTestColumn(1) { WelcomeStep(replay = false, onNext = {}) }
    }

    @Test
    fun onboardingFeatures() = verify("onboarding-features") {
        OnboardingTestColumn(6) { FeaturesStep(false, true, {}, {}, {}, {}) }
    }

    @Test
    fun onboardingProfile() = verify("onboarding-profile") {
        OnboardingTestColumn(2) {
            ProfileStep("Dor", "dor@example.com", {}, false, {}, {})
        }
    }

    @Test
    fun onboardingPay() = verify("onboarding-pay") {
        OnboardingTestColumn(3) {
            PaySetupStep("50", CurrencyCode.ILS, {}, {}, true, {}, {})
        }
    }

    @Test
    fun onboardingWorkWeek() = verify("onboarding-work-week") {
        OnboardingTestColumn(4) {
            WorkWeekStep(listOf(5, 6), "8", "40", "Asia/Jerusalem", {}, {}, {}, {}, true, {}, {})
        }
    }

    @Test
    fun onboardingReview() = verify("onboarding-review") {
        OnboardingTestColumn(7) {
            ReviewStep("Dor", 50.0, CurrencyCode.ILS, "Israel", listOf(5, 6), 2, null, {}, {})
        }
    }

    @Test
    fun dashboardSkeleton() = verify("dashboard-skeleton") {
        DashboardSkeleton(Modifier.fillMaxSize().padding(16.dp))
    }

    @Test
    fun reportsInsightsDisabled() = verify("reports-insights-disabled") {
        val settings = sampleSettings().copy(featuresInsights = false)
        val shifts = sampleShifts()
        Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
            HoursReport(
                ReportsUiState.Ready(
                    year = 2024,
                    month = 1,
                    report = MonthlyReport(2024, 1, 990, 870, 120, 0, 2, emptyList()),
                    weeklyTotals = emptyList(),
                    paySummary = PayrollCalculator.sumMonthlyPay(shifts, settings),
                    rawShifts = shifts,
                    settings = settings,
                    previousMonthMinutes = 840,
                ),
                onPreviousMonth = {},
                onNextMonth = {},
                canGoNext = true,
                onExportCsv = {},
                onExportPdf = {},
            )
        }
    }

    @Test
    fun reportsHours() = verify("reports-hours") {
        val settings = sampleSettings()
        val shifts = sampleShifts()
        Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
            HoursReport(
                ReportsUiState.Ready(
                    year = 2024,
                    month = 1,
                    report = MonthlyReport(2024, 1, 990, 870, 120, 0, 2, emptyList()),
                    weeklyTotals = emptyList(),
                    paySummary = PayrollCalculator.sumMonthlyPay(shifts, settings),
                    rawShifts = shifts,
                    settings = settings,
                    previousMonthMinutes = 840,
                ),
                onPreviousMonth = {},
                onNextMonth = {},
                canGoNext = true,
                onExportCsv = {},
                onExportPdf = {},
            )
        }
    }

    @Test
    fun shiftsList() = verify("shifts-list") {
        val settings = sampleSettings()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            MonthShiftSummary(sampleShifts(), settings)
            ShiftRow(sampleShifts()[0], settings, showRefunds = true, onClick = {})
            ShiftRow(sampleShifts()[1], settings, showRefunds = true, onClick = {})
        }
    }

    @Test
    fun shiftsListDark() = verify("shifts-list-dark", darkTheme = true) {
        val settings = sampleSettings()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            MonthShiftSummary(sampleShifts(), settings)
            ShiftRow(sampleShifts()[0], settings, showRefunds = true, onClick = {})
            ShiftRow(sampleShifts()[1], settings, showRefunds = true, onClick = {})
        }
    }

    @Test
    fun rideProviderGraphics() = verify("ride-provider-graphics") {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            RideProviderSelector(RefundProvider.LIME, {})
        }
    }

    private fun verify(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                ElmTrackrTheme(darkTheme = darkTheme) {
                    Box(
                        Modifier
                            .size(360.dp, 640.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .testTag("golden"),
                    ) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val actual = compose.onNodeWithTag("golden").captureToImage().asAndroidBitmap()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val record = InstrumentationRegistry.getArguments().getString("recordScreenshots") == "true"
        if (record) {
            val output = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots/$name.png")
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        val expected = instrumentation.context.assets.open("goldens/$name.png").use(BitmapFactory::decodeStream)
        assertEquals("width", expected.width, actual.width)
        assertEquals("height", expected.height, actual.height)
        assertPixelsClose(expected, actual)
    }

    @Composable
    private fun OnboardingTestColumn(step: Int, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp).verticalScroll(rememberScrollState())) {
            OnboardingProgress(step)
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            content()
        }
    }

    private fun assertPixelsClose(expected: Bitmap, actual: Bitmap) {
        val expectedPixels = IntArray(expected.width * expected.height)
        val actualPixels = IntArray(actual.width * actual.height)
        expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(actualPixels, 0, actual.width, 0, 0, actual.width, actual.height)
        var changed = 0
        expectedPixels.indices.forEach { index ->
            val a = expectedPixels[index]
            val b = actualPixels[index]
            val delta = kotlin.math.abs((a shr 16 and 0xff) - (b shr 16 and 0xff)) +
                kotlin.math.abs((a shr 8 and 0xff) - (b shr 8 and 0xff)) +
                kotlin.math.abs((a and 0xff) - (b and 0xff))
            if (delta > 30) changed++
        }
        val ratio = changed.toDouble() / expectedPixels.size
        assertTrue("Screenshot changed by ${"%.2f".format(ratio * 100)}%", ratio <= 0.005)
    }

    private fun sampleSettings() = UserSettings(
        id = "settings",
        userId = "user",
        hourlyRate = 50.0,
        featuresTravelRefunds = true,
    )

    private fun sampleShifts() = listOf(
        Shift("one", "user", Instant.parse("2024-01-08T08:00:00Z"), Instant.parse("2024-01-08T17:00:00Z"), breakMinutes = 30),
        Shift("two", "user", Instant.parse("2024-01-12T20:00:00Z"), Instant.parse("2024-01-13T04:00:00Z"), notes = "Late coverage", isSpecialDay = true),
    )
}
