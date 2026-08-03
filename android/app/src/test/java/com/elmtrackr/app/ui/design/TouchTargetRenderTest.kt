package com.elmtrackr.app.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.ui.tasks.TaskColorDot
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Interactive controls must be reachable.
 *
 * Measured with assertHeightIsAtLeast rather than a touch-bounds assertion,
 * because each fix grows the node's own layout size rather than only extending
 * its input area — the visual stays put inside a larger box.
 *
 * These all sat below the 48dp minimum: the segmented-pill options and
 * choice chips at a 40dp floor, and the task colour swatch at the size of its
 * 28dp circle. In each case the fix keeps the original visual and grows only
 * the target, so the screens look unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class TouchTargetRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val minimum = 48.dp

    @Test
    fun `segmented pill options are at least 48dp tall`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ElmSegmentedPillRow(
                    options = listOf("Hourly", "Project"),
                    selectedIndex = 0,
                    onSelect = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        composeRule.onNodeWithText("Hourly").assertHeightIsAtLeast(minimum)
        composeRule.onNodeWithText("Project").assertHeightIsAtLeast(minimum)
    }

    @Test
    fun `choice chips are at least 48dp tall`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ElmChoiceChip(selected = false, onClick = {}) { Text("Acme redesign") }
            }
        }

        composeRule.onNodeWithText("Acme redesign").assertHeightIsAtLeast(minimum)
    }

    @Test
    fun `a clickable task colour swatch is at least 48dp tall`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Column {
                    TaskColorDot(
                        color = Color.Red,
                        selected = false,
                        contentDescription = "Colour",
                        onClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Colour").assertHeightIsAtLeast(minimum)
    }
}
