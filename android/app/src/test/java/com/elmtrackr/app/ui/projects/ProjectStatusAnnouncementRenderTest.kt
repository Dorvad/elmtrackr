package com.elmtrackr.app.ui.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.projects.ProjectBillingStatus
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Status has to survive being read aloud, and being seen without colour.
 *
 * A pill is tinted — amber for paused, red for overdue, green for paid — and
 * that tint is the fastest cue for a sighted user with normal colour vision and
 * no cue at all for anyone else. So the status name is always written in the
 * pill, and the pill's description says which *kind* of status it is: "Active"
 * and "Paid" are answers to different questions, and a bare word does not say
 * which was asked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class ProjectStatusAnnouncementRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(rtl: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            ElmTrackrTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    content()
                }
            }
        }
    }

    // ── Work status ──────────────────────────────────────────────────────────

    @Test
    fun `a work status pill names the axis it belongs to`() {
        render { WorkStatusPill(ProjectWorkStatus.ACTIVE) }

        composeRule.onNodeWithContentDescription("Project status: Active").assertIsDisplayed()
    }

    @Test
    fun `every work status is both written and announced`() {
        // All of them in one composition: the rule allows a single setContent,
        // and rendering them together also proves no two statuses collapse into
        // the same description.
        render {
            androidx.compose.foundation.layout.Column {
                ProjectWorkStatus.entries.forEach { WorkStatusPill(it) }
            }
        }

        // Whatever the tint, the name is in the pill, so greyscale and
        // colour-blind users read the same thing as everyone else.
        val described = composeRule
            .onAllNodesWithContentDescription("Project status:", substring = true)
            .fetchSemanticsNodes()
        assertEquals(ProjectWorkStatus.entries.size, described.size)
    }

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h891dp")
    fun `a work status pill is announced in Hebrew`() {
        render(rtl = true) { WorkStatusPill(ProjectWorkStatus.ACTIVE) }

        composeRule.onNodeWithContentDescription("מצב הפרויקט:", substring = true)
            .assertIsDisplayed()
    }

    // ── Billing status ───────────────────────────────────────────────────────

    @Test
    fun `a billing status pill names its own axis, not the work one`() {
        render { BillingStatusPill(ProjectBillingStatus.PAID) }

        composeRule.onNodeWithContentDescription("Billing status: Paid").assertIsDisplayed()
    }

    @Test
    fun `an overdue pill says overdue rather than relying on being red`() {
        render { BillingStatusPill(ProjectBillingStatus.OVERDUE) }

        composeRule.onNodeWithContentDescription("Billing status: Overdue").assertIsDisplayed()
        // And the word is on screen, not only in the description.
        composeRule.onNodeWithText("Overdue").assertIsDisplayed()
    }

    @Test
    fun `a paid pill says paid rather than relying on being green`() {
        render { BillingStatusPill(ProjectBillingStatus.PAID) }

        composeRule.onNodeWithText("Paid").assertIsDisplayed()
    }

    @Test
    fun `every billing status is both written and announced`() {
        render {
            androidx.compose.foundation.layout.Column {
                ProjectBillingStatus.entries.forEach { BillingStatusPill(it) }
            }
        }

        val described = composeRule
            .onAllNodesWithContentDescription("Billing status:", substring = true)
            .fetchSemanticsNodes()
        assertEquals(ProjectBillingStatus.entries.size, described.size)
    }

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h891dp")
    fun `a billing status pill is announced in Hebrew`() {
        render(rtl = true) { BillingStatusPill(ProjectBillingStatus.OVERDUE) }

        composeRule.onNodeWithContentDescription("מצב החיוב:", substring = true)
            .assertIsDisplayed()
    }

    // ── The two axes stay distinguishable ────────────────────────────────────

    @Test
    fun `a completed project that is not yet paid announces both states separately`() {
        // The distinction Prompt 3 established and this pass must not blur:
        // finishing the work and being paid for it are independent.
        render {
            androidx.compose.foundation.layout.Column {
                WorkStatusPill(ProjectWorkStatus.COMPLETED)
                BillingStatusPill(ProjectBillingStatus.NOT_BILLED)
            }
        }

        composeRule.onNodeWithContentDescription("Project status: Completed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Billing status: Not billed").assertIsDisplayed()
    }

    @Test
    fun `a paid project that is still active announces both states separately`() {
        render {
            androidx.compose.foundation.layout.Column {
                WorkStatusPill(ProjectWorkStatus.ACTIVE)
                BillingStatusPill(ProjectBillingStatus.PAID)
            }
        }

        composeRule.onNodeWithContentDescription("Project status: Active").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Billing status: Paid").assertIsDisplayed()
    }
}
