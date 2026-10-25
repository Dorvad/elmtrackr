package com.elmtrackr.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The work profile form must not lose typed changes to a gesture or a chip, and must
 * not claim there are changes when there are none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class CompensationFormGuardRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val preset = RegionPresets.forRegion(RegionCode.IL)
    private val profile = CompensationProfile(
        "cp1", "user", "Cafe", RegionCode.IL, "ILS", "Asia/Jerusalem",
        60.0, preset.rules, preset.stackingPolicy, isDefault = true,
    )

    private var backs = 0
    private var creates = 0

    private fun render() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                CompensationSettingsContent(
                    state = CompensationSettingsUiState.Ready(
                        profiles = listOf(profile),
                        profile = profile,
                        settings = UserSettings(id = "s", userId = "user"),
                        presets = RegionPresets.all,
                        currencyOptions = listOf("ILS" to "₪ Israeli shekel", "USD" to "$ US dollar"),
                        timezoneOptions = listOf("Asia/Jerusalem", "UTC"),
                        isSaving = false,
                        saveMessage = null,
                    ),
                    onBack = { backs++ },
                    onSelectProfile = {},
                    onCreateProfile = { creates++ },
                    onDeleteProfile = {},
                    onSave = {},
                    onDismissMessage = {},
                )
            }
        }
    }

    private fun rateField() = composeRule.onNode(hasSetTextAction() and hasText("60"))

    private fun systemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the rate shows as typed, without a stray decimal`() {
        render()
        // Was "60.0", which then never equalled what the user typed.
        rateField().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the back gesture asks before discarding typed changes`() {
        render()
        rateField().performScrollTo().performTextReplacement("75")
        composeRule.waitForIdle()

        systemBack()

        // The gesture stayed on the screen instead of falling through and finishing.
        assertEquals(false, composeRule.activity.isFinishing)
        composeRule.onNodeWithText("Discard your changes?").assertExists()
        composeRule.onNodeWithText("Keep editing").performClick()
        // Nothing left the screen, and the form is still there to save.
        composeRule.onNodeWithText("Discard your changes?").assertDoesNotExist()
    }

    @Test
    fun `retyping the saved rate is not an unsaved change`() {
        render()
        rateField().performScrollTo().performTextReplacement("60")
        composeRule.waitForIdle()

        systemBack()

        composeRule.onNodeWithText("Discard your changes?").assertDoesNotExist()
    }

    @Test
    fun `adding a job with unsaved edits asks first`() {
        render()
        rateField().performScrollTo().performTextReplacement("75")

        // A LazyColumn: the chip row was disposed when the rate field scrolled into view.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Add job"))
        composeRule.onNodeWithText("Add job").performClick()

        composeRule.onNodeWithText("Discard your changes?").assertExists()
        assertEquals(0, creates)
    }

    @Test
    fun `a comma typed in the rate is kept as a decimal point`() {
        render()
        rateField().performScrollTo().performTextReplacement("62,5")
        composeRule.onNode(hasSetTextAction() and hasText("62.5")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `advanced rules start folded and open with one tap`() {
        render()
        composeRule.onNodeWithText("Round paid time").assertDoesNotExist()

        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Overtime, breaks and other pay rules"))
        composeRule.onNodeWithText("Overtime, breaks and other pay rules").performClick()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Round paid time"))

        composeRule.onNodeWithText("Round paid time").assertIsDisplayed()
    }
}
