package com.elmtrackr.app.ui.reports

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.Workplace
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeLeaveRepository
import com.elmtrackr.app.fake.FakeWorkplacesRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeProjectsRepository
import com.elmtrackr.app.fake.FakeRefundReceiptStorage
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeReviewPromptRecorder
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Where the dashboard's refund reminder lands.
 *
 * It used to open Reports on Hours, leaving the user to find the Travel Refunds
 * tab themselves — the work the nudge existed to save them. The tab is now
 * requested by the caller and consumed once, which is the part worth holding: a
 * request that stuck would override the user's own tab choice every time they
 * came back to Reports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class ReportsLaunchRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val settingsRepo = FakeSettingsRepository()
    private val leaveRepo = FakeLeaveRepository()
    private val workplacesRepo = FakeWorkplacesRepository()

    private fun viewModel(): ReportsViewModel {
        settingsRepo.setSettings(
            UserSettings(id = "s", userId = "u1", featuresTravelRefunds = true),
        )
        return ReportsViewModel(
            FakeReportsRepository(),
            FakeShiftsRepository(),
            FakeTasksRepository(),
            settingsRepo,
            FakeCurrentUserProvider(),
            FakeRefundsRepository(),
            FakeCompensationProfilesRepository(),
            FakePremiumProfilesRepository(),
            FakeRefundReceiptStorage(),
            FakeProjectsRepository(),
            FakeReviewPromptRecorder(),
            leaveRepo,
            workplacesRepo,
            computationDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `every launch request maps to a tab`() {
        // A request added without a branch would not compile; this catches the
        // other direction, two requests collapsing onto one tab.
        val tabs = ReportsLaunchRequest.entries.map { it.toTab() }
        assertEquals(tabs.size, tabs.distinct().size)
        assertEquals(ReportTab.REFUNDS, ReportsLaunchRequest.REFUNDS.toTab())
    }

    @Test
    fun `Reports opens on Hours with no request`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ReportsScreen(viewModel = viewModel())
            }
        }

        composeRule.onNodeWithText("Hours").assertIsSelected()
        composeRule.onNodeWithText("Travel Refunds").assertIsNotSelected()
    }

    @Test
    fun `a refunds request opens the refunds tab and is consumed`() {
        var consumed = 0
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ReportsScreen(
                    viewModel = viewModel(),
                    pendingLaunch = ReportsLaunchRequest.REFUNDS,
                    onPendingLaunchConsumed = { consumed++ },
                )
            }
        }

        composeRule.onNodeWithText("Travel Refunds").assertIsSelected()
        composeRule.onNodeWithText("Hours").assertIsNotSelected()
        assertEquals(1, consumed)
    }

    /**
     * The reason the request is consumed rather than held: a stuck request would
     * drag the user back to Refunds every time Reports recomposed, so switching
     * tabs by hand would appear not to work.
     */
    @Test
    fun `the request does not fight the user's own tab choice`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ReportsScreen(
                    viewModel = viewModel(),
                    pendingLaunch = ReportsLaunchRequest.REFUNDS,
                )
            }
        }
        composeRule.onNodeWithText("Travel Refunds").assertIsSelected()

        composeRule.onNodeWithText("Hours").performClick()

        composeRule.onNodeWithText("Hours").assertIsSelected()
        composeRule.onNodeWithText("Travel Refunds").assertIsNotSelected()
    }

    @Test
    fun `a vacation day is named on the hours report`() {
        val day = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(15)
        workplacesRepo.setWorkplaces(
            Workplace(
                id = "wp",
                userId = "local-user",
                name = "Cafe North",
                regionCode = RegionCode.IL,
                currencyCode = "ILS",
                timezone = "UTC",
            ),
        )
        leaveRepo.setEvents(
            AbsenceEvent(
                id = "e1",
                userId = "local-user",
                type = AbsenceType.VACATION,
                startDate = day,
                endDate = day,
                notes = "beach day",
            ),
        )
        leaveRepo.setAllocations(
            AbsenceAllocation(
                id = "a1",
                userId = "local-user",
                absenceEventId = "e1",
                workplaceId = "wp",
                affectedDate = day,
                entitlementUnits = 1.0,
                unit = LeaveBalanceUnit.DAYS,
                estimatedGrossPay = 320.0,
            ),
        )

        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ReportsScreen(viewModel = viewModel())
            }
        }

        assertTrue(
            composeRule.onAllNodesWithText("Vacation", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText("PAID LEAVE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cafe North", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("beach day", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No completed shifts").assertDoesNotExist()
    }

    @Test
    fun `leave at two workplaces in two currencies shows two totals, never one`() {
        val day = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(15)
        workplacesRepo.setWorkplaces(
            Workplace(
                id = "wp-ils",
                userId = "local-user",
                name = "Cafe North",
                regionCode = RegionCode.IL,
                currencyCode = "ILS",
                timezone = "UTC",
            ),
            Workplace(
                id = "wp-usd",
                userId = "local-user",
                name = "Remote Inc",
                regionCode = RegionCode.US,
                currencyCode = "USD",
                timezone = "UTC",
            ),
        )
        leaveRepo.setEvents(
            AbsenceEvent(
                id = "e1",
                userId = "local-user",
                type = AbsenceType.VACATION,
                startDate = day,
                endDate = day,
            ),
        )
        leaveRepo.setAllocations(
            AbsenceAllocation(
                id = "a1",
                userId = "local-user",
                absenceEventId = "e1",
                workplaceId = "wp-ils",
                affectedDate = day,
                entitlementUnits = 1.0,
                unit = LeaveBalanceUnit.DAYS,
                estimatedGrossPay = 4000.0,
            ),
            AbsenceAllocation(
                id = "a2",
                userId = "local-user",
                absenceEventId = "e1",
                workplaceId = "wp-usd",
                affectedDate = day,
                entitlementUnits = 1.0,
                unit = LeaveBalanceUnit.DAYS,
                estimatedGrossPay = 1000.0,
            ),
        )

        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ReportsScreen(viewModel = viewModel())
            }
        }

        // The code is bidi-isolated inside the label, so match it apart from the marks.
        val totals = composeRule.onAllNodesWithText("Estimated total (", substring = true)
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(2, totals.size)
        assertTrue(totals.toString(), totals.any { "ILS" in it })
        assertTrue(totals.toString(), totals.any { "USD" in it })
        // The two amounts added together, under either label, must not appear.
        assertTrue(composeRule.onAllNodesWithText("5,000", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
