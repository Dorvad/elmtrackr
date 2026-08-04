package com.elmtrackr.app.ui.design

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared read-only picker.
 *
 * Four settings screens had hand-rolled this recipe and all four exposed no
 * open/closed state, so a screen reader could not distinguish an open list from a
 * closed one — the chevron is the only on-screen indication and it carries no
 * description.
 *
 * The Role assertion is deliberately here rather than in the component: it records
 * that `Role.DropdownList` comes from Material's own `menuAnchor` and must not be
 * set again by hand. If a Material upgrade ever stops providing it, this fails and
 * says where to put it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class ElmDropdownFieldRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val themes = listOf("system", "light", "dark")

    private fun setContent(
        selected: String = "system",
        supportingText: String? = null,
        onSelect: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ElmDropdownField(
                    label = "Theme",
                    selected = selected,
                    options = themes,
                    onSelect = onSelect,
                    displayName = { it.replaceFirstChar(Char::uppercase) },
                    supportingText = supportingText,
                )
            }
        }
    }

    /**
     * Read by walking the config rather than indexing it: the indexed accessor
     * throws when a property is absent, and "absent" is exactly the failure these
     * tests exist to catch.
     */
    private fun anchorProperty(key: SemanticsPropertyKey<*>): Any? =
        // Matched on the role, not on its text: once the list is open the chosen
        // option also appears as a menu row, and matching by text finds both.
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .fetchSemanticsNode()
            .config
            .firstOrNull { it.key == key }
            ?.value

    private fun anchorState(): Any? = anchorProperty(SemanticsProperties.StateDescription)

    private fun anchorRole(): Any? = anchorProperty(SemanticsProperties.Role)

    @Test
    fun `the field shows the current choice and its label`() {
        setContent()

        composeRule.onNodeWithText("System").assertIsDisplayed()
        composeRule.onNodeWithText("Theme").assertIsDisplayed()
    }

    /** Material's own anchor supplies this; the component must not duplicate it. */
    @Test
    fun `the anchor reports itself as a dropdown list`() {
        setContent()

        assertEquals(Role.DropdownList, anchorRole())
    }

    /**
     * The gap this component closed. Material 1.3.1's anchor exposes OnClick and
     * nothing that says whether the list is open, so the state has to be attached.
     */
    @Test
    fun `the anchor announces whether the list is open`() {
        setContent()

        assertEquals("Collapsed", anchorState())

        composeRule.onNodeWithText("System").performClick()

        assertEquals("Expanded", anchorState())
    }

    @Test
    fun `choosing an option reports it and closes the list`() {
        var chosen: String? = null
        setContent(onSelect = { chosen = it })

        composeRule.onNodeWithText("System").performClick()
        composeRule.onNodeWithText("Dark").performClick()

        assertEquals("dark", chosen)
        assertEquals("Collapsed", anchorState())
    }

    @Test
    fun `supporting text is shown when given`() {
        setContent(supportingText = "Applies to every screen")

        composeRule.onNodeWithText("Applies to every screen").assertIsDisplayed()
    }
}
