package com.elmtrackr.app.ui.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.ProjectFormInput
import com.elmtrackr.app.domain.projects.ProjectListFilter
import com.elmtrackr.app.domain.projects.ProjectMetrics
import com.elmtrackr.app.domain.projects.ProjectStatusFilter
import com.elmtrackr.app.domain.projects.ProjectSummary
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
import java.time.temporal.ChronoUnit

/**
 * Renders the Paid Projects screens and asserts what a user actually sees.
 *
 * Assertions are on English resource text, and the RTL cases check that the same
 * content still renders with the layout direction flipped — mirroring is what
 * breaks in RTL, not the strings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    // Tall viewport so a LazyColumn composes the entire screen: assertions
    // must not depend on scroll position, which is not stable across runs.
    qualifiers = "w411dp-h1800dp",
    application = ScreenshotTestApplication::class,
)
class ProjectsRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Raises Espresso's idling budget for this class.
     *
     * These renders pass in seconds on their own. In a full-suite run — once other
     * Robolectric + Compose classes have built and torn down their own Compose
     * environments in the same JVM — the detail-screen cases instead fail with
     * AppNotIdleException, which points at leaked global Compose state rather than
     * at anything this screen does. The list and form cases in this class pass in
     * the same run.
     *
     * The failure arrived with this class, in the commit that first added these
     * screens, and is not a regression from the later shift, billing or reporting
     * work: the same eight cases fail identically on that earlier commit.
     *
     * Ruled out so far, so the next attempt does not repeat them: raising the
     * idling budget (240s changes nothing, so this is not slowness); recycling the
     * JVM with forkEvery; LinearProgressIndicator, which these fixtures never
     * compose because none of them set an hour budget; clock reads during
     * composition; and an infinite animation in the tree under test — every
     * rememberInfiniteTransition in the app sits in a skeleton or in the scaffold
     * background, neither of which ProjectDetailScreen reaches when called
     * directly like this.
     */
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

    private val today = LocalDate.of(2026, 7, 29)

    /** Generous, but bounded: a genuine composition loop must still fail. */
    private val IDLING_BUDGET_SECONDS = 240L

    private fun project(
        id: String = "p1",
        name: String = "Website rebuild",
        client: String? = "Acme Ltd",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        entered: String = "10000",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
        currency: String = "USD",
    ) = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = client,
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, mode, BigDecimal(rate), "VAT"),
        deadline = today.plusDays(20),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun summary(
        project: Project = project(),
        shifts: List<Shift> = emptyList(),
        records: List<ProjectBillingRecord> = emptyList(),
        payments: List<ProjectPayment> = emptyList(),
    ): ProjectSummary = ProjectMetrics.summarize(project, shifts, records, payments, today)

    private fun readyState(vararg summaries: ProjectSummary) = ProjectsUiState.Ready(
        allProjects = summaries.toList(),
        visibleProjects = ProjectListFilter.apply(summaries.toList(), "", ProjectStatusFilter.ALL),
        counts = ProjectListFilter.counts(summaries.toList()),
    )

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

    // ── List and empty state ──────────────────────────────────────────────────

    @Test
    fun `the empty state invites a first project`() {
        composeRule.setContent {
            Themed {
                ProjectsList(readyState(), {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("No projects yet").assertIsDisplayed()
        composeRule.onNodeWithText("New project").assertIsDisplayed()
    }

    @Test
    fun `a search matching nothing shows the no-matches state, not the empty state`() {
        val state = readyState(summary()).copy(
            query = "nothing",
            visibleProjects = emptyList(),
        )
        composeRule.setContent { Themed { ProjectsList(state, {}, {}, {}, {}) } }

        composeRule.onNodeWithText("No projects match").assertIsDisplayed()
        composeRule.onNodeWithText("No projects yet").assertDoesNotExist()
    }

    @Test
    fun `a card shows the project, client and client total`() {
        composeRule.setContent { Themed { ProjectsList(readyState(summary()), {}, {}, {}, {}) } }

        composeRule.onNodeWithText("Website rebuild").assertIsDisplayed()
        composeRule.onNodeWithText("Acme Ltd").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onNodeWithText("Not billed").assertIsDisplayed()
    }

    @Test
    fun `secondary detail is hidden until the card is expanded`() {
        composeRule.setContent { Themed { ProjectsList(readyState(summary()), {}, {}, {}, {}) } }

        // Collapsed: the secondary figures are not in the tree at all.
        composeRule.onNodeWithText("Effective hourly rate").assertDoesNotExist()
        composeRule.onNodeWithText("Hours tracked").assertDoesNotExist()
        composeRule.onNodeWithText("More detail").performClick()
        composeRule.waitForIdle()

        // Expanded: the toggle flips, and the secondary figures join the tree.
        composeRule.onNodeWithText("Less detail").assertExists()
        composeRule.onNodeWithText("Hours tracked").assertExists()
    }

    @Test
    fun `filter chips are rendered and clickable`() {
        var chosen: ProjectStatusFilter? = null
        val summaries = listOf(
            summary(project("a", status = ProjectWorkStatus.ACTIVE)),
            summary(project("b", name = "Draft one", status = ProjectWorkStatus.DRAFT)),
        )
        val state = ProjectsUiState.Ready(
            allProjects = summaries,
            visibleProjects = summaries,
            counts = ProjectListFilter.counts(summaries),
        )
        composeRule.setContent {
            Themed { ProjectsList(state, {}, { chosen = it }, {}, {}) }
        }

        composeRule.onNodeWithText("Draft (1)").performClick()
        assertEquals(ProjectStatusFilter.DRAFT, chosen)
    }

    @Test
    fun `tapping a card opens that project`() {
        var opened: String? = null
        composeRule.setContent {
            Themed { ProjectsList(readyState(summary()), {}, {}, {}, { opened = it }) }
        }
        composeRule.onNodeWithText("Website rebuild").performClick()
        assertEquals("p1", opened)
    }

    @Test
    fun `the list renders right-to-left`() {
        composeRule.setContent {
            Themed(rtl = true) { ProjectsList(readyState(summary()), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Website rebuild").assertIsDisplayed()
        composeRule.onNodeWithText("Acme Ltd").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
    }

    @Test
    fun `the empty state renders right-to-left`() {
        composeRule.setContent { Themed(rtl = true) { ProjectsList(readyState(), {}, {}, {}, {}) } }
        composeRule.onNodeWithText("No projects yet").assertIsDisplayed()
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    @Test
    fun `the detail screen shows every section`() {
        composeRule.setContent {
            Themed {
                ProjectDetailScreen(summary(), emptyList(), emptyList(), {}, {}, {}, {})
            }
        }
        listOf("Overview", "Time", "Billing", "Notes", "Project status").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun `the detail screen shows the three amounts for a taxed project`() {
        composeRule.setContent {
            Themed { ProjectDetailScreen(summary(), emptyList(), emptyList(), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Your fee before tax").assertExists()
        composeRule.onNodeWithText("Client total").assertExists()
    }

    @Test
    fun `a tax-disabled project shows only the client total`() {
        val untaxed = summary(project(mode = TaxMode.NONE, rate = "0"))
        composeRule.setContent {
            Themed { ProjectDetailScreen(untaxed, emptyList(), emptyList(), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Client total").assertExists()
        composeRule.onNodeWithText("Your fee before tax").assertDoesNotExist()
    }

    @Test
    fun `the detail screen offers the actions for the current status`() {
        composeRule.setContent {
            Themed { ProjectDetailScreen(summary(), emptyList(), emptyList(), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Pause").assertExists()
        composeRule.onNodeWithText("Complete").assertExists()
        composeRule.onNodeWithText("Archive").assertExists()
        composeRule.onNodeWithText("Start project").assertDoesNotExist()
    }

    @Test
    fun `an archived project only offers restore`() {
        val archived = summary(project(status = ProjectWorkStatus.ARCHIVED))
        composeRule.setContent {
            Themed { ProjectDetailScreen(archived, emptyList(), emptyList(), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Restore from archive").assertExists()
        composeRule.onNodeWithText("Pause").assertDoesNotExist()
    }

    @Test
    fun `a used project explains archiving instead of offering deletion`() {
        val used = summary(
            project(status = ProjectWorkStatus.DRAFT),
            shifts = listOf(
                Shift(
                    id = "s1",
                    userId = "u1",
                    startTime = Instant.parse("2026-07-20T08:00:00Z"),
                    endTime = Instant.parse("2026-07-20T08:00:00Z").plus(2, ChronoUnit.HOURS),
                    projectId = "p1",
                ),
            ),
        )
        composeRule.setContent {
            Themed { ProjectDetailScreen(used, emptyList(), emptyList(), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Delete project").assertDoesNotExist()
    }

    @Test
    fun `the detail screen renders right-to-left`() {
        composeRule.setContent {
            Themed(rtl = true) { ProjectDetailScreen(summary(), emptyList(), emptyList(), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Overview").assertIsDisplayed()
        composeRule.onNodeWithText("Client total").assertExists()
    }

    @Test
    fun `billing details render when a record exists`() {
        val record = ProjectBillingRecord(
            id = "bill-1",
            userId = "u1",
            projectId = "p1",
            fee = ProjectFee.from(BigDecimal("11800"), "USD", TaxMode.NONE, BigDecimal.ZERO),
            externalReference = "2026-014",
            billedOn = today.minusDays(7),
            dueOn = today.plusDays(14),
        )
        val payment = ProjectPayment(
            id = "pay-1",
            userId = "u1",
            projectId = "p1",
            billingRecordId = "bill-1",
            paidOn = today,
            amount = Money.of("5000", "USD"),
        )
        val billed = summary(records = listOf(record), payments = listOf(payment))
        composeRule.setContent {
            Themed { ProjectDetailScreen(billed, listOf(record), listOf(payment), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Billing reference").assertExists()
        composeRule.onNodeWithText("2026-014").assertExists()
        composeRule.onNodeWithText("Outstanding").assertExists()
        // Appears twice by design: the Overview pill and the Billing row.
        composeRule.onAllNodesWithText("Partially paid").onFirst().assertExists()
    }

    // ── Form ──────────────────────────────────────────────────────────────────

    @Test
    fun `the form shows the amount entry choice`() {
        composeRule.setContent {
            Themed {
                ProjectFormScreen(
                    existing = null,
                    initialInput = ProjectFormInput(currencyCode = "USD"),
                    isBilled = false,
                    isSaving = false,
                    onSave = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Which amount do you know?").assertExists()
        // Two nodes carry this text: the entry-mode choice and the amount
        // field's own label, which is exactly the pairing being checked.
        composeRule.onAllNodesWithText("Your fee before tax").onFirst().assertExists()
        composeRule.onAllNodesWithText("Client total").onFirst().assertExists()
    }

    @Test
    fun `the form explains that a billing snapshot is preserved`() {
        composeRule.setContent {
            Themed {
                ProjectFormScreen(
                    existing = project(),
                    initialInput = ProjectFormInput.forEdit(project()),
                    isBilled = true,
                    isSaving = false,
                    onSave = {},
                    onBack = {},
                )
            }
        }
        composeRule
            .onNodeWithText("billing request keeps the amount", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `the form does not warn about a snapshot when nothing is billed`() {
        composeRule.setContent {
            Themed {
                ProjectFormScreen(
                    existing = project(),
                    initialInput = ProjectFormInput.forEdit(project()),
                    isBilled = false,
                    isSaving = false,
                    onSave = {},
                    onBack = {},
                )
            }
        }
        composeRule
            .onNodeWithText("billing request keeps the amount", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `saving an incomplete form surfaces the validation errors`() {
        var saved = false
        composeRule.setContent {
            Themed {
                ProjectFormScreen(
                    existing = null,
                    initialInput = ProjectFormInput(currencyCode = "USD"),
                    isBilled = false,
                    isSaving = false,
                    onSave = { saved = true },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Save project").performClick()
        composeRule.waitForIdle()

        assertEquals(false, saved)
        composeRule.onAllNodesWithText("Required").onFirst().assertExists()
    }

    @Test
    fun `the form states that currencies are not converted`() {
        composeRule.setContent {
            Themed {
                ProjectFormScreen(
                    existing = null,
                    initialInput = ProjectFormInput(currencyCode = "USD"),
                    isBilled = false,
                    isSaving = false,
                    onSave = {},
                    onBack = {},
                )
            }
        }
        composeRule
            .onNodeWithText("does not convert currencies", substring = true)
            .assertExists()
    }

    @Test
    fun `the form renders right-to-left`() {
        composeRule.setContent {
            Themed(rtl = true) {
                ProjectFormScreen(
                    existing = null,
                    initialInput = ProjectFormInput(currencyCode = "ILS"),
                    isBilled = false,
                    isSaving = false,
                    onSave = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Project name").assertIsDisplayed()
        composeRule.onNodeWithText("Client name").assertExists()
    }

}
