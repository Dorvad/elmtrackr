package com.elmtrackr.app.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The task selector's height must not grow with the number of tasks.
 *
 * It used to be a FlowRow, which added a row of chips per few tasks. It sits
 * directly below the clock on the dashboard — the most valuable space in the app —
 * so a user with ten tasks pushed the month summary and recent shifts off the
 * first screen entirely. One scrolling line costs the same whether there are three
 * tasks or thirty.
 *
 * Height here is structural — rows of chips — rather than text-width driven, so it
 * is measurable in this harness, unlike the label/value crowding cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class TaskSelectorBarRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var taskCount by mutableIntStateOf(3)

    private fun tasks(count: Int) = List(count) { i ->
        Task(
            id = "t$i",
            userId = "u1",
            name = "Task number $i",
            icon = "🛠",
            hourlyRate = 50.0,
        )
    }

    private fun setBar() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Column(Modifier.fillMaxWidth()) {
                    TaskSelectorBar(
                        tasks = tasks(taskCount),
                        selectedTaskId = null,
                        suggestedTaskId = null,
                        showSuggestedNow = false,
                        suggestionExplanation = null,
                        onSelectTask = {},
                        onManageTasks = {},
                    )
                }
            }
        }
    }

    private fun height(): Float = composeRule.onRoot().getUnclippedBoundsInRoot()
        .let { it.bottom.value - it.top.value }

    @Test
    fun `adding tasks does not make the selector taller`() {
        taskCount = 3
        setBar()
        val withThree = height()

        taskCount = 24
        composeRule.waitForIdle()
        val withTwentyFour = height()

        assertEquals(
            "3 tasks -> $withThree dp, 24 tasks -> $withTwentyFour dp",
            withThree,
            withTwentyFour,
            0.5f,
        )
    }

    /**
     * The reason for LazyRow over Row + horizontalScroll: a user with thirty tasks
     * should not lay out thirty chips to see the four that fit.
     */
    @Test
    fun `chips far off screen are not composed`() {
        taskCount = 30
        setBar()

        val composed = (0 until 30).count { i ->
            composeRule.onAllNodes(hasText("Task number $i", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue("composed $composed of 30 chips", composed < 30)
        composeRule.onAllNodes(hasText("Task number 0", substring = true))
            .fetchSemanticsNodes()
            .let { assertTrue("the first chip should be laid out", it.isNotEmpty()) }
    }

    @Test
    fun `the manage action stays reachable`() {
        taskCount = 24
        setBar()

        composeRule.onAllNodes(hasText("Manage", substring = true))
            .fetchSemanticsNodes()
            .let { assertTrue("manage action missing", it.isNotEmpty()) }
    }

    @Test
    fun `an empty task list renders nothing`() {
        taskCount = 0
        setBar()

        assertEquals(0f, height(), 0.5f)
    }

    @Test
    fun `a selected task still shows its summary`() {
        taskCount = 5
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Column(Modifier.fillMaxWidth()) {
                    TaskSelectorBar(
                        tasks = tasks(5),
                        selectedTaskId = "t2",
                        suggestedTaskId = null,
                        showSuggestedNow = false,
                        suggestionExplanation = null,
                        onSelectTask = {},
                        onManageTasks = {},
                    )
                }
            }
        }

        composeRule.onAllNodes(hasText("Task number 2", substring = true))
            .fetchSemanticsNodes()
            .let { assertTrue("selected summary missing", it.isNotEmpty()) }
    }
}
