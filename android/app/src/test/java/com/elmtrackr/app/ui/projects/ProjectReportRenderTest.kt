package com.elmtrackr.app.ui.projects

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
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyByCurrency
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.ProjectDashboardSummary
import com.elmtrackr.app.domain.projects.ProjectHealthResolver
import com.elmtrackr.app.domain.projects.ProjectMetrics
import com.elmtrackr.app.domain.projects.ProjectReport
import com.elmtrackr.app.domain.projects.ProjectReportBuilder
import com.elmtrackr.app.domain.projects.ProjectReportFilter
import com.elmtrackr.app.domain.projects.ProjectTimeSummary
import com.elmtrackr.app.ui.dashboard.ProjectDashboardCard
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Renders the report and dashboard surfaces.
 *
 * What is asserted is the honesty of the presentation: two currencies appear as
 * two labelled rows rather than one total, the efficiency figure is explained as
 * not being profit, and the dashboard says why hourly earnings and project cash
 * are not added together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    qualifiers = "w411dp-h2400dp",
    application = ScreenshotTestApplication::class,
)
class ProjectReportRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** See ProjectsRenderTest for why the default 60s budget is not enough. */
    @org.junit.Before
    fun setIdlingBudget() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(
            240L,
            java.util.concurrent.TimeUnit.SECONDS,
        )
        androidx.test.espresso.IdlingPolicies.setIdlingResourceTimeout(
            240L,
            java.util.concurrent.TimeUnit.SECONDS,
        )
    }

    private val today = LocalDate.of(2026, 7, 29)
    private val from = LocalDate.of(2026, 7, 1)
    private val to = LocalDate.of(2026, 7, 31)

    private fun project(
        id: String = "p1",
        name: String = "Website rebuild",
        client: String? = "Acme Ltd",
        currency: String = "USD",
        entered: String = "10000",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        budgetMinutes: Int? = null,
        targetRate: String? = null,
    ) = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = client,
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        hourBudgetMinutes = budgetMinutes,
        targetHourlyRate = targetRate?.let { Money.of(it, currency) },
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun record(projectId: String = "p1", currency: String = "USD") = ProjectBillingRecord(
        id = "b-$projectId",
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal("10000"), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        billedOn = from.plusDays(2),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(projectId: String = "p1", amount: String, currency: String = "USD") =
        ProjectPayment(
            id = "pay-$projectId",
            userId = "u1",
            projectId = projectId,
            billingRecordId = "b-$projectId",
            paidOn = from.plusDays(9),
            amount = Money.of(amount, currency),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun report(
        projects: List<Project>,
        records: List<ProjectBillingRecord> = emptyList(),
        payments: List<ProjectPayment> = emptyList(),
        minutes: Map<String, Int> = emptyMap(),
        filter: ProjectReportFilter = ProjectReportFilter(from = from, to = to),
    ): ProjectReport {
        val summaries = projects.map { p ->
            ProjectMetrics.summarize(
                project = p,
                shifts = emptyList(),
                billingRecords = records,
                payments = payments,
                today = today,
                timeSummary = ProjectTimeSummary(minutes[p.id] ?: 0, 1),
            )
        }
        return ProjectReportBuilder.build(
            summaries = summaries,
            billingRecords = records,
            payments = payments,
            filter = filter,
            projectMinutes = minutes,
            activeDays = 3,
            today = today,
        )
    }

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

    // ── Report sections ───────────────────────────────────────────────────────

    @Test
    fun `the time section reports hours, active days and average`() {
        render {
            ProjectReportSection(
                report = report(listOf(project()), minutes = mapOf("p1" to 600)),
                onExportCsv = {},
            )
        }

        composeRule.onAllNodesWithText("Tracked hours").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Active days").assertIsDisplayed()
        composeRule.onNodeWithText("Average per project").assertIsDisplayed()
    }

    @Test
    fun `the value section keeps the accounting bases as separate lines`() {
        render {
            ProjectReportSection(
                report = report(
                    listOf(project()),
                    records = listOf(record()),
                    payments = listOf(payment(amount = "5000.00")),
                ),
                onExportCsv = {},
            )
        }

        composeRule.onAllNodesWithText("Contracted base fees").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Billed").assertIsDisplayed()
        composeRule.onNodeWithText("Cash received").assertIsDisplayed()
        composeRule.onAllNodesWithText("Outstanding").onFirst().assertIsDisplayed()
    }

    @Test
    fun `tax collected is labelled apart from revenue`() {
        render {
            ProjectReportSection(
                report = report(
                    listOf(project()),
                    records = listOf(record()),
                    payments = listOf(payment(amount = "11800.00")),
                ),
                onExportCsv = {},
            )
        }
        composeRule.onAllNodesWithText("Tax collected").onFirst().assertIsDisplayed()
    }

    @Test
    fun `two currencies render as two labelled rows, not one total`() {
        render {
            ProjectReportSection(
                report = report(
                    listOf(
                        project("p1", currency = "USD", entered = "10000"),
                        project("p2", name = "Mobile app", currency = "ILS", entered = "20000"),
                    ),
                ),
                onExportCsv = {},
            )
        }

        // Each currency gets its own row, labelled with its code.
        composeRule.onNodeWithText("Contracted base fees · USD").assertIsDisplayed()
        composeRule.onNodeWithText("Contracted base fees · ILS").assertIsDisplayed()
        // And the reason is stated on screen.
        composeRule.onNodeWithText(
            "does not convert between currencies",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `a single currency renders one unlabelled row`() {
        render {
            ProjectReportSection(report = report(listOf(project())), onExportCsv = {})
        }

        composeRule.onAllNodesWithText("Contracted base fees").onFirst().assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Contracted base fees · USD").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `the efficiency section says it is not net profit`() {
        render {
            ProjectReportSection(
                report = report(listOf(project()), minutes = mapOf("p1" to 600)),
                onExportCsv = {},
            )
        }

        composeRule.onAllNodesWithText("Project value per hour").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("this is not net profit", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an empty report says so rather than showing zeroes`() {
        render {
            ProjectReportSection(
                report = report(emptyList()),
                onExportCsv = {},
            )
        }
        composeRule.onNodeWithText("No projects match these filters.").assertIsDisplayed()
    }

    @Test
    fun `the report renders right-to-left`() {
        render(rtl = true) {
            ProjectReportSection(
                report = report(listOf(project(name = "בניית אתר מחדש")), minutes = mapOf("p1" to 600)),
                onExportCsv = {},
            )
        }
        composeRule.onNodeWithText("בניית אתר מחדש").assertIsDisplayed()
    }

    // ── Health indicators ─────────────────────────────────────────────────────

    @Test
    fun `an over-budget project explains why the indicator appeared`() {
        val insights = report(
            listOf(project(budgetMinutes = 300)),
            minutes = mapOf("p1" to 600),
        ).projects.single()

        render { ProjectHealthChips(ProjectHealthResolver.flagsFor(insights)) }

        composeRule.onNodeWithText("Over hour budget").assertIsDisplayed()
        composeRule.onNodeWithText("past the hour budget", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a below-target rate explains the shortfall without a minus sign`() {
        val insights = report(
            listOf(project(targetRate = "1500")),
            minutes = mapOf("p1" to 600),
        ).projects.single()

        render { ProjectHealthChips(ProjectHealthResolver.flagsFor(insights)) }

        composeRule.onNodeWithText("Effective rate below target").assertIsDisplayed()
        composeRule.onNodeWithText("below your target", substring = true).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("-$", substring = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `a healthy project reads on track with its reason`() {
        val insights = report(listOf(project()), minutes = mapOf("p1" to 60)).projects.single()

        render { ProjectHealthChips(ProjectHealthResolver.flagsFor(insights)) }

        composeRule.onNodeWithText("On track").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing needs attention.").assertIsDisplayed()
    }

    @Test
    fun `a completed but unbilled project is flagged and explained`() {
        val insights = report(
            listOf(project(status = ProjectWorkStatus.COMPLETED)),
        ).projects.single()

        render { ProjectHealthChips(ProjectHealthResolver.flagsFor(insights)) }

        composeRule.onNodeWithText("Completed but not billed").assertIsDisplayed()
        composeRule.onNodeWithText(
            "The work is finished and nothing has been billed.",
        ).assertIsDisplayed()
    }

    // ── Dashboard card ────────────────────────────────────────────────────────

    @Test
    fun `the dashboard card explains why the two bases are not added together`() {
        render {
            ProjectDashboardCard(
                summary = ProjectDashboardSummary(
                    activeProjectCount = 2,
                    unbilledProjectCount = 1,
                    outstanding = MoneyByCurrency.of(Money.of("6800.00", "USD")),
                    overdue = MoneyByCurrency.EMPTY,
                    receivedThisMonth = MoneyByCurrency.of(Money.of("11800.00", "USD")),
                    receivedTaxThisMonth = MoneyByCurrency.of(Money.of("1800.00", "USD")),
                    needsAttentionCount = 0,
                ),
                onOpenProjects = {},
            )
        }

        composeRule.onNodeWithText("2 active").assertIsDisplayed()
        composeRule.onNodeWithText("1 not billed").assertIsDisplayed()
        composeRule.onNodeWithText("Project payments received").assertIsDisplayed()
        composeRule.onNodeWithText("Tax collected").assertIsDisplayed()
        composeRule.onNodeWithText("Project revenue before tax").assertIsDisplayed()
        composeRule.onNodeWithText(
            "They are not added together because they measure different things.",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `the dashboard card shows nothing at all when there is nothing to say`() {
        render {
            ProjectDashboardCard(
                summary = ProjectDashboardSummary(
                    activeProjectCount = 0,
                    unbilledProjectCount = 0,
                    outstanding = MoneyByCurrency.EMPTY,
                    overdue = MoneyByCurrency.EMPTY,
                    receivedThisMonth = MoneyByCurrency.EMPTY,
                    receivedTaxThisMonth = MoneyByCurrency.EMPTY,
                    needsAttentionCount = 0,
                ),
                onOpenProjects = {},
            )
        }

        assertEquals(0, composeRule.onAllNodesWithText("Projects").fetchSemanticsNodes().size)
    }

    @Test
    fun `the dashboard card splits two currencies into labelled rows`() {
        render {
            ProjectDashboardCard(
                summary = ProjectDashboardSummary(
                    activeProjectCount = 2,
                    unbilledProjectCount = 0,
                    outstanding = MoneyByCurrency.from(
                        listOf(Money.of("100.00", "USD"), Money.of("200.00", "ILS")),
                    ),
                    overdue = MoneyByCurrency.EMPTY,
                    receivedThisMonth = MoneyByCurrency.EMPTY,
                    receivedTaxThisMonth = MoneyByCurrency.EMPTY,
                    needsAttentionCount = 0,
                ),
                onOpenProjects = {},
            )
        }

        composeRule.onNodeWithText("Outstanding · USD").assertIsDisplayed()
        composeRule.onNodeWithText("Outstanding · ILS").assertIsDisplayed()
    }

    @Test
    fun `the dashboard card renders right-to-left`() {
        render(rtl = true) {
            ProjectDashboardCard(
                summary = ProjectDashboardSummary(
                    activeProjectCount = 1,
                    unbilledProjectCount = 0,
                    outstanding = MoneyByCurrency.of(Money.of("100.00", "ILS")),
                    overdue = MoneyByCurrency.EMPTY,
                    receivedThisMonth = MoneyByCurrency.EMPTY,
                    receivedTaxThisMonth = MoneyByCurrency.EMPTY,
                    needsAttentionCount = 0,
                ),
                onOpenProjects = {},
            )
        }
        composeRule.onAllNodesWithText("Outstanding").onFirst().assertIsDisplayed()
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @Test
    fun `the filters offer only the clients and currencies that exist`() {
        render {
            ProjectReportFilters(
                filter = ProjectReportFilter(from = from, to = to),
                availableClients = listOf("Acme Ltd", "Globex"),
                availableCurrencies = listOf("ILS", "USD"),
                availableProjects = listOf(project("p1"), project("p2", name = "Mobile app")),
                onFilterChange = {},
            )
        }

        composeRule.onNodeWithText("Acme Ltd").assertIsDisplayed()
        composeRule.onNodeWithText("Globex").assertIsDisplayed()
        composeRule.onNodeWithText("USD").assertIsDisplayed()
        composeRule.onNodeWithText("Mobile app").assertIsDisplayed()
    }

    @Test
    fun `a single currency offers no currency filter`() {
        render {
            ProjectReportFilters(
                filter = ProjectReportFilter(from = from, to = to),
                availableClients = emptyList(),
                availableCurrencies = listOf("USD"),
                availableProjects = listOf(project()),
                onFilterChange = {},
            )
        }
        // Nothing to choose between, so the row is not offered.
        assertEquals(0, composeRule.onAllNodesWithText("Currency").fetchSemanticsNodes().size)
    }

    @Test
    fun `changing a filter reports the new filter back`() {
        var latest: ProjectReportFilter? = null
        render {
            ProjectReportFilters(
                filter = ProjectReportFilter(from = from, to = to),
                availableClients = listOf("Acme Ltd"),
                availableCurrencies = listOf("USD"),
                availableProjects = listOf(project()),
                onFilterChange = { latest = it },
            )
        }

        composeRule.onNodeWithText("Acme Ltd").performClick()

        assertEquals("Acme Ltd", latest?.clientName)
        // The date range is preserved: filtering by client does not reset the period.
        assertEquals(from, latest?.from)
    }
}
