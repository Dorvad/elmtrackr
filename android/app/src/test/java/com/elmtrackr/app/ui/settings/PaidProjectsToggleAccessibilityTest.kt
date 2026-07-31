package com.elmtrackr.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Paid Projects feature selector, as a screen reader meets it.
 *
 * This is the control that adds and removes a navigation destination, so it is
 * worth more than a glance. The defect it guards against is specific: with an
 * interactive Switch sitting next to its title in a separate node, the control
 * has no accessible name, and TalkBack announces "switch, off" — accurate about
 * the state and silent about which of the eight settings on the screen it
 * belongs to.
 *
 * [SettingsToggleRow] is shared by every settings switch, so these assertions
 * hold for all of them; the Paid Projects copy is used here because that is the
 * row this module owns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class PaidProjectsToggleAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val title = "Paid Projects"
    private val description = "Track fixed-price work, billing and payments."

    private fun render(
        checked: Boolean,
        enabled: Boolean = true,
        rtl: Boolean = false,
        onChange: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            ElmTrackrTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    SettingsToggleRow(
                        title = title,
                        description = description,
                        checked = checked,
                        onCheckedChange = onChange,
                        enabled = enabled,
                    )
                }
            }
        }
    }

    /** Nodes carrying a toggleable state — the accessible switches on screen. */
    private fun toggleNodes() = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        .fetchSemanticsNodes()

    @Test
    fun `the toggle is a single node, not a switch beside an unrelated label`() {
        render(checked = false)

        // One toggleable node, so a screen reader cannot land on a nameless
        // control with the title stranded elsewhere in the tree.
        assertEquals(1, toggleNodes().size)
    }

    @Test
    fun `the toggle carries its title as its accessible name`() {
        render(checked = false)

        composeRule.onNodeWithText(title).assertIsDisplayed()
        val node = toggleNodes().single()
        val spoken = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }

        assertTrue(
            "the toggle should be announced with its title, got $spoken",
            spoken?.contains(title) == true,
        )
    }

    @Test
    fun `the toggle reports the switch role`() {
        render(checked = false)

        assertEquals(Role.Switch, toggleNodes().single().config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun `the toggle reports its state as off`() {
        render(checked = false)

        composeRule.onNodeWithText(title).assertIsOff()
    }

    @Test
    fun `the toggle reports its state as on`() {
        render(checked = true)

        composeRule.onNodeWithText(title).assertIsOn()
        assertEquals(
            ToggleableState.On,
            toggleNodes().single().config.getOrNull(SemanticsProperties.ToggleableState),
        )
    }

    @Test
    fun `activating the toggle turns the feature on`() {
        var value: Boolean? = null
        render(checked = false, onChange = { value = it })

        composeRule.onNodeWithText(title).performClick()

        assertEquals(true, value)
    }

    @Test
    fun `activating an enabled toggle again turns the feature off`() {
        var value: Boolean? = null
        render(checked = true, onChange = { value = it })

        composeRule.onNodeWithText(title).performClick()

        assertEquals(false, value)
    }

    @Test
    fun `a disabled toggle does not fire`() {
        var fired = false
        render(checked = false, enabled = false, onChange = { fired = true })

        composeRule.onNodeWithText(title).performClick()

        assertEquals(false, fired)
    }

    @Test
    fun `the description is available alongside the title`() {
        // Turning the module on changes the navigation, so the explanation must
        // reach a screen reader too, not only the title.
        render(checked = false)

        val spoken = toggleNodes().single().config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }

        assertTrue("expected the description too, got $spoken", spoken?.contains("billing") == true)
    }

    @Test
    fun `the whole row is a usable touch target`() {
        render(checked = false)

        composeRule.onNodeWithText(title).assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h891dp")
    fun `the toggle keeps its semantics in a right-to-left layout`() {
        var value: Boolean? = null
        render(checked = false, rtl = true, onChange = { value = it })

        assertEquals(1, toggleNodes().size)
        composeRule.onNodeWithText(title).assertIsOff()
        composeRule.onNodeWithText(title).performClick()

        assertEquals(true, value)
    }
}
