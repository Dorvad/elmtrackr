package com.elmtrackr.app.ui.projects

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.ui.design.LocalReduceMotion
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Projects tab guide, as a first-time user meets it.
 *
 * Rendered with reduce-motion on, which is what makes these deterministic: the
 * demo clock holds at its end state instead of looping, so nothing in the tree
 * animates forever and the test framework can reach idle. That is not a test-only
 * concession — it is the same path a user with reduce-motion enabled gets, and
 * these assertions are the check that the guide still explains itself when
 * nothing is moving.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1800dp", application = ScreenshotTestApplication::class)
class ProjectsGuideRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val pageTitles = listOf(
        "Start with the price you agreed",
        "Clock in the way you always do",
        "See what the price really pays",
        "Bill it, then track what lands",
    )

    private var finished = 0
    private var created = 0

    private fun render() {
        composeRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                ElmTrackrTheme {
                    ProjectsGuide(
                        onFinish = { finished++ },
                        onCreateProject = { created++ },
                    )
                }
            }
        }
    }

    private fun next() = composeRule.onNodeWithText("Next").performClick()

    @Test
    fun `opens on setting a project up, and says where in the guide the reader is`() {
        render()

        composeRule.onNodeWithText(pageTitles[0]).assertIsDisplayed()
        composeRule.onNodeWithText("Step 1 of 4").assertIsDisplayed()
    }

    /**
     * The four questions a first-time user has, in the order the work happens.
     * Named here so removing or reordering a page is a decision someone makes on
     * purpose rather than a silent edit to a list.
     */
    @Test
    fun `walks setup, tracking, the rate and billing in that order`() {
        render()

        pageTitles.forEachIndexed { index, title ->
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText("Step ${index + 1} of 4").assertIsDisplayed()
            if (index < pageTitles.lastIndex) next()
        }
    }

    /** Back walks the pages in reverse rather than leaving the guide. */
    @Test
    fun `back returns to the previous page`() {
        render()
        next()

        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithText(pageTitles[0]).assertIsDisplayed()
        assertEquals(0, finished)
    }

    /**
     * The two things the guide has to teach that a screenshot cannot: that
     * project time is a mode of the ordinary clock-in, and that the effective
     * rate falls as hours mount. Both are stated in words as well as drawn, so
     * a reader with motion off still gets them.
     */
    @Test
    fun `tracking says it is the ordinary clock-in, not a second timer`() {
        render()
        next()

        composeRule
            .onNodeWithText("Project hours are not a separate timer", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("never counts toward your wages or overtime", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `the rate page says the rate falls as the job runs long`() {
        render()
        next(); next()

        composeRule
            .onNodeWithText("the longer the job takes, the lower the rate goes", substring = true)
            .assertIsDisplayed()
    }

    /** Each demo is a canvas, so its explanation has to reach a screen reader. */
    @Test
    fun `every demo is described for a screen reader`() {
        render()

        listOf(
            "A project card filling in",
            "The dashboard toggle sliding",
            "A fixed fee bar above a row of hours",
            "A billed amount locking in place",
        ).forEachIndexed { index, description ->
            composeRule
                .onNodeWithContentDescription(description, substring = true)
                .assertIsDisplayed()
            if (index < 3) next()
        }
    }

    /** The last page ends on the action it has been describing. */
    @Test
    fun `finishing offers the first project and closes the guide`() {
        render()
        repeat(3) { next() }

        composeRule.onNodeWithText("Create your first project").performClick()

        assertEquals(1, created)
        // Closed before the form opens, so backing out of the form lands on the
        // list rather than back in the guide.
        assertEquals(1, finished)
    }

    @Test
    fun `done closes the guide without starting a project`() {
        render()
        repeat(3) { next() }

        composeRule.onNodeWithText("Done").performClick()

        assertEquals(1, finished)
        assertEquals(0, created)
    }

    /** One dismiss control, labelled, and it works from the first page. */
    @Test
    fun `skip closes the guide from the first page`() {
        render()

        composeRule.onNodeWithText("Skip").performClick()

        assertEquals(1, finished)
        assertEquals(0, created)
    }
}
