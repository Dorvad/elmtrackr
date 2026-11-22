package com.elmtrackr.app.ui.design

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The segmented control keeps one row while every label fits its equal share, and
 * stacks its options when one does not.
 *
 * It was single-line, equal-width and unconditional, so four options at phone width
 * cut "Multiplicative" and "Base-plus-premium" off mid-word, and "Fixed amount per
 * month" read "Fixed amount per". 296dp is the work-profile card's content width on a
 * 360dp phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp", application = ScreenshotTestApplication::class)
// Native graphics: the behaviour under test depends on real text measurement, which
// Robolectric's default (legacy) graphics mode does not perform.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElmSegmentedPillRowRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(options: List<String>, selected: Int = 0) {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ElmSegmentedPillRow(
                    options = options,
                    selectedIndex = selected,
                    onSelect = {},
                    modifier = Modifier.width(296.dp),
                )
            }
        }
    }

    private fun top(label: String): Float =
        composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    /**
     * Cut off means ellipsized: the segments use ellipsis overflow, so a label that
     * did not fit its line ends in "…". (`hasVisualOverflow` and `didOverflowWidth`
     * are not usable here: through the semantics action they compare the text with
     * its paragraph width and report overflow for text that fits.)
     */
    private fun overflows(label: String): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        val layout = results.first()
        return (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
    }

    @Test
    fun `short labels share one row`() {
        val options = listOf("Light", "Dark", "System")
        render(options)

        assertEquals("one row: the segments share a top edge", top("Light"), top("System"), 0.5f)
        options.forEach { assertFalse("$it is cut off", overflows(it)) }
    }

    @Test
    fun `labels too long for their share stack, uncut`() {
        val options = listOf("Highest only", "Additive", "Multiplicative", "Base-plus-premium")
        render(options, selected = 2)

        assertNotEquals("stacked: each option on its own line", top("Highest only"), top("Base-plus-premium"))
        options.forEach { assertFalse("$it is cut off", overflows(it)) }
        composeRule.onNodeWithText("Multiplicative").assertIsSelected()
    }

    @Test
    fun `a two-option row with one long label stacks`() {
        val options = listOf("Percentage", "Fixed amount per month")
        render(options)

        assertNotEquals(top("Percentage"), top("Fixed amount per month"))
        options.forEach { assertFalse("$it is cut off", overflows(it)) }
    }
}
