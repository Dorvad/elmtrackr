package com.elmtrackr.app.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ReportScope
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.PaidTime
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.ShiftBreakdown
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class ReportsOverviewRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val settings = UserSettings(id = "s", userId = "u1", featuresInsights = false)

    private fun job(id: String, name: String) = CompensationProfile(
        id = id, userId = "u1", name = name, regionCode = RegionCode.IL, currencyCode = "ILS",
        timezone = "UTC", baseHourlyRate = 50.0, rules = RegionPresets.forRegion(RegionCode.IL).rules,
        stackingPolicy = RegionPresets.forRegion(RegionCode.IL).stackingPolicy, isDefault = id == "a",
    )

    /** Two shifts: 15h 44m worked, paid as 16h 00m after rounding. */
    private val report = run {
        fun shift(worked: Int, paid: Int) = ShiftBreakdown(
            totalMinutes = paid, regularMinutes = paid, overtimeMinutes = 0, weekendMinutes = 0,
            segments = emptyList(), workedMinutes = worked,
            paidTime = PaidTime(worked, 0, 0, false, paid - worked, 0, paid),
        )
        val shifts = listOf(shift(472, 480), shift(472, 480))
        MonthlyReport(
            year = 2024, month = 1,
            totalMinutes = 960, regularMinutes = 960, overtimeMinutes = 0, weekendMinutes = 0,
            shiftCount = 2, shifts = shifts, workedMinutes = 944,
        )
    }

    /** Regular 700 + overtime 100 + weekend 120 + night 80 = 1 000. */
    private val pay = PayrollCalculator.MonthlyPaySummary(
        totalGross = 1000.0, regularGross = 700.0, overtimeGross = 100.0, specialGross = 120.0,
        weekendGross = 120.0, nightGross = 80.0, currencyCode = "ILS",
        byCurrency = listOf(
            PayrollCalculator.CurrencyPayTotals(
                currencyCode = "ILS", totalGross = 1000.0, regularGross = 700.0, overtimeGross = 100.0,
                specialGross = 120.0, weekendGross = 120.0, nightGross = 80.0,
            ),
        ),
    )

    private fun state(
        scope: ReportScope = ReportScope.AllJobs,
        report: MonthlyReport = this.report,
        profiles: List<CompensationProfile> = listOf(job("a", "Cafe"), job("b", "Studio")),
    ) = ReportsUiState.Ready(
        year = 2024, month = 1, report = report, weeklyTotals = emptyList(), paySummary = pay,
        settings = settings, profiles = profiles, zone = ZoneOffset.UTC, scope = scope, allJobsShiftCount = 2,
    )

    private fun render(state: ReportsUiState.Ready, rtl: Boolean = false, fontScale: Float = 1f) {
        composeRule.setContent {
            val base = LocalDensity.current
            ElmTrackrTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(base.density, fontScale),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HoursReport(
                            state = state,
                            onPreviousMonth = {}, onNextMonth = {}, canGoNext = false,
                            onExportCsv = {}, onExportPdf = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the overview leads with worked hours and estimated gross`() {
        render(state())

        composeRule.onNodeWithText("WORKED").assertIsDisplayed()
        // Headline, and again as the first step of "How paid time was counted".
        assertEquals(2, composeRule.onAllNodesWithText("15.7 h", substring = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("ESTIMATED GROSS · BEFORE TAX").assertIsDisplayed()
        composeRule.onNodeWithText("Paid as 16.0 h after rounding", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 shifts · All jobs").assertIsDisplayed()
    }

    @Test
    fun `the pay explanation lists every component and they add up`() {
        render(state())

        composeRule.onNodeWithText("HOW YOUR PAY WAS ESTIMATED").performScrollTo().assertIsDisplayed()
        listOf("Regular pay", "Overtime", "Weekend / rest day", "Night premium", "Estimated gross").forEach {
            composeRule.onNodeWithContentDescription("$it, ", substring = true).performScrollTo().assertIsDisplayed()
        }
        // The old card labelled weekend money "Holiday" and left night out.
        assertTrue(
            composeRule.onAllNodesWithText("HOLIDAY", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `paid time steps are listed when rounding changed them`() {
        render(state())
        composeRule.onNodeWithText("HOW PAID TIME WAS COUNTED").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Rounding").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the export card names the period, the job scope and the currency`() {
        render(state(scope = ReportScope.Job("b")))

        composeRule.onNodeWithText("January 2024 · ", substring = true).performScrollTo().assertIsDisplayed()
        // Named in the overview and on the export card.
        assertEquals(2, composeRule.onAllNodesWithText("Studio", substring = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Amounts in ILS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("CSV spreadsheet").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PDF summary").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `stays readable right to left at double font size`() {
        render(state(), rtl = true, fontScale = 2f)
        composeRule.onNodeWithText("WORKED").assertIsDisplayed()
        composeRule.onNodeWithText("CSV spreadsheet").performScrollTo().assertIsDisplayed()
    }
}
