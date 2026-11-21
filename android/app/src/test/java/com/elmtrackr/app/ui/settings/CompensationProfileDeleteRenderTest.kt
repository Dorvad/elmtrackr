package com.elmtrackr.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Deleting a work profile must always be findable.
 *
 * The button used to be rendered only when the user had two or more jobs, so a user
 * with one job saw no delete option at all and no explanation. It is now always
 * there: enabled with two or more jobs, disabled with the reason underneath with one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class CompensationProfileDeleteRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val preset = RegionPresets.forRegion(RegionCode.IL)
    private val cafe = CompensationProfile(
        "cp1", "user", "Cafe", RegionCode.IL, "ILS", "Asia/Jerusalem",
        60.0, preset.rules, preset.stackingPolicy, isDefault = true,
    )
    private val bar = cafe.copy(id = "cp2", name = "Bar", isDefault = false)

    private var deletes = 0

    private fun render(profiles: List<CompensationProfile>) {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                CompensationSettingsContent(
                    state = CompensationSettingsUiState.Ready(
                        profiles = profiles,
                        profile = profiles.first(),
                        settings = UserSettings(id = "s", userId = "user"),
                        presets = RegionPresets.all,
                        currencyOptions = listOf("ILS" to "₪ Israeli shekel"),
                        timezoneOptions = listOf("Asia/Jerusalem"),
                        isSaving = false,
                        saveMessage = null,
                    ),
                    onBack = {},
                    onSelectProfile = {},
                    onCreateProfile = {},
                    onDeleteProfile = { deletes++ },
                    onSave = {},
                    onDismissMessage = {},
                )
            }
        }
    }

    @Test
    fun `with one job the delete option is shown, disabled, with the reason`() {
        render(listOf(cafe))

        composeRule.onNodeWithText("Delete this job").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText(
            "This is your only job, so it can’t be deleted. " +
                "To replace it, add the new job first, then delete this one.",
        ).assertIsDisplayed()
    }

    @Test
    fun `with two jobs delete asks first and then deletes`() {
        render(listOf(cafe, bar))

        composeRule.onNodeWithText("Delete this job").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Delete “Cafe”?").assertIsDisplayed()
        assertEquals("nothing is deleted before the user confirms", 0, deletes)

        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(1, deletes)
    }

    @Test
    fun `with two jobs there is no only-job explanation`() {
        render(listOf(cafe, bar))

        composeRule.onNodeWithText("This is your only job", substring = true).assertDoesNotExist()
    }
}
