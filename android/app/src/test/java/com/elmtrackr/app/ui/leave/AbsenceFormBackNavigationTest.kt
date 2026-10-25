package com.elmtrackr.app.ui.leave

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.data.local.DirectTransactionRunner
import com.elmtrackr.app.data.repository.LocalLeaveRepository
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.fake.FakeAbsenceAllocationDao
import com.elmtrackr.app.fake.FakeAbsenceEventDao
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeLeaveBalanceSnapshotDao
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftDao
import com.elmtrackr.app.fake.FakeSyncTrigger
import com.elmtrackr.app.fake.FakeWorkplaceDao
import com.elmtrackr.app.fake.FakeWorkplacesRepository
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The absence form's ways out, rendered: the system back gesture and the header's
 * back arrow. Both used to close the form outright.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class AbsenceFormBackNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val workplacesRepo = FakeWorkplacesRepository()
    private val viewModel = AbsenceFormViewModel(
        LocalLeaveRepository(
            FakeAbsenceEventDao(),
            FakeAbsenceAllocationDao(),
            FakeLeaveBalanceSnapshotDao(),
            FakeWorkplaceDao(),
            FakeShiftDao(),
            workplacesRepo,
            FakeSettingsRepository(),
            FakeCompensationProfilesRepository(),
            DirectTransactionRunner,
            FakeSyncTrigger(),
        ),
        workplacesRepo,
        FakeCurrentUserProvider(),
    )

    private var closes = 0

    private fun showForm() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                AbsenceFormScreen(
                    type = AbsenceType.VACATION,
                    onClose = { closes++ },
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun systemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun typeNote(text: String) {
        composeRule.onNodeWithText("Note (optional)").performScrollTo().performTextInput(text)
        composeRule.waitForIdle()
    }

    @Test
    fun `back on an untouched form closes it without asking`() {
        showForm()

        systemBack()

        assertEquals(1, closes)
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun `back with unsaved edits asks first, and keep editing keeps them`() {
        showForm()
        typeNote("Beach week")

        systemBack()

        composeRule.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, closes)
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(0, closes)
        composeRule.onNodeWithText("Beach week").assertExists()
    }

    @Test
    fun `discarding closes the form`() {
        showForm()
        typeNote("Beach week")

        systemBack()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.waitForIdle()

        assertEquals(1, closes)
    }

    @Test
    fun `the header's back arrow is guarded the same way`() {
        showForm()
        typeNote("Beach week")

        composeRule.onNodeWithContentDescription("Back").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, closes)
    }
}
