package com.elmtrackr.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Paid Projects update wizard, as a user meets it.
 *
 * This pop-up replaced the old discovery card, so what it must preserve is the
 * card's contract: the user leaves either by turning the feature on or by
 * skipping, never both, and each choice fires exactly one callback. The paging
 * itself is checked through the visible page titles, not internal state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class PaidProjectsUpdateWizardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val title = "ElmTrackr v1.2 - Paid Projects are here"
    private val pageTitles = listOf(
        "Track fixed-price work",
        "Clock in against a project",
        "Billing and payments",
        "Know your real hourly rate",
    )

    private var enabled = 0
    private var dismissed = 0

    private fun render() {
        composeRule.setContent {
            ElmTrackrTheme {
                PaidProjectsUpdateWizard(
                    onEnable = { enabled++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    private fun next() = composeRule.onNodeWithText("Next").performClick()

    @Test
    fun `opens on the release title and the first feature`() {
        render()

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(pageTitles.first()).assertIsDisplayed()
    }

    @Test
    fun `next walks through every feature page`() {
        render()

        pageTitles.forEachIndexed { index, pageTitle ->
            composeRule.onNodeWithText(pageTitle).assertIsDisplayed()
            if (index < pageTitles.lastIndex) next()
        }
    }

    @Test
    fun `back returns to the previous feature page`() {
        render()
        next()
        composeRule.onNodeWithText(pageTitles[1]).assertIsDisplayed()

        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithText(pageTitles[0]).assertIsDisplayed()
    }

    @Test
    fun `the final page offers the activation choice`() {
        render()
        repeat(pageTitles.lastIndex) { next() }

        composeRule.onNodeWithText("Turn on Paid Projects").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").assertIsDisplayed()
    }

    @Test
    fun `activating fires enable once and never dismiss`() {
        render()
        repeat(pageTitles.lastIndex) { next() }

        composeRule.onNodeWithText("Turn on Paid Projects").performClick()

        assertEquals(1, enabled)
        assertEquals(0, dismissed)
    }

    @Test
    fun `not now fires dismiss and never enable`() {
        render()
        repeat(pageTitles.lastIndex) { next() }

        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(0, enabled)
        assertEquals(1, dismissed)
    }

    @Test
    fun `skipping from the first page fires dismiss and never enable`() {
        render()

        composeRule.onNodeWithText("Skip").performClick()

        assertEquals(0, enabled)
        assertEquals(1, dismissed)
    }

    @Test
    fun `the close button dismisses and is named for a screen reader`() {
        render()

        composeRule
            .onNodeWithContentDescription("Close the Paid Projects introduction")
            .performClick()

        assertEquals(0, enabled)
        assertEquals(1, dismissed)
    }

    @Test
    fun `no callback fires without an explicit choice`() {
        render()
        next()
        next()

        assertTrue(enabled == 0)
        assertFalse(dismissed != 0)
    }
}
