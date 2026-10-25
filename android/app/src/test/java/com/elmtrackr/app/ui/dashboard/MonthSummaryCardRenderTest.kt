package com.elmtrackr.app.ui.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.PaidTime
import com.elmtrackr.app.domain.model.ShiftBreakdown
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Layout guards for the dashboard's month summary card.
 *
 * The "View full report" action used to be a sibling of the hours legend inside a
 * SpaceBetween row. Two things went wrong at once: centred against a three-row
 * legend it sat level with the middle row, so it read as part of "Overtime"; and
 * because each legend row is itself full-width SpaceBetween, that row's hours
 * value was pushed hard against it and rendered as "0.0hView full report".
 *
 * Both are geometry, which is why these assertions are geometric rather than a
 * check that the text exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class MonthSummaryCardRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setCard(
        report: MonthlyReport?,
        paySummary: PayrollCalculator.MonthlyPaySummary? = null,
        hasRate: Boolean = paySummary != null,
        onSetRate: () -> Unit = {},
        coversSeveralJobs: Boolean = false,
        rtl: Boolean = false,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            ElmTrackrTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(base.density, fontScale),
                ) {
                    MonthSummaryCard(
                        report = report,
                        paySummary = paySummary,
                        onOpenReport = {},
                        hasRate = hasRate,
                        onSetRate = onSetRate,
                        coversSeveralJobs = coversSeveralJobs,
                    )
                }
            }
        }
    }

    private fun shiftWith(paidTime: PaidTime) = ShiftBreakdown(
        totalMinutes = paidTime.payableMinutes,
        regularMinutes = paidTime.payableMinutes,
        overtimeMinutes = 0,
        weekendMinutes = 0,
        segments = emptyList(),
        workedMinutes = paidTime.workedMinutes,
        paidTime = paidTime,
    )

    /** 7h 42m worked, paid as 7h 45m by a quarter-hour rounding rule. */
    private val roundedShift = shiftWith(
        PaidTime(
            elapsedMinutes = 462, recordedBreakMinutes = 0, autoBreakMinutes = 0,
            breaksPaid = false, roundingMinutes = 3, minimumTopUpMinutes = 0, payableMinutes = 465,
        ),
    )

    private fun reportOf(vararg shifts: ShiftBreakdown) = MonthlyReport(
        year = 2026,
        month = 10,
        totalMinutes = shifts.sumOf { it.totalMinutes },
        regularMinutes = shifts.sumOf { it.regularMinutes },
        overtimeMinutes = 0,
        weekendMinutes = 0,
        shiftCount = shifts.size,
        shifts = shifts.toList(),
        workedMinutes = shifts.sumOf { it.workedMinutes },
    )

    private fun pay(vararg totals: Pair<String, Double>) = PayrollCalculator.MonthlyPaySummary(
        totalGross = totals.sumOf { it.second },
        regularGross = totals.sumOf { it.second },
        overtimeGross = 0.0,
        specialGross = 0.0,
        currencyCode = totals.singleOrNull()?.first,
        byCurrency = totals.map { (code, amount) ->
            PayrollCalculator.CurrencyPayTotals(code, amount, amount, 0.0, 0.0)
        },
    )

    /**
     * A fresh month used to render a bar and three "0.0h" legend rows, which said
     * nothing. It now says so in words, and the report link still closes the card.
     */
    @Test
    fun `an empty month says so and keeps the report link last`() {
        setCard(null)

        val message = composeRule.onNodeWithText("No completed shifts this month yet.", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val link = composeRule.onNodeWithText("View full report", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "link top ${link.top} should be at or below the empty-month message ${message.bottom}",
            link.top.value >= message.bottom.value,
        )
        assertTrue(
            "no zero-hour legend on an empty month",
            composeRule.onAllNodesWithText("Overtime", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `hours are labelled as worked, not as a total`() {
        setCard(reportOf(roundedShift))

        composeRule.onNodeWithText("Worked", useUnmergedTree = true).assertIsDisplayed()
        // 462 minutes is 7.7h worked; the payable 7.75h must not be the headline.
        composeRule.onNodeWithText("7.7h", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Total", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `paid time is explained only when it differs from worked time`() {
        setCard(reportOf(roundedShift))
        composeRule.onNodeWithText("Paid as 7.8h after rounding", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `no paid-time note when the rules leave worked time alone`() {
        val plain = shiftWith(
            PaidTime(480, 30, 0, false, 0, 0, 450),
        )
        setCard(reportOf(plain))

        assertTrue(
            composeRule.onAllNodesWithText("Paid as", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `earnings are labelled as an estimate before tax`() {
        setCard(reportOf(roundedShift), paySummary = pay("ILS" to 387.5))
        composeRule.onNodeWithText("ESTIMATED GROSS · BEFORE TAX", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `two currencies are two figures, never one sum`() {
        setCard(reportOf(roundedShift), paySummary = pay("ILS" to 300.0, "USD" to 100.0))

        composeRule.onNodeWithText("ILS", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("USD", substring = true, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            "400 is not an amount of anything",
            composeRule.onAllNodesWithText("400", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `without a rate the card offers to set one`() {
        var opened = false
        setCard(reportOf(roundedShift), paySummary = null, hasRate = false, onSetRate = { opened = true })

        composeRule.onNodeWithText("Add your hourly rate to see estimated earnings.", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Set rate").performClick()
        assertTrue(opened)
    }

    @Test
    fun `with a rate there is no set-rate prompt`() {
        setCard(reportOf(roundedShift), paySummary = pay("ILS" to 387.5))
        assertTrue(
            composeRule.onAllNodesWithText("Set rate", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `several jobs are named as covered`() {
        setCard(reportOf(roundedShift), paySummary = pay("ILS" to 387.5), coversSeveralJobs = true)
        composeRule.onNodeWithText("All jobs", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    /** Hebrew and Arabic users at a large font: every element still drawn, link still last. */
    @Test
    fun `stays readable right to left at double font size`() {
        setCard(reportOf(roundedShift), paySummary = pay("ILS" to 387.5), rtl = true, fontScale = 2f)

        composeRule.onNodeWithText("Worked", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Paid as 7.8h after rounding", useUnmergedTree = true).assertIsDisplayed()
        val weekendRow = composeRule.onNodeWithText("Weekend", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val link = composeRule.onNodeWithText("View full report", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(link.top.value >= weekendRow.bottom.value)
    }

    @Test
    fun `the link stays below the legend with hours recorded`() {
        setCard(
            MonthlyReport(
                year = 2026,
                month = 8,
                totalMinutes = 9_600,
                regularMinutes = 7_200,
                overtimeMinutes = 1_800,
                weekendMinutes = 600,
                shiftCount = 20,
                shifts = emptyList(),
            ),
        )

        composeRule.onNodeWithText("View full report", useUnmergedTree = true).assertIsDisplayed()

        val weekendRow = composeRule.onNodeWithText("Weekend", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val link = composeRule.onNodeWithText("View full report", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "link top ${link.top} should be at or below the last legend row's bottom ${weekendRow.bottom}",
            link.top.value >= weekendRow.bottom.value,
        )
    }
}
