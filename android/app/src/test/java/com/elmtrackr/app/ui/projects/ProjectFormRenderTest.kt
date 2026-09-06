package com.elmtrackr.app.ui.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.projects.ProjectFormInput
import com.elmtrackr.app.domain.text.BidiText
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The project form's shape, as the person filling it in meets it.
 *
 * Its own class rather than more cases in `ProjectsRenderTest`, because the
 * build gives each test class a fresh JVM (`forkEvery = 1`, see the note in
 * `build.gradle.kts`) and a full form render at this viewport is expensive
 * enough that adding four of them to a class already carrying twenty-two
 * exhausted its heap. Splitting is the remedy the fork setting was chosen for.
 *
 * Every assertion here is about *what is said once*. The form's problem was
 * never a missing control — it was six section cards, a currency field spread
 * over four stacked elements, a chosen amount mode repeated verbatim as the
 * label of the field below it, and a date row that printed its own name twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    qualifiers = "w411dp-h1800dp",
    application = ScreenshotTestApplication::class,
)
class ProjectFormRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Themed(rtl: Boolean = false, content: @Composable () -> Unit) {
        ElmTrackrTheme {
            if (rtl) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
            } else {
                content()
            }
        }
    }

    private fun render(input: ProjectFormInput, rtl: Boolean = false) {
        composeRule.setContent {
            Themed(rtl) {
                ProjectFormScreen(
                    existing = null,
                    initialInput = input,
                    isBilled = false,
                    isSaving = false,
                    onSave = {},
                    onBack = {},
                )
            }
        }
    }

    /**
     * The amount mode is offered once and stated once.
     *
     * The chosen mode used to be repeated, word for word, as the label of the
     * amount field directly beneath the control that set it. The field is
     * labelled by its currency instead; the breakdown names both amounts in full
     * once there is a figure to break down.
     */
    @Test
    fun `the amount entry choice is offered without repeating itself`() {
        render(ProjectFormInput(currencyCode = "USD"))

        composeRule.onNodeWithText("Which amount do you know?").assertExists()
        composeRule.onNodeWithText("Client total").assertExists()
        composeRule.onAllNodesWithText("Fee before tax").assertCountEquals(1)
        composeRule.onNodeWithText("Amount in " + BidiText.isolate("USD")).assertExists()
    }

    /**
     * The currency was a read-only row, a full-width "Change currency" button, an
     * error slot and a two-line note — four stacked elements for a field almost
     * nobody touches twice.
     */
    @Test
    fun `currency is one row that opens the picker`() {
        render(ProjectFormInput(currencyCode = "USD"))

        composeRule.onNodeWithText("Change currency").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Currency: USD").assertExists()
        // The dialog it opens is not rendered here, and not for want of trying:
        // it carries a lazy list of every ISO 4217 code and never reports idle
        // under Robolectric, whether opened from the form or composed alone.
        // That predates this change. Its one button now reads Close rather than
        // "Clear" — the date field's string, which sounded like emptying the
        // currency rather than leaving.
    }

    /**
     * Each date row printed its label twice: beside the value, and again as the
     * text button that opened the picker. A blank form read "Start date / Not
     * set … Start date", and the only thing that looked tappable was the copy
     * that said nothing about what tapping would do.
     */
    @Test
    fun `a date row states its label once and clears only when set`() {
        render(
            ProjectFormInput(
                currencyCode = "USD",
                startDate = java.time.LocalDate.of(2026, 9, 1),
            ),
        )

        composeRule.onAllNodesWithText("Start date").assertCountEquals(1)
        composeRule.onAllNodesWithText("Deadline").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Clear Start date").assertExists()
        composeRule.onNodeWithContentDescription("Clear Deadline").assertDoesNotExist()
    }

    /**
     * Six section cards became four: tax joined the price it modifies, the dates
     * joined the time targets they plan, and the description joined the notes
     * instead of sitting two cards away from them.
     */
    @Test
    fun `the form is four sections`() {
        render(ProjectFormInput(currencyCode = "USD"))

        listOf("Project", "Price", "Time and dates").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        // "Notes" titles the last card and labels the field inside it.
        composeRule.onAllNodesWithText("Notes").assertCountEquals(2)
        listOf("Tax", "Dates", "Amount").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    /** The layout mirrors; nothing in the new rows is pinned to the left. */
    @Test
    fun `the form renders right-to-left`() {
        render(
            ProjectFormInput(currencyCode = "ILS", startDate = java.time.LocalDate.of(2026, 9, 1)),
            rtl = true,
        )

        composeRule.onNodeWithContentDescription("Currency: ILS").assertExists()
        composeRule.onAllNodesWithText("Start date").assertCountEquals(1)
    }
}
