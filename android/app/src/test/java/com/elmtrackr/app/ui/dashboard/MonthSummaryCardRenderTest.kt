package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.MonthlyReport
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

    private fun setCard(report: MonthlyReport?) {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                MonthSummaryCard(
                    report = report,
                    paySummary = null,
                    currencyCode = "ILS",
                    onOpenReport = {},
                )
            }
        }
    }

    /** The state in the reported screenshot: a fresh month, every row at 0.0h. */
    @Test
    fun `the report link sits below the whole legend on an empty month`() {
        setCard(null)

        val weekendRow = composeRule.onNodeWithText("Weekend", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val link = composeRule.onNodeWithText("View full report", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "link top ${link.top} should be at or below the last legend row's bottom ${weekendRow.bottom}",
            link.top.value >= weekendRow.bottom.value,
        )
    }

    /**
     * The collision itself: the link must not share a line with any legend row's
     * hours value. Checked against Overtime, the middle row, which is where a
     * vertically-centred link lands.
     */
    @Test
    fun `the report link does not overlap the overtime row`() {
        setCard(null)

        val overtimeRow = composeRule.onNodeWithText("Overtime", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val link = composeRule.onNodeWithText("View full report", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        val overlaps = link.top.value < overtimeRow.bottom.value &&
            link.bottom.value > overtimeRow.top.value
        assertTrue("link vertically overlaps the Overtime row", !overlaps)
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
