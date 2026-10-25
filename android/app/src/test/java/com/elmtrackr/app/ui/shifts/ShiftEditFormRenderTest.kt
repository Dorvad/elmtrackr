package com.elmtrackr.app.ui.shifts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.RoundingRules
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The shift form explains what a shift is paid, in the shift's own currency and
 * the user's language, and does not lose edits on the way out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class ShiftEditFormRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Account in shekels; this job is paid in dollars and rounds to the quarter hour. */
    private val settings = UserSettings(
        id = "cfg", userId = "u1", currencyCode = "ILS", defaultCompensationProfileId = "usd-job",
    )
    private val dollarJob = CompensationProfile(
        id = "usd-job",
        userId = "u1",
        name = "Cafe",
        regionCode = RegionCode.US,
        currencyCode = "USD",
        timezone = "UTC",
        baseHourlyRate = 20.0,
        rules = RegionPresets.forRegion(RegionCode.US).rules.copy(
            rounding = RoundingRules(enabled = true, incrementMinutes = 15, direction = "nearest"),
        ),
        stackingPolicy = RegionPresets.forRegion(RegionCode.US).stackingPolicy,
        isDefault = true,
    )

    /** Monday 09:00–16:52 UTC: 7h 52m worked, paid as 7h 45m. */
    private val saved = Shift(
        id = "s1",
        userId = "u1",
        startTime = Instant.parse("2026-10-05T09:00:00Z"),
        endTime = Instant.parse("2026-10-05T16:52:00Z"),
        compensationProfileId = "usd-job",
    )

    private fun render(endMillis: Long = saved.endTime!!.toEpochMilli(), onClose: () -> Unit = {}) {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ShiftEditFormContent(
                    navState = ShiftFormNavState.Edit(saved),
                    settings = settings,
                    errors = emptyMap(),
                    featuresTravelRefunds = false,
                    onSave = {},
                    onDelete = {},
                    onClose = onClose,
                    onPickStartDate = {},
                    onPickStartTime = {},
                    onPickEndDate = {},
                    onPickEndTime = {},
                    startMillis = saved.startTime.toEpochMilli(),
                    endMillis = endMillis,
                    hasEndTime = true,
                    onHasEndTimeChange = {},
                    breakMinutes = 0,
                    onBreakMinutesChange = {},
                    notesText = "",
                    onNotesChange = {},
                    premiumProfileId = null,
                    onPremiumProfileIdChange = {},
                    premiumProfiles = emptyList(),
                    profiles = listOf(dollarJob),
                    allShiftsForPay = listOf(saved),
                    compensationProfileId = "usd-job",
                    onCompensationProfileIdChange = {},
                    tasks = emptyList(),
                    taskId = null,
                    onTaskIdChange = {},
                    showRefundSection = false,
                    initialShift = saved,
                )
            }
        }
    }

    @Test
    fun `the preview is in the job's currency, not the account's`() {
        render()
        // 7h 45m at 20.00 = 155.00, in dollars: the total and its single bracket line.
        assertEquals(
            2,
            composeRule.onAllNodesWithText("\$155.00", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        assertTrue(
            composeRule.onAllNodesWithText("₪", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `bracket lines use translated labels, not the engine's diagnostic text`() {
        render()
        assertTrue(
            composeRule.onAllNodesWithText("100% Regular", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithText("% — Regular", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `the time line names worked time and explains the rounding`() {
        render()
        composeRule.onNodeWithText("Worked", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("after rounding", substring = true, useUnmergedTree = true).assertIsDisplayed()
        // The old line called clock time "worked" and clock time less breaks "paid".
        assertTrue(
            composeRule.onAllNodesWithText("h paid", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `an unchanged shift shows no comparison line`() {
        render()
        assertTrue(
            composeRule.onAllNodesWithText("Currently saved", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `a changed end time shows what is saved now beside the new estimate`() {
        render(endMillis = Instant.parse("2026-10-05T18:00:00Z").toEpochMilli())
        composeRule.onNodeWithText("Currently saved: 09:00 – 16:52", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `leaving with unsaved edits asks first`() {
        var closed = 0
        render(endMillis = Instant.parse("2026-10-05T18:00:00Z").toEpochMilli(), onClose = { closed++ })

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals(0, closed)
        composeRule.onNodeWithText("Keep editing").performClick()
        assertEquals(0, closed)
    }

    @Test
    fun `the saved row is priced as stored until something that affects pay changes`() {
        val notesOnly = saved.copy(notes = "draft notes", compensationSnapshot = null)
        assertSame(saved, shiftAsItWillBeSaved(saved, notesOnly))

        val retimed = saved.copy(endTime = Instant.parse("2026-10-05T18:00:00Z"))
        assertSame(retimed, shiftAsItWillBeSaved(saved, retimed))

        val running = saved.copy(endTime = null)
        assertSame(notesOnly, shiftAsItWillBeSaved(running, notesOnly))
    }
}
