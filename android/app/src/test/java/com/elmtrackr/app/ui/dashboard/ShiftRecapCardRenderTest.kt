package com.elmtrackr.app.ui.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.PaidTime
import com.elmtrackr.app.domain.model.PayBracket
import com.elmtrackr.app.domain.model.PayCategory
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.ui.common.LocalWorkZone
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class ShiftRecapCardRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 09:00–18:12 with a 30 minute break: 8h 42m worked, rounded to 8h 45m paid. */
    private val time = PaidTime(
        elapsedMinutes = 552,
        recordedBreakMinutes = 30,
        autoBreakMinutes = 0,
        breaksPaid = false,
        roundingMinutes = 3,
        minimumTopUpMinutes = 0,
        payableMinutes = 525,
    )

    private val pay = ShiftPayBreakdown(
        brackets = listOf(
            PayBracket("100% — Regular", 480, 1.0, 400.0, PayCategory.REGULAR),
            PayBracket("125% — Overtime", 45, 1.25, 46.875, PayCategory.DAILY_OVERTIME),
        ),
        totalGross = 446.875,
        regularGross = 400.0,
        overtimeGross = 46.875,
        isSpecial = false,
        currencyCode = "ILS",
    )

    private fun recap(pay: ShiftPayBreakdown? = this.pay) = DashboardViewModel.ShiftRecap(
        shiftId = "s1",
        startTime = Instant.parse("2026-10-05T09:00:00Z"),
        endTime = Instant.parse("2026-10-05T18:12:00Z"),
        time = time,
        pay = pay,
    )

    private fun render(
        recap: DashboardViewModel.ShiftRecap,
        onReview: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onSetRate: () -> Unit = {},
        rtl: Boolean = false,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            ElmTrackrTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalWorkZone provides ZoneOffset.UTC,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(base.density, fontScale),
                ) {
                    ShiftRecapCard(recap, onReview = onReview, onDismiss = onDismiss, onSetRate = onSetRate)
                }
            }
        }
    }

    @Test
    fun `shows each step from shift length to paid time`() {
        render(recap())

        composeRule.onNodeWithText("Shift ended").assertIsDisplayed()
        composeRule.onNodeWithText("09:00 – 18:12").assertIsDisplayed()
        composeRule.onNodeWithText("Shift length", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Unpaid break", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Worked", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Paid time", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("after rounding", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `shows the engine total and the overtime inside it`() {
        render(recap())

        composeRule.onNodeWithText("Estimated pay", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Estimate, before tax", useUnmergedTree = true).assertIsDisplayed()
        // Once as the headline and once as the "Estimated gross" row the parts add up to.
        assertEquals(
            2,
            composeRule.onAllNodesWithText("446.88", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        // The overtime row reads as one item to TalkBack, label and amount together.
        val overtime = composeRule.onNodeWithContentDescription("Overtime, ", substring = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
            .single()
        assertTrue(overtime, overtime.contains("46.88"))
    }

    @Test
    fun `never claims the shift reached the cloud`() {
        render(recap())
        assertTrue(
            composeRule.onAllNodesWithText("sync", substring = true, ignoreCase = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `review opens the shift and done dismisses`() {
        var reviewed = 0
        var dismissed = 0
        render(recap(), onReview = { reviewed++ }, onDismiss = { dismissed++ })

        composeRule.onNodeWithText("Review or edit").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithContentDescription("Dismiss shift summary").performClick()

        assertEquals(1, reviewed)
        assertEquals(2, dismissed)
    }

    @Test
    fun `without a rate it offers to set one instead of showing zero`() {
        var setRate = false
        render(recap(pay = null), onSetRate = { setRate = true })

        composeRule.onNodeWithText("Add your hourly rate to see estimated earnings.").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Estimated pay", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithText("Set rate").performClick()
        assertTrue(setRate)
    }

    @Test
    fun `stays usable right to left at double font size`() {
        render(recap(), rtl = true, fontScale = 2f)

        composeRule.onNodeWithText("Worked", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Review or edit").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
    }
}
