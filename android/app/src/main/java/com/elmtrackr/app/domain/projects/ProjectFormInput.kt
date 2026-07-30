package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.CurrencyScales
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.model.UserSettings
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Which figure the user knows, and therefore types. */
enum class ProjectAmountEntryMode {
    /** "Your fee before tax": tax and client total are calculated from it. */
    FEE_BEFORE_TAX,

    /** "Client total": the base fee and tax are extracted from it. */
    CLIENT_TOTAL,
    ;

    fun toTaxMode(taxEnabled: Boolean): TaxMode = when {
        !taxEnabled -> TaxMode.NONE
        this == CLIENT_TOTAL -> TaxMode.INCLUSIVE
        else -> TaxMode.EXCLUSIVE
    }

    companion object {
        fun fromTaxMode(taxMode: TaxMode): ProjectAmountEntryMode =
            if (taxMode == TaxMode.INCLUSIVE) CLIENT_TOTAL else FEE_BEFORE_TAX
    }
}

/** Which field failed validation, so the form can point at it. */
enum class ProjectFormField { NAME, CLIENT_NAME, CURRENCY, AMOUNT, TAX_RATE, HOUR_BUDGET, TARGET_RATE, DEADLINE }

/**
 * Raw, editable form state. Strings rather than parsed values, so a half-typed
 * number survives rotation and process death without being coerced.
 */
data class ProjectFormInput(
    val name: String = "",
    val clientName: String = "",
    val description: String = "",
    val currencyCode: String = "",
    val amountText: String = "",
    val entryMode: ProjectAmountEntryMode = ProjectAmountEntryMode.FEE_BEFORE_TAX,
    val taxEnabled: Boolean = false,
    val taxLabel: String = "",
    val taxRateText: String = "",
    val hourBudgetText: String = "",
    val targetHourlyRateText: String = "",
    val startDate: LocalDate? = null,
    val deadline: LocalDate? = null,
    val notes: String = "",
    val workStatus: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
) {
    val amount: BigDecimal? get() = amountText.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
    val taxRate: BigDecimal get() = taxRateText.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
    /**
     * The hour budget as the user typed it.
     *
     * BigDecimal rather than Double even though hours are not money: 2.35 hours
     * is 140.99999999999997 minutes in binary floating point, and while rounding
     * currently rescues that, nothing in the type system says it has to. Keeping
     * the whole project domain free of binary floating point removes the
     * question instead of relying on the answer.
     */
    val hourBudgetHours: BigDecimal?
        get() = hourBudgetText.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
    val targetHourlyRate: BigDecimal?
        get() = targetHourlyRateText.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

    val taxMode: TaxMode get() = entryMode.toTaxMode(taxEnabled)

    /**
     * The live base / tax / client total breakdown for what has been typed so
     * far, or null while the amount is unusable. Always produced by
     * [ProjectFee], so the preview and the saved project cannot disagree.
     */
    fun feePreview(): ProjectFee? {
        val value = amount ?: return null
        if (!CurrencyScales.isValidCode(currencyCode)) return null
        return ProjectFee.from(
            enteredAmount = value,
            currencyCode = currencyCode,
            taxMode = taxMode,
            taxRatePercent = taxRate,
            taxLabel = taxLabel.trim().takeIf { it.isNotEmpty() },
        )
    }

    companion object {

        internal val MINUTES_PER_HOUR = BigDecimal("60")

        /** A blank form seeded from the user's Paid Projects defaults. */
        fun forNewProject(settings: UserSettings?): ProjectFormInput {
            val taxRate = settings?.projectsTaxRateBasisPoints ?: 0
            return ProjectFormInput(
                currencyCode = settings?.projectsCurrencyCode() ?: "USD",
                taxEnabled = taxRate > 0,
                taxLabel = settings?.projectsTaxLabel.orEmpty(),
                taxRateText = if (taxRate > 0) {
                    BigDecimal(taxRate).movePointLeft(2).stripTrailingZeros().toPlainString()
                } else {
                    ""
                },
                entryMode = if (settings?.projectsTaxInclusive == true) {
                    ProjectAmountEntryMode.CLIENT_TOTAL
                } else {
                    ProjectAmountEntryMode.FEE_BEFORE_TAX
                },
                workStatus = ProjectWorkStatus.ACTIVE,
            )
        }

        /** The form for an existing project, showing the figure it was priced by. */
        fun forEdit(project: Project): ProjectFormInput = ProjectFormInput(
            name = project.name,
            clientName = project.clientName.orEmpty(),
            description = project.description.orEmpty(),
            currencyCode = project.currencyCode,
            amountText = project.fee.enteredAmount.amount.stripTrailingZeros().toPlainString(),
            entryMode = ProjectAmountEntryMode.fromTaxMode(project.fee.taxMode),
            taxEnabled = project.fee.taxMode.isEnabled,
            taxLabel = project.fee.taxLabel.orEmpty(),
            taxRateText = project.fee.taxRatePercent
                .takeIf { it.signum() > 0 }
                ?.stripTrailingZeros()
                ?.toPlainString()
                .orEmpty(),
            // Two decimal places, then trailing zeros dropped: 90 minutes reads
            // as "1.5" and 120 as "2". Division by 60 does not terminate for
            // most values, so an unbounded conversion put "1.6666666666666667"
            // in the field for a 100-minute budget. Two places always round-trip
            // back to the same whole minute.
            hourBudgetText = project.hourBudgetMinutes
                ?.let { minutes ->
                    BigDecimal(minutes)
                        .divide(MINUTES_PER_HOUR, 2, java.math.RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                }
                .orEmpty(),
            targetHourlyRateText = project.targetHourlyRate
                ?.amount
                ?.stripTrailingZeros()
                ?.toPlainString()
                .orEmpty(),
            startDate = project.startDate,
            deadline = project.deadline,
            notes = project.notes.orEmpty(),
            workStatus = project.workStatus,
        )
    }
}

/** Validation outcome, keyed by field so the form can mark the offending inputs. */
data class ProjectFormValidation(val errors: Map<ProjectFormField, ProjectFormError>) {
    val isValid: Boolean get() = errors.isEmpty()
    operator fun get(field: ProjectFormField): ProjectFormError? = errors[field]
}

enum class ProjectFormError {
    REQUIRED,
    NOT_A_NUMBER,
    MUST_BE_POSITIVE,
    TAX_RATE_OUT_OF_RANGE,
    INVALID_CURRENCY,
    DEADLINE_BEFORE_START,
}

object ProjectFormValidator {

    private val HUNDRED = BigDecimal("100")

    fun validate(input: ProjectFormInput): ProjectFormValidation {
        val errors = mutableMapOf<ProjectFormField, ProjectFormError>()

        if (input.name.isBlank()) errors[ProjectFormField.NAME] = ProjectFormError.REQUIRED
        if (input.clientName.isBlank()) errors[ProjectFormField.CLIENT_NAME] = ProjectFormError.REQUIRED
        if (!CurrencyScales.isValidCode(input.currencyCode)) {
            errors[ProjectFormField.CURRENCY] = ProjectFormError.INVALID_CURRENCY
        }

        when {
            input.amountText.isBlank() -> errors[ProjectFormField.AMOUNT] = ProjectFormError.REQUIRED
            input.amount == null -> errors[ProjectFormField.AMOUNT] = ProjectFormError.NOT_A_NUMBER
            input.amount!!.signum() <= 0 -> errors[ProjectFormField.AMOUNT] = ProjectFormError.MUST_BE_POSITIVE
        }

        if (input.taxEnabled) {
            val rateText = input.taxRateText.trim()
            val rate = rateText.toBigDecimalOrNull()
            when {
                rateText.isEmpty() -> errors[ProjectFormField.TAX_RATE] = ProjectFormError.REQUIRED
                rate == null -> errors[ProjectFormField.TAX_RATE] = ProjectFormError.NOT_A_NUMBER
                rate.signum() <= 0 || rate > HUNDRED ->
                    errors[ProjectFormField.TAX_RATE] = ProjectFormError.TAX_RATE_OUT_OF_RANGE
            }
        }

        if (input.hourBudgetText.isNotBlank()) {
            val hours = input.hourBudgetHours
            when {
                hours == null -> errors[ProjectFormField.HOUR_BUDGET] = ProjectFormError.NOT_A_NUMBER
                hours.signum() <= 0 -> errors[ProjectFormField.HOUR_BUDGET] =
                    ProjectFormError.MUST_BE_POSITIVE
            }
        }

        if (input.targetHourlyRateText.isNotBlank()) {
            val rate = input.targetHourlyRate
            when {
                rate == null -> errors[ProjectFormField.TARGET_RATE] = ProjectFormError.NOT_A_NUMBER
                rate.signum() <= 0 -> errors[ProjectFormField.TARGET_RATE] = ProjectFormError.MUST_BE_POSITIVE
            }
        }

        val start = input.startDate
        val deadline = input.deadline
        if (start != null && deadline != null && deadline.isBefore(start)) {
            errors[ProjectFormField.DEADLINE] = ProjectFormError.DEADLINE_BEFORE_START
        }

        return ProjectFormValidation(errors)
    }

    /**
     * Builds the project to save. Returns null when the form is invalid, so a
     * caller cannot accidentally persist a half-filled project.
     *
     * Only project fields are produced: no billing record is created or touched
     * here, which is what keeps an existing billed snapshot intact when the
     * user edits a project's price.
     */
    fun toProject(
        input: ProjectFormInput,
        existing: Project?,
        userId: String,
        now: Instant,
    ): Project? {
        if (!validate(input).isValid) return null
        val fee = input.feePreview() ?: return null
        return Project(
            id = existing?.id ?: "",
            userId = userId,
            name = input.name.trim(),
            clientName = input.clientName.trim().takeIf { it.isNotEmpty() },
            clientId = existing?.clientId,
            description = input.description.trim().takeIf { it.isNotEmpty() },
            workStatus = input.workStatus,
            fee = fee,
            hourBudgetMinutes = input.hourBudgetHours?.let { hours ->
                hours.multiply(ProjectFormInput.MINUTES_PER_HOUR)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .toInt()
            },
            targetHourlyRate = input.targetHourlyRate?.let { Money.of(it, fee.currencyCode) },
            startDate = input.startDate,
            deadline = input.deadline,
            completionDate = existing?.completionDate,
            notes = input.notes.trim().takeIf { it.isNotEmpty() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            archivedAt = existing?.archivedAt,
            remoteId = existing?.remoteId,
        )
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    runCatching { BigDecimal(this.trim()) }.getOrNull()
