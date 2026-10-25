package com.elmtrackr.app.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.dashboard.MonthSummaryCard
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale

/**
 * The Dashboard month card and the Reports pay card, rendered for a month at a shekel
 * job (4,000) and a dollar job (1,000).
 *
 * Both used to print `totalGross` — 5,000 — under whichever currency the fallback
 * picked. They now print one figure per currency, labelled with its code, and a
 * single-currency month renders exactly as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class MixedCurrencyPayRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun profile(id: String, currency: String, rate: Double) = CompensationProfile(
        id = id,
        userId = "u1",
        name = id,
        regionCode = RegionCode.CUSTOM,
        currencyCode = currency,
        timezone = "UTC",
        baseHourlyRate = rate,
        rules = RegionPresets.forRegion(RegionCode.CUSTOM).rules.copy(weekendEnabled = false),
        stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        isDefault = currency == "ILS",
    )

    private val ils = profile("job-a", "ILS", 500.0)
    private val usd = profile("job-b", "USD", 125.0)
    private val settings = UserSettings(id = "cfg", userId = "u1", timezone = "UTC", currencyCode = "ILS")

    private fun shift(id: String, day: Int, profile: CompensationProfile) = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse("2024-01-%02dT09:00:00Z".format(day)),
        endTime = Instant.parse("2024-01-%02dT17:00:00Z".format(day)),
        breakMinutes = 0,
        compensationProfileId = profile.id,
    )

    private val mixed = PayrollCalculator.sumMonthlyPay(
        listOf(shift("a", 8, ils), shift("b", 9, usd)), settings, listOf(ils, usd),
    )
    private val single = PayrollCalculator.sumMonthlyPay(
        listOf(shift("a", 8, ils)), settings, listOf(ils, usd),
    )

    private val report = MonthlyReport(2024, 1, 960, 960, 0, 0, 2, emptyList())

    private fun SemanticsNodeInteractionCollection.texts(): List<String> =
        fetchSemanticsNodes().map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }

    /** Every Text node on its own: the month card is clickable and merges them otherwise. */
    private fun allTexts(): List<String> =
        composeRule.onAllNodesWithText("", substring = true, useUnmergedTree = true).texts()

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Test
    fun `dashboard month card shows each currency on its own and never their sum`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                MonthSummaryCard(report = report, paySummary = mixed, onOpenReport = {})
            }
        }

        val texts = allTexts()
        // Each amount is its own line with its own code.
        assertTrue(texts.toString(), texts.any { "4,000.00" in it && "ILS" in it && "USD" !in it })
        assertTrue(texts.toString(), texts.any { "1,000.00" in it && "USD" in it && "ILS" !in it })
        assertTrue(texts.toString(), texts.none { "5,000" in it })
    }

    @Test
    fun `dashboard month card for one currency reads exactly as before`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                MonthSummaryCard(report = report, paySummary = single, onOpenReport = {})
            }
        }

        val texts = allTexts()
        // Byte for byte what the single-currency card always printed.
        assertTrue(texts.toString(), texts.contains(MoneyFormatter.format(4000.0, "ILS", Locale.getDefault())))
        assertTrue(texts.toString(), texts.none { "ILS" in it })
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    private fun setHoursReport(pay: PayrollCalculator.MonthlyPaySummary) {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoursReport(
                        state = ReportsUiState.Ready(
                            year = 2024,
                            month = 1,
                            report = report,
                            weeklyTotals = emptyList(),
                            paySummary = pay,
                            settings = settings,
                            profiles = listOf(ils, usd),
                        ),
                        onPreviousMonth = {},
                        onNextMonth = {},
                        canGoNext = false,
                        onExportCsv = {},
                        onExportPdf = {},
                    )
                }
            }
        }
    }

    @Test
    fun `reports show one gross pay card per currency, each labelled`() {
        setHoursReport(mixed)

        val labels = composeRule.onAllNodesWithText("HOW YOUR PAY WAS ESTIMATED", substring = true).texts()
        assertEquals(labels.toString(), 2, labels.size)
        assertTrue(labels.toString(), labels.any { "ILS" in it })
        assertTrue(labels.toString(), labels.any { "USD" in it })
        val texts = allTexts()
        assertTrue(texts.toString(), texts.contains(MoneyFormatter.format(4000.0, "ILS", Locale.getDefault())))
        assertTrue(texts.toString(), texts.contains(MoneyFormatter.format(1000.0, "USD", Locale.getDefault())))
        assertTrue(texts.toString(), texts.none { "5,000" in it })
    }

    @Test
    fun `reports for one currency keep the single unlabelled card`() {
        setHoursReport(single)

        val labels = composeRule.onAllNodesWithText("HOW YOUR PAY WAS ESTIMATED", substring = true).texts()
        assertEquals(listOf("HOW YOUR PAY WAS ESTIMATED"), labels)
        assertTrue(allTexts().contains(MoneyFormatter.format(4000.0, "ILS", Locale.getDefault())))
    }
}
