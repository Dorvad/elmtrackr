package com.elmtrackr.app.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.elmtrackr.app.ScreenshotTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rule that keeps the hours and the estimate from fighting over one row.
 *
 * The defect this exists for was found in a render, not by a test: at Android's
 * largest font size the month card's hours were squeezed to the width of one
 * character, because the two figures shared a weighted `Row` and the estimate's
 * label took the width. The existing large-font tests asserted `isDisplayed`,
 * which a figure laid out one letter per line satisfies.
 *
 * Blocks of fixed size stand in for the figures here, deliberately. Robolectric
 * does not measure text — every glyph comes out about a pixel wide — so a test
 * built on real labels cannot tell a healthy layout from a broken one. What can
 * be pinned exactly is the decision: two blocks that fit share a row, two that do
 * not each get their own, and neither is ever narrower than it asked to be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class AuroraFigurePairRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(startWidth: Dp, endWidth: Dp?, container: Dp = 300.dp, rtl: Boolean = false) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                AuroraFigurePair(
                    modifier = Modifier.width(container).testTag("pair"),
                    start = { Box(Modifier.size(startWidth, 40.dp).testTag("start")) },
                    end = endWidth?.let { width ->
                        { Box(Modifier.size(width, 24.dp).testTag("end")) }
                    },
                )
            }
        }
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `two figures that fit share a row, one at each edge`() {
        render(startWidth = 100.dp, endWidth = 150.dp)
        val pair = bounds("pair")
        val start = bounds("start")
        val end = bounds("end")

        assertEquals(pair.left, start.left)
        assertEquals(pair.right, end.right)
        assertTrue("the figures overlap", start.right <= end.left)
        // Bottom-aligned, so two numbers of different sizes sit on one baseline edge.
        assertEquals(start.bottom, end.bottom)
        assertEquals(40.dp, pair.height)
    }

    @Test
    fun `two figures that do not fit each get a row of their own`() {
        // 180 + 12 gap + 150 is wider than 300.
        render(startWidth = 180.dp, endWidth = 150.dp)
        val pair = bounds("pair")
        val start = bounds("start")
        val end = bounds("end")

        assertTrue("the second figure was not moved below the first", end.top >= start.bottom)
        assertEquals(pair.left, start.left)
        assertEquals(pair.right, end.right)
    }

    /** The failure itself: the first figure used to be handed whatever was left over. */
    @Test
    fun `neither figure is narrower than it asked to be`() {
        render(startWidth = 180.dp, endWidth = 290.dp)

        assertEquals(180.dp, bounds("start").width)
        assertEquals(290.dp, bounds("end").width)
    }

    @Test
    fun `figures that exactly fill the row still share it`() {
        // 138 + 12 gap + 150 = 300.
        render(startWidth = 138.dp, endWidth = 150.dp)

        assertEquals(bounds("start").bottom, bounds("end").bottom)
    }

    @Test
    fun `a lone figure is laid out on its own`() {
        render(startWidth = 100.dp, endWidth = null)
        val pair = bounds("pair")

        assertEquals(pair.left, bounds("start").left)
        assertEquals(40.dp, pair.height)
    }

    @Test
    fun `right to left swaps the edges, sharing a row and stacked alike`() {
        render(startWidth = 100.dp, endWidth = 150.dp, rtl = true)
        var pair = bounds("pair")
        assertEquals(pair.right, bounds("start").right)
        assertEquals(pair.left, bounds("end").left)
    }

    @Test
    fun `right to left stacked keeps each figure on its own edge`() {
        render(startWidth = 180.dp, endWidth = 150.dp, rtl = true)
        val pair = bounds("pair")
        assertEquals(pair.right, bounds("start").right)
        assertEquals(pair.left, bounds("end").left)
        assertTrue(bounds("end").top >= bounds("start").bottom)
    }
}
