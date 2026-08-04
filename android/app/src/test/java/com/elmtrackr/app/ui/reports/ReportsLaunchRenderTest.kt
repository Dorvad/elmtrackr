package com.elmtrackr.app.ui.reports

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
