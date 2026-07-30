package com.elmtrackr.app.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.ProjectClockInOptions
import com.elmtrackr.app.domain.text.BidiText
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Renders the project-time clock-in controls and the active-project panel.
 *
 * The assertions are about what the user is told: that project time is not paid
 * as wages, which project the hours go to, and that a countdown only appears when
 * they set a target to count down to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    qualifiers = "w411dp-h1800dp",
    application = ScreenshotTestApplication::class,
)
class ProjectTimeRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** See ProjectsRenderTest for why the default 60s budget is not enough. */
    @org.junit.Before
    fun setIdlingBudget() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(
            IDLING_BUDGET_SECONDS,
            java.util.concurrent.TimeUnit.SECONDS,
        )
        androidx.test.espresso.IdlingPolicies.setIdlingResourceTimeout(
            IDLING_BUDGET_SECONDS,
            java.util.concurrent.TimeUnit.SECONDS,
        )
    }

    private val IDLING_BUDGET_SECONDS = 240L

    private val now: Instant = Instant.parse("2026-07-29T12:00:00Z")

    private fun project(
        id: String = "p1",
        name: String = "Website rebuild",
        client: String? = "Acme Ltd",
        budgetMinutes: Int? = null,
        targetRate: String? = null,
    ) = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = client,
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        hourBudgetMinutes = budgetMinutes,
        targetHourlyRate = targetRate?.let { Money.of(it, "USD") },
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun activeShift() = Shift(
        id = "active",
        userId = "u1",
        startTime = Instant.now().minus(120, ChronoUnit.MINUTES),
        endTime = null,
        compensationSource = CompensationSource.PROJECT,
        projectId = "p1",
        projectNameSnapshot = "Website rebuild",
    )

    private fun bankedShift() = Shift(
        id = "banked",
        userId = "u1",
        startTime = now.minus(180, ChronoUnit.MINUTES),
        endTime = now.minus(60, ChronoUnit.MINUTES),
        compensationSource = CompensationSource.PROJECT,
        projectId = "p1",
    )

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

    // ── Clock-in selector ─────────────────────────────────────────────────────

    @Test
    fun `the selector explains that project time is not paid as wages`() {
        render {
            ProjectTimeSelector(
                options = ProjectClockInOptions.options(listOf(project())),
                selectedSource = CompensationSource.PROJECT,
                selectedProjectId = "p1",
                note = "",
                onSelectSource = {},
                onSelectProject = {},
                onNoteChange = {},
            )
        }

        composeRule.onNodeWithText("Project time", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Not paid as wages and not counted as overtime.",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `hourly work is described as counting towards overtime`() {
        render {
            ProjectTimeSelector(
                options = ProjectClockInOptions.options(listOf(project())),
                selectedSource = CompensationSource.EMPLOYEE,
                selectedProjectId = null,
                note = "",
                onSelectSource = {},
                onSelectProject = {},
                onNoteChange = {},
            )
        }

        composeRule.onNodeWithText("Counts towards overtime", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a project chip shows its client and currency`() {
        render {
            ProjectTimeSelector(
                options = ProjectClockInOptions.options(listOf(project())),
                selectedSource = CompensationSource.PROJECT,
                selectedProjectId = "p1",
                note = "",
                onSelectSource = {},
                onSelectProject = {},
                onNoteChange = {},
            )
        }

        composeRule.onNodeWithText("Website rebuild").assertIsDisplayed()
        composeRule.onNodeWithText(
            "${BidiText.isolate("Acme Ltd")} · ${BidiText.isolate("USD")}",
        ).assertIsDisplayed()
    }

    @Test
    fun `no project picker is shown while hourly work is selected`() {
        render {
            ProjectTimeSelector(
                options = ProjectClockInOptions.options(listOf(project())),
                selectedSource = CompensationSource.EMPLOYEE,
                selectedProjectId = null,
                note = "",
                onSelectSource = {},
                onSelectProject = {},
                onNoteChange = {},
            )
        }

        assertEquals(0, composeRule.onAllNodesWithText("Website rebuild").fetchSemanticsNodes().size)
    }

    @Test
    fun `picking a project reports the selection`() {
        var picked: String? = null
        render {
            ProjectTimeSelector(
                options = ProjectClockInOptions.options(listOf(project(), project("p2", "Mobile app"))),
                selectedSource = CompensationSource.PROJECT,
                selectedProjectId = "p1",
                note = "",
                onSelectSource = {},
                onSelectProject = { picked = it },
                onNoteChange = {},
            )
        }

        composeRule.onNodeWithText("Mobile app").performClick()
        assertEquals("p2", picked)
    }

    @Test
    fun `the selector renders right-to-left`() {
        render(rtl = true) {
            ProjectTimeSelector(
                options = ProjectClockInOptions.options(listOf(project())),
                selectedSource = CompensationSource.PROJECT,
                selectedProjectId = "p1",
                note = "",
                onSelectSource = {},
                onSelectProject = {},
                onNoteChange = {},
            )
        }

        composeRule.onNodeWithText("Website rebuild").assertIsDisplayed()
    }

    // ── Active project shift ──────────────────────────────────────────────────

    @Test
    fun `the active card names the project and says the time is not wages`() {
        render {
            ActiveProjectShiftCard(
                project = project(),
                activeShift = activeShift(),
                projectShifts = listOf(bankedShift(), activeShift()),
                featureDisabled = false,
            )
        }

        composeRule.onNodeWithText("Tracking ${BidiText.isolate("Website rebuild")}")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(
            "Not paid as wages",
            substring = true,
        ).onFirst().assertIsDisplayed()
    }

    @Test
    fun `a project with no banked hours reads not enough hours instead of zero`() {
        render {
            ActiveProjectShiftCard(
                project = project(),
                activeShift = activeShift(),
                projectShifts = listOf(activeShift()),
                featureDisabled = false,
            )
        }

        // Zero would read as "you are earning nothing", which is not what an
        // untracked project means.
        composeRule.onNodeWithText("Not enough hours yet").assertIsDisplayed()
        composeRule.onNodeWithText("Rate if you stop now").assertIsDisplayed()
    }

    @Test
    fun `no target rate means no countdown on screen`() {
        render {
            ActiveProjectShiftCard(
                project = project(),
                activeShift = activeShift(),
                projectShifts = listOf(bankedShift(), activeShift()),
                featureDisabled = false,
            )
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithText("before you drop below", substring = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `a target rate produces a countdown`() {
        render {
            ActiveProjectShiftCard(
                project = project(targetRate = "100"),
                activeShift = activeShift(),
                projectShifts = listOf(bankedShift(), activeShift()),
                featureDisabled = false,
            )
        }

        composeRule.onNodeWithText("before you drop below", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an hour budget is reported as time left`() {
        render {
            ActiveProjectShiftCard(
                project = project(budgetMinutes = 600),
                activeShift = activeShift(),
                projectShifts = listOf(bankedShift(), activeShift()),
                featureDisabled = false,
            )
        }

        composeRule.onNodeWithText("of budget left", substring = true).assertIsDisplayed()
    }

    @Test
    fun `going past the budget is reported as over budget`() {
        render {
            ActiveProjectShiftCard(
                project = project(budgetMinutes = 60),
                activeShift = activeShift(),
                projectShifts = listOf(bankedShift(), activeShift()),
                featureDisabled = false,
            )
        }

        composeRule.onNodeWithText("over budget", substring = true).assertIsDisplayed()
    }

    @Test
    fun `switching the feature off mid-shift says the shift is kept`() {
        render {
            ActiveProjectShiftCard(
                project = project(),
                activeShift = activeShift(),
                projectShifts = listOf(activeShift()),
                featureDisabled = true,
            )
        }

        composeRule.onAllNodesWithText(
            "Clock out as usual",
            substring = true,
        ).onFirst().assertIsDisplayed()
    }

    @Test
    fun `the active card renders right-to-left`() {
        render(rtl = true) {
            ActiveProjectShiftCard(
                project = project(),
                activeShift = activeShift(),
                projectShifts = listOf(bankedShift(), activeShift()),
                featureDisabled = false,
            )
        }

        composeRule.onNodeWithText("Tracking ${BidiText.isolate("Website rebuild")}")
            .assertIsDisplayed()
    }

    // ── Feature-disabled notice ───────────────────────────────────────────────

    @Test
    fun `the disabled notice names the project when it is known`() {
        render { DisabledProjectShiftNotice(projectName = "Website rebuild") }

        composeRule.onNodeWithText("Website rebuild", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Clock out as usual", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the disabled notice still reassures when the project is unknown`() {
        render { DisabledProjectShiftNotice(projectName = null) }

        composeRule.onNodeWithText(
            "it will not become paid hours",
            substring = true,
        ).assertIsDisplayed()
    }

    // ── Post-shift summary ────────────────────────────────────────────────────

    @Test
    fun `the summary reports the hours added and the resulting rate`() {
        render {
            ProjectShiftSummaryCard(
                summary = DashboardViewModel.ProjectShiftSummary(
                    projectId = "p1",
                    projectName = "Website rebuild",
                    addedMinutes = 150,
                    effectiveHourlyRate = Money.of("2500", "USD"),
                ),
                onDismiss = {},
                onViewProject = {},
            )
        }

        composeRule.onNodeWithText(
            "added to ${BidiText.isolate("Website rebuild")}",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Effective rate is now", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the summary says there is no rate yet rather than showing zero`() {
        render {
            ProjectShiftSummaryCard(
                summary = DashboardViewModel.ProjectShiftSummary(
                    projectId = "p1",
                    projectName = "Website rebuild",
                    addedMinutes = 30,
                    effectiveHourlyRate = null,
                ),
                onDismiss = {},
                onViewProject = {},
            )
        }

        composeRule.onNodeWithText("No rate yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the summary can be dismissed and can open the project`() {
        var dismissed = false
        var opened = false
        render {
            ProjectShiftSummaryCard(
                summary = DashboardViewModel.ProjectShiftSummary(
                    projectId = "p1",
                    projectName = "Website rebuild",
                    addedMinutes = 30,
                    effectiveHourlyRate = Money.of("2500", "USD"),
                ),
                onDismiss = { dismissed = true },
                onViewProject = { opened = true },
            )
        }

        composeRule.onNodeWithText("View project").performClick()
        composeRule.onNodeWithText("Dismiss").performClick()

        assertEquals(true, opened)
        assertEquals(true, dismissed)
    }
}
