package com.elmtrackr.app.ui.tasks

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Getting rid of a task.
 *
 * Delete existed, and was tested at the repository, and could not be reached:
 * the only button that called it lived inside the Archived card, which is
 * collapsed and does not appear at all until something has been archived. So
 * removing a task meant opening it, archiving it, closing the sheet, finding a
 * card that had just appeared, expanding it and deleting from there. Anyone who
 * did not guess that chain concluded that tasks cannot be deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1200dp", application = ScreenshotTestApplication::class)
class TaskEditorRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun task(id: String = "t1") = Task(
        id = id,
        userId = "u1",
        name = "Driving",
        icon = "🚗",
        color = null,
        hourlyRate = 60.0,
        isArchived = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun render(
        tasks: List<Task>,
        onDelete: (String) -> Unit = {},
        onArchive: (String) -> Unit = {},
        onSave: (rate: Double) -> Unit = {},
    ) {
        composeRule.setContent {
            ElmTrackrTheme {
                TaskManagementContent(
                    state = TaskManagementUiState.Ready(tasks = tasks),
                    onBack = {},
                    onSave = { _, _, _, _, rate -> onSave(rate) },
                    onArchive = onArchive,
                    onRestore = {},
                    onDelete = onDelete,
                    onSelectProfile = {},
                    onDismissMessage = {},
                )
            }
        }
    }

    /** Open a task, and both ways out of it are there. */
    @Test
    fun `the editor offers archive and delete`() {
        render(listOf(task()))

        composeRule.onNodeWithText("Driving").performClick()

        composeRule.onNodeWithText("Archive this task").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    /**
     * Delete from the editor does not delete on the spot; it opens the same
     * confirmation the archived list uses.
     *
     * The dialog itself is not asserted here. It and the editor sheet are two
     * windows over one another, and Compose's test tree does not reach the one
     * behind — the same limitation the project form's currency picker hits. What
     * is asserted is the half that matters for safety: one tap removes nothing.
     * The confirmation's own wiring is covered below, where no sheet is open.
     */
    @Test
    fun `delete from the editor does not act on one tap`() {
        var deleted: String? = null
        render(listOf(task()), onDelete = { deleted = it })

        composeRule.onNodeWithText("Driving").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(null, deleted)
    }

    /**
     * The confirmation, end to end, through the archived list — the one place a
     * delete could be reached before this change, and the only one where the
     * dialog composes without a sheet in front of it.
     */
    @Test
    fun `confirming the dialog deletes, cancelling does not`() {
        var deleted: String? = null
        render(listOf(task().copy(isArchived = true)), onDelete = { deleted = it })

        composeRule.onNodeWithText("Archived").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(null, deleted)

        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onAllNodesWithText("Delete").onLast().performClick()

        assertEquals("t1", deleted)
    }

    /**
     * A rate typed with a decimal comma is the rate that was typed.
     *
     * The field kept digits and `.` only, so the comma — the separator key on
     * Hebrew, Russian and most European keyboards — was dropped and "62,5" was
     * saved as 625: ten times the pay on every shift booked to the task, with a
     * success message. Onboarding and the work-profile form already read their
     * rates through `DecimalInput`; this field had been left on its own filter.
     */
    @Test
    fun `a rate typed with a decimal comma is saved as typed`() {
        var saved: Double? = null
        render(listOf(task()), onSave = { saved = it })

        composeRule.onNodeWithText("Driving").performClick()
        composeRule.onNodeWithText("Hourly rate").performTextReplacement("62,5")
        saveFromTheSheet()

        assertEquals(62.5, saved)
    }

    /** Arabic-Indic digits and the Arabic decimal separator, as an Arabic keyboard types them. */
    @Test
    fun `a rate typed in Arabic-Indic digits is saved as its value`() {
        var saved: Double? = null
        render(listOf(task()), onSave = { saved = it })

        composeRule.onNodeWithText("Driving").performClick()
        composeRule.onNodeWithText("Hourly rate").performTextReplacement("٦٢٫٥")
        saveFromTheSheet()

        assertEquals(62.5, saved)
    }

    /**
     * Presses Save through its semantics action rather than a touch.
     *
     * The editor is a modal sheet, which is a window of its own, and under
     * Robolectric an injected touch is delivered to the activity's window behind
     * it — the tap lands nowhere and the test would fail for a reason that has
     * nothing to do with the field. The action invokes the same click handler.
     */
    private fun saveFromTheSheet() {
        composeRule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** A new task has nothing to archive or delete. */
    @Test
    fun `a new task offers neither archive nor delete`() {
        render(emptyList())

        composeRule.onNodeWithText("New task").performClick()

        composeRule.onNodeWithText("Archive this task").assertDoesNotExist()
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
    }
}
