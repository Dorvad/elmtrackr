package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ProjectFormValidatorTest {

    private val now = Instant.ofEpochMilli(1_000)

    private fun validInput() = ProjectFormInput(
        name = "Website rebuild",
        clientName = "Acme Ltd",
        currencyCode = "ILS",
        amountText = "10000",
    )

    // ── Creation ──────────────────────────────────────────────────────────────

    @Test
    fun `a complete form produces a project`() {
        val project = ProjectFormValidator.toProject(validInput(), null, "u1", now)
        assertNotNull(project)
        assertEquals("Website rebuild", project!!.name)
        assertEquals("Acme Ltd", project.clientName)
        assertEquals("ILS", project.currencyCode)
        assertEquals(Money.of("10000.00", "ILS"), project.fee.clientTotal)
        assertEquals("", project.id)
        assertEquals("u1", project.userId)
    }

    @Test
    fun `required fields are enforced`() {
        val validation = ProjectFormValidator.validate(ProjectFormInput())
        assertEquals(ProjectFormError.REQUIRED, validation[ProjectFormField.NAME])
        assertEquals(ProjectFormError.REQUIRED, validation[ProjectFormField.CLIENT_NAME])
        assertEquals(ProjectFormError.INVALID_CURRENCY, validation[ProjectFormField.CURRENCY])
        assertEquals(ProjectFormError.REQUIRED, validation[ProjectFormField.AMOUNT])
        assertFalse(validation.isValid)
    }

    @Test
    fun `an invalid form produces no project`() {
        assertNull(ProjectFormValidator.toProject(ProjectFormInput(), null, "u1", now))
    }

    @Test
    fun `a zero or negative amount is rejected`() {
        assertEquals(
            ProjectFormError.MUST_BE_POSITIVE,
            ProjectFormValidator.validate(validInput().copy(amountText = "0"))[ProjectFormField.AMOUNT],
        )
        assertEquals(
            ProjectFormError.MUST_BE_POSITIVE,
            ProjectFormValidator.validate(validInput().copy(amountText = "-5"))[ProjectFormField.AMOUNT],
        )
    }

    @Test
    fun `optional fields stay absent when blank`() {
        val project = ProjectFormValidator.toProject(validInput(), null, "u1", now)!!
        assertNull(project.description)
        assertNull(project.notes)
        assertNull(project.hourBudgetMinutes)
        assertNull(project.targetHourlyRate)
        assertNull(project.startDate)
        assertNull(project.deadline)
    }

    @Test
    fun `optional fields are carried through when filled`() {
        val input = validInput().copy(
            description = "Five pages",
            notes = "Kickoff done",
            hourBudgetText = "40",
            targetHourlyRateText = "250",
            startDate = LocalDate.of(2026, 7, 1),
            deadline = LocalDate.of(2026, 8, 31),
        )
        val project = ProjectFormValidator.toProject(input, null, "u1", now)!!
        assertEquals("Five pages", project.description)
        assertEquals("Kickoff done", project.notes)
        assertEquals(2400, project.hourBudgetMinutes)
        assertEquals(Money.of("250.00", "ILS"), project.targetHourlyRate)
        assertEquals(LocalDate.of(2026, 7, 1), project.startDate)
        assertEquals(LocalDate.of(2026, 8, 31), project.deadline)
    }

    @Test
    fun `a deadline before the start date is rejected`() {
        val input = validInput().copy(
            startDate = LocalDate.of(2026, 8, 1),
            deadline = LocalDate.of(2026, 7, 1),
        )
        assertEquals(
            ProjectFormError.DEADLINE_BEFORE_START,
            ProjectFormValidator.validate(input)[ProjectFormField.DEADLINE],
        )
    }

    // ── Tax-exclusive entry ───────────────────────────────────────────────────

    @Test
    fun `fee-before-tax entry adds tax on top`() {
        val input = validInput().copy(
            entryMode = ProjectAmountEntryMode.FEE_BEFORE_TAX,
            taxEnabled = true,
            taxRateText = "18",
            taxLabel = "VAT",
        )
        val fee = ProjectFormValidator.toProject(input, null, "u1", now)!!.fee
        assertEquals(TaxMode.EXCLUSIVE, fee.taxMode)
        assertEquals(Money.of("10000.00", "ILS"), fee.base)
        assertEquals(Money.of("1800.00", "ILS"), fee.tax)
        assertEquals(Money.of("11800.00", "ILS"), fee.clientTotal)
        assertEquals("VAT", fee.taxLabel)
    }

    // ── Tax-inclusive entry ───────────────────────────────────────────────────

    @Test
    fun `client-total entry extracts the base fee and tax`() {
        val input = validInput().copy(
            amountText = "11800",
            entryMode = ProjectAmountEntryMode.CLIENT_TOTAL,
            taxEnabled = true,
            taxRateText = "18",
        )
        val fee = ProjectFormValidator.toProject(input, null, "u1", now)!!.fee
        assertEquals(TaxMode.INCLUSIVE, fee.taxMode)
        assertEquals(Money.of("11800.00", "ILS"), fee.clientTotal)
        assertEquals(Money.of("10000.00", "ILS"), fee.base)
        assertEquals(Money.of("1800.00", "ILS"), fee.tax)
    }

    @Test
    fun `client-total entry with a repeating quotient still reconciles`() {
        val input = validInput().copy(
            amountText = "100",
            entryMode = ProjectAmountEntryMode.CLIENT_TOTAL,
            taxEnabled = true,
            taxRateText = "17",
        )
        val fee = ProjectFormValidator.toProject(input, null, "u1", now)!!.fee
        assertEquals(Money.of("100.00", "ILS"), fee.clientTotal)
        assertEquals(fee.clientTotal, fee.base + fee.tax)
    }

    @Test
    fun `the live preview matches what gets saved`() {
        val input = validInput().copy(
            amountText = "100",
            entryMode = ProjectAmountEntryMode.CLIENT_TOTAL,
            taxEnabled = true,
            taxRateText = "17",
        )
        assertEquals(input.feePreview(), ProjectFormValidator.toProject(input, null, "u1", now)!!.fee)
    }

    // ── Tax disabled ──────────────────────────────────────────────────────────

    @Test
    fun `tax disabled makes base and client total the same`() {
        val input = validInput().copy(taxEnabled = false, taxRateText = "18", taxLabel = "VAT")
        val fee = ProjectFormValidator.toProject(input, null, "u1", now)!!.fee
        assertEquals(TaxMode.NONE, fee.taxMode)
        assertEquals(fee.base, fee.clientTotal)
        assertTrue(fee.tax.isZero)
        assertNull(fee.taxLabel)
        assertEquals(BigDecimal.ZERO, fee.taxRatePercent)
    }

    @Test
    fun `the tax rate is only required while tax is enabled`() {
        val disabled = validInput().copy(taxEnabled = false, taxRateText = "")
        assertTrue(ProjectFormValidator.validate(disabled).isValid)

        val enabled = validInput().copy(taxEnabled = true, taxRateText = "")
        assertEquals(
            ProjectFormError.REQUIRED,
            ProjectFormValidator.validate(enabled)[ProjectFormField.TAX_RATE],
        )
    }

    @Test
    fun `an out-of-range tax rate is rejected`() {
        listOf("0", "-1", "101").forEach { rate ->
            val input = validInput().copy(taxEnabled = true, taxRateText = rate)
            assertEquals(
                "rate $rate should be rejected",
                ProjectFormError.TAX_RATE_OUT_OF_RANGE,
                ProjectFormValidator.validate(input)[ProjectFormField.TAX_RATE],
            )
        }
    }

    // ── Currency ──────────────────────────────────────────────────────────────

    @Test
    fun `the project keeps the selected currency`() {
        listOf("ILS", "USD", "EUR", "JPY", "SEK").forEach { code ->
            val project = ProjectFormValidator.toProject(
                validInput().copy(currencyCode = code),
                null,
                "u1",
                now,
            )!!
            assertEquals(code, project.currencyCode)
            assertEquals(code, project.fee.clientTotal.currencyCode)
        }
    }

    @Test
    fun `a malformed currency code is rejected`() {
        listOf("", "IL", "ILSX", "1LS").forEach { code ->
            assertEquals(
                "code '$code' should be rejected",
                ProjectFormError.INVALID_CURRENCY,
                ProjectFormValidator.validate(validInput().copy(currencyCode = code))[ProjectFormField.CURRENCY],
            )
        }
    }

    @Test
    fun `a zero-decimal currency scales the fee correctly`() {
        val project = ProjectFormValidator.toProject(
            validInput().copy(currencyCode = "JPY", amountText = "300000"),
            null,
            "u1",
            now,
        )!!
        assertEquals(0, project.fee.clientTotal.fractionDigits)
        assertEquals(Money.of("300000", "JPY"), project.fee.clientTotal)
    }

    // ── Defaults from settings ────────────────────────────────────────────────

    @Test
    fun `a new project form is seeded from the paid-projects defaults`() {
        val settings = UserSettings(
            id = "s1",
            userId = "u1",
            currency = CurrencyCode.ILS,
            currencyCode = "ILS",
            projectsDefaultCurrencyCode = "USD",
            projectsTaxLabel = "Sales Tax",
            projectsTaxRateBasisPoints = 875,
            projectsTaxInclusive = true,
        )
        val input = ProjectFormInput.forNewProject(settings)
        assertEquals("USD", input.currencyCode)
        assertTrue(input.taxEnabled)
        assertEquals("Sales Tax", input.taxLabel)
        assertEquals("8.75", input.taxRateText)
        assertEquals(ProjectAmountEntryMode.CLIENT_TOTAL, input.entryMode)
    }

    @Test
    fun `with no project defaults the form falls back to the app currency`() {
        val settings = UserSettings(id = "s1", userId = "u1", currencyCode = "GBP")
        val input = ProjectFormInput.forNewProject(settings)
        assertEquals("GBP", input.currencyCode)
        assertFalse(input.taxEnabled)
        assertEquals("", input.taxRateText)
        assertEquals(ProjectAmountEntryMode.FEE_BEFORE_TAX, input.entryMode)
    }

    @Test
    fun `a project-level currency overrides the default`() {
        val settings = UserSettings(id = "s1", userId = "u1", projectsDefaultCurrencyCode = "USD")
        val input = ProjectFormInput.forNewProject(settings).copy(
            name = "X",
            clientName = "Y",
            amountText = "100",
            currencyCode = "EUR",
        )
        assertEquals("EUR", ProjectFormValidator.toProject(input, null, "u1", now)!!.currencyCode)
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    private fun existingProject(
        mode: TaxMode = TaxMode.EXCLUSIVE,
        entered: String = "10000",
        rate: String = "18",
    ) = Project(
        id = "p1",
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal(entered), "ILS", mode, BigDecimal(rate), "VAT"),
        createdAt = Instant.ofEpochMilli(500),
        updatedAt = Instant.ofEpochMilli(500),
        remoteId = "remote-1",
    )

    @Test
    fun `the edit form shows the figure the project was priced by`() {
        val exclusive = ProjectFormInput.forEdit(existingProject(TaxMode.EXCLUSIVE, "10000", "18"))
        assertEquals("10000", exclusive.amountText)
        assertEquals(ProjectAmountEntryMode.FEE_BEFORE_TAX, exclusive.entryMode)

        val inclusive = ProjectFormInput.forEdit(existingProject(TaxMode.INCLUSIVE, "11800", "18"))
        assertEquals("11800", inclusive.amountText)
        assertEquals(ProjectAmountEntryMode.CLIENT_TOTAL, inclusive.entryMode)
    }

    @Test
    fun `an edit round-trips without changing anything`() {
        val original = existingProject()
        val edited = ProjectFormValidator.toProject(
            ProjectFormInput.forEdit(original),
            original,
            "u1",
            now,
        )!!
        assertEquals(original.fee, edited.fee)
        assertEquals(original.name, edited.name)
        assertEquals(original.clientName, edited.clientName)
    }

    @Test
    fun `editing preserves identity and creation metadata`() {
        val original = existingProject()
        val edited = ProjectFormValidator.toProject(
            ProjectFormInput.forEdit(original).copy(name = "Renamed"),
            original,
            "u1",
            now,
        )!!
        assertEquals("p1", edited.id)
        assertEquals("remote-1", edited.remoteId)
        assertEquals(Instant.ofEpochMilli(500), edited.createdAt)
        assertEquals(now, edited.updatedAt)
        assertEquals("Renamed", edited.name)
    }

    @Test
    fun `editing the amount changes only the project fee`() {
        val original = existingProject()
        val edited = ProjectFormValidator.toProject(
            ProjectFormInput.forEdit(original).copy(amountText = "20000"),
            original,
            "u1",
            now,
        )!!
        assertEquals(Money.of("20000.00", "ILS"), edited.fee.base)
        assertEquals(Money.of("23600.00", "ILS"), edited.fee.clientTotal)
        // Work status and completion state are not touched by an amount edit.
        assertEquals(original.workStatus, edited.workStatus)
        assertEquals(original.completionDate, edited.completionDate)
    }

    @Test
    fun `switching a project from tax to no tax clears the tax fields`() {
        val original = existingProject()
        val edited = ProjectFormValidator.toProject(
            ProjectFormInput.forEdit(original).copy(taxEnabled = false),
            original,
            "u1",
            now,
        )!!
        assertEquals(TaxMode.NONE, edited.fee.taxMode)
        assertNull(edited.fee.taxLabel)
        assertTrue(edited.fee.tax.isZero)
        assertEquals(edited.fee.base, edited.fee.clientTotal)
    }
}
