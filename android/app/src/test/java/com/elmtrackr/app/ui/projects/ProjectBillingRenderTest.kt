package com.elmtrackr.app.ui.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.PaymentMethod
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.PaymentRejection
import com.elmtrackr.app.domain.projects.ProjectBillingCorrection
import com.elmtrackr.app.domain.projects.ProjectBillingFormInput
import com.elmtrackr.app.domain.projects.ProjectBillingStatusResolver
import com.elmtrackr.app.domain.projects.ProjectPaymentFormInput
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
import java.time.LocalDate

/**
 * Renders the billing and payment surfaces.
 *
 * The assertions are about the words the user sees: that nothing claims to issue
 * an invoice, that a refused correction explains why, and that a rejected payment
 * says how much may actually be recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    qualifiers = "w411dp-h1800dp",
    application = ScreenshotTestApplication::class,
)
class ProjectBillingRenderTest {

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

    private val today = LocalDate.of(2026, 7, 29)

    private fun project() = Project(
        id = "p1",
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun record(dueOn: LocalDate? = today.plusDays(10)) = ProjectBillingRecord(
        id = "b1",
        userId = "u1",
        projectId = "p1",
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        externalReference = "INV-42",
        billedOn = today.minusDays(5),
        dueOn = dueOn,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(id: String = "pay1", amount: String = "5000.00") = ProjectPayment(
        id = id,
        userId = "u1",
        projectId = "p1",
        billingRecordId = "b1",
        paidOn = today,
        amount = Money.of(amount, "USD"),
        method = PaymentMethod.BANK_TRANSFER.name,
        externalReference = "TRX-9",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
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

    // ── Billing form ──────────────────────────────────────────────────────────

    @Test
    fun `the billing form says the app does not create official invoices`() {
        render {
            ProjectBillingFormScreen(
                initialInput = ProjectBillingFormInput.from(project(), today),
                errors = emptyMap(),
                onSave = {},
                onBack = {},
            )
        }

        composeRule.onAllNodesWithText("Record billing details").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText(
            "does not create official invoices",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `the billing form calls the reference external rather than generated`() {
        render {
            ProjectBillingFormScreen(
                initialInput = ProjectBillingFormInput.from(project(), today),
                errors = emptyMap(),
                onSave = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("External billing reference").assertIsDisplayed()
    }

    @Test
    fun `the billing form previews the three amounts before saving`() {
        render {
            ProjectBillingFormScreen(
                initialInput = ProjectBillingFormInput.from(project(), today),
                errors = emptyMap(),
                onSave = {},
                onBack = {},
            )
        }

        // Base, tax and client total, so what the client will be asked for is
        // visible before the record is written.
        composeRule.onAllNodesWithText("10,000.00", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("1,800.00", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("11,800.00", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun `the billing form reports a due date before the billing date`() {
        render {
            ProjectBillingFormScreen(
                initialInput = ProjectBillingFormInput.from(project(), today),
                errors = mapOf(
                    "dueOn" to com.elmtrackr.app.domain.projects.BillingFormError.DUE_BEFORE_BILLED,
                ),
                onSave = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText(
            "Due date cannot be before the billing date",
        ).assertIsDisplayed()
    }

    @Test
    fun `the billing form renders right-to-left`() {
        render(rtl = true) {
            ProjectBillingFormScreen(
                initialInput = ProjectBillingFormInput.from(project(), today),
                errors = emptyMap(),
                onSave = {},
                onBack = {},
            )
        }
        composeRule.onAllNodesWithText("Record billing details").onFirst().assertIsDisplayed()
    }

    // ── Payment form ──────────────────────────────────────────────────────────

    @Test
    fun `the payment form shows the outstanding balance and every method`() {
        render {
            ProjectPaymentFormScreen(
                initialInput = ProjectPaymentFormInput.forRecord(record(), emptyList(), today),
                billingRecord = record(),
                existingPayments = emptyList(),
                rejection = null,
                onSave = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Outstanding balance is", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Bank transfer").assertIsDisplayed()
        composeRule.onNodeWithText("Cash").assertIsDisplayed()
        composeRule.onNodeWithText("Card").assertIsDisplayed()
        composeRule.onNodeWithText("Check").assertIsDisplayed()
        composeRule.onNodeWithText("PayPal").assertIsDisplayed()
        composeRule.onNodeWithText("Payment app").assertIsDisplayed()
        composeRule.onNodeWithText("Other").assertIsDisplayed()
    }

    @Test
    fun `an overpayment says how much may actually be recorded`() {
        render {
            ProjectPaymentFormScreen(
                initialInput = ProjectPaymentFormInput.forRecord(record(), emptyList(), today),
                billingRecord = record(),
                existingPayments = emptyList(),
                rejection = PaymentRejection.Overpayment(
                    billedTotal = Money.of("11800.00", "USD"),
                    alreadyPaid = Money.of("5000.00", "USD"),
                    attempted = Money.of("9000.00", "USD"),
                    maximumAllowed = Money.of("6800.00", "USD"),
                ),
                onSave = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("still outstanding", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a currency mismatch says the app does not convert currencies`() {
        render {
            ProjectPaymentFormScreen(
                initialInput = ProjectPaymentFormInput.forRecord(record(), emptyList(), today),
                billingRecord = record(),
                existingPayments = emptyList(),
                rejection = PaymentRejection.CurrencyMismatch("EUR", "USD"),
                onSave = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText(
            "does not convert currencies",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `the payment form reports the chosen method back`() {
        var saved: ProjectPaymentFormInput? = null
        render {
            ProjectPaymentFormScreen(
                initialInput = ProjectPaymentFormInput.forRecord(record(), emptyList(), today),
                billingRecord = record(),
                existingPayments = emptyList(),
                rejection = null,
                onSave = { saved = it },
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Cash").performClick()
        composeRule.onNodeWithText("Save payment").performClick()

        assertEquals(PaymentMethod.CASH, saved?.method)
    }

    // ── Payment history ───────────────────────────────────────────────────────

    @Test
    fun `a payment row shows its amount, method and reference`() {
        render { PaymentHistoryRow(payment = payment(), onDelete = {}) }

        composeRule.onNodeWithText("5,000.00", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Bank transfer · ${BidiText.isolate("TRX-9")}")
            .assertIsDisplayed()
    }

    @Test
    fun `deleting a payment asks for confirmation first`() {
        var deleted = false
        render { PaymentHistoryRow(payment = payment(), onDelete = { deleted = true }) }

        composeRule.onNodeWithText("Delete payment").performClick()
        composeRule.onNodeWithText("Delete this payment?").assertIsDisplayed()
        // Nothing has happened yet.
        assertEquals(false, deleted)

        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(true, deleted)
    }

    @Test
    fun `keeping a payment leaves it alone`() {
        var deleted = false
        render { PaymentHistoryRow(payment = payment(), onDelete = { deleted = true }) }

        composeRule.onNodeWithText("Delete payment").performClick()
        composeRule.onNodeWithText("Keep").performClick()

        assertEquals(false, deleted)
    }

    // ── Overdue and correction text ───────────────────────────────────────────

    @Test
    fun `an overdue record reports how many days late it is`() {
        val state = ProjectBillingStatusResolver.resolve(
            currencyCode = "USD",
            billingRecords = listOf(record(dueOn = today.minusDays(5))),
            payments = emptyList(),
            today = today,
        )
        render { androidx.compose.material3.Text(dueStatusText(state).orEmpty()) }

        composeRule.onNodeWithText("5 days overdue").assertIsDisplayed()
    }

    @Test
    fun `a record due today says so rather than reporting zero days late`() {
        val state = ProjectBillingStatusResolver.resolve(
            currencyCode = "USD",
            billingRecords = listOf(record(dueOn = today)),
            payments = emptyList(),
            today = today,
        )
        render { androidx.compose.material3.Text(dueStatusText(state).orEmpty()) }

        composeRule.onNodeWithText("Due today").assertIsDisplayed()
    }

    @Test
    fun `a refused correction explains that payment history must be preserved`() {
        render {
            androidx.compose.material3.Text(
                correctionBlockedMessage(ProjectBillingCorrection.Block.HAS_PAYMENTS),
            )
        }

        composeRule.onNodeWithText(
            "Payment history has to stay as it is",
            substring = true,
        ).assertIsDisplayed()
    }

    // ── Discard guard ─────────────────────────────────────────────────────────

    /**
     * These forms had no dirty tracking, no BackHandler and no discard dialog, so a
     * filled-in payment amount was lost to one back tap. The shift form and every
     * Settings detail screen already guarded this.
     */
    @Test
    fun `leaving the payment form with a typed amount asks before discarding`() {
        var closed = false
        render {
            ProjectPaymentFormScreen(
                initialInput = ProjectPaymentFormInput.forRecord(record(), emptyList(), today),
                billingRecord = record(),
                existingPayments = emptyList(),
                rejection = null,
                onSave = {},
                onBack = { closed = true },
            )
        }

        composeRule.onNodeWithText("Amount received").performTextInput("1200")
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals(false, closed)
    }

    /** Confirming discards; the caller's close runs exactly once. */
    @Test
    fun `confirming the discard closes the payment form`() {
        var closed = 0
        render {
            ProjectPaymentFormScreen(
                initialInput = ProjectPaymentFormInput.forRecord(record(), emptyList(), today),
                billingRecord = record(),
                existingPayments = emptyList(),
                rejection = null,
                onSave = {},
                onBack = { closed++ },
            )
        }
        composeRule.onNodeWithText("Amount received").performTextInput("1200")
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Discard").performClick()

        assertEquals(1, closed)
    }

    /** Keeping the edits leaves the form open with the amount still typed. */
    @Test
    fun `keeping the edits returns to the payment form`() {
        var closed = false
        render {
            ProjectPaymentFormScreen(
                initialInput = ProjectPaymentFormInput.forRecord(record(), emptyList(), today),
                billingRecord = record(),
                existingPayments = emptyList(),
                rejection = null,
                onSave = {},
                onBack = { closed = true },
            )
        }
        composeRule.onNodeWithText("Amount received").performTextInput("1200")
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Keep editing").performClick()

        assertEquals(false, closed)
        // The dialog is gone and the form did not close. Deliberately not asserting
        // the typed "1200" is still there: the field lives in a LazyColumn item that
        // is out of composition while the dialog is up, so it is absent from the
        // semantics tree even though rememberSaveable still holds the value. Claiming
        // otherwise would be asserting something this harness cannot see.
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    /** An untouched form must not nag on the way out. */
    @Test
    fun `leaving an untouched payment form closes immediately`() {
        var closed = false
        render {
            ProjectPaymentFormScreen(
                initialInput = ProjectPaymentFormInput.forRecord(record(), emptyList(), today),
                billingRecord = record(),
                existingPayments = emptyList(),
                rejection = null,
                onSave = {},
                onBack = { closed = true },
            )
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(true, closed)
    }
}
