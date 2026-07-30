package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.PaymentMethod
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.BillingFormError
import com.elmtrackr.app.domain.projects.BillingFormResult
import com.elmtrackr.app.domain.projects.PaymentRejection
import com.elmtrackr.app.domain.projects.ProjectBillingCorrection
import com.elmtrackr.app.domain.projects.ProjectBillingFormInput
import com.elmtrackr.app.domain.projects.ProjectBillingFormValidator
import com.elmtrackr.app.domain.projects.ProjectBillingState
import com.elmtrackr.app.domain.projects.ProjectPaymentFormInput
import com.elmtrackr.app.domain.projects.ProjectPaymentValidator
import com.elmtrackr.app.domain.text.BidiText
import com.elmtrackr.app.ui.settings.SettingsDetailHeader
import com.elmtrackr.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * "Record billing details" — deliberately not "create invoice".
 *
 * ElmTrackr does not issue invoices. This form records that the user asked a
 * client for money, on a date, due on a date, under a reference they generated
 * somewhere else. The subtitle says so on screen, not just in this comment.
 *
 * The amount defaults to the project's agreed fee, so accepting the form bills
 * what was agreed; changing it is a deliberate act.
 */
@Composable
fun ProjectBillingFormScreen(
    initialInput: ProjectBillingFormInput,
    errors: Map<String, BillingFormError>,
    onSave: (ProjectBillingFormInput) -> Unit,
    onBack: () -> Unit,
) {
    val key = initialInput.projectId
    var baseAmount by rememberSaveable(key) { mutableStateOf(initialInput.baseAmount) }
    var currencyCode by rememberSaveable(key) { mutableStateOf(initialInput.currencyCode) }
    var taxLabel by rememberSaveable(key) { mutableStateOf(initialInput.taxLabel.orEmpty()) }
    var taxRate by rememberSaveable(key) { mutableStateOf(initialInput.taxRatePercent) }
    var billedOnEpoch by rememberSaveable(key) { mutableStateOf(initialInput.billedOn.toEpochDay()) }
    var dueOnEpoch by rememberSaveable(key) { mutableStateOf(initialInput.dueOn?.toEpochDay()) }
    var reference by rememberSaveable(key) { mutableStateOf(initialInput.externalReference) }
    var notes by rememberSaveable(key) { mutableStateOf(initialInput.notes) }
    var showCurrencyPicker by rememberSaveable(key) { mutableStateOf(false) }

    val input = ProjectBillingFormInput(
        projectId = initialInput.projectId,
        baseAmount = baseAmount,
        currencyCode = currencyCode,
        taxLabel = taxLabel.ifBlank { null },
        taxRatePercent = taxRate,
        taxMode = initialInput.taxMode,
        billedOn = LocalDate.ofEpochDay(billedOnEpoch),
        dueOn = dueOnEpoch?.let { LocalDate.ofEpochDay(it) },
        externalReference = reference,
        notes = notes,
    )

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            selected = currencyCode,
            onSelect = {
                currencyCode = it
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
    item {
        SettingsDetailHeader(
            title = stringResource(R.string.project_billing_form_title),
            onBack = onBack,
        )
    }
    item {
      ProjectSectionCard(stringResource(R.string.project_billing_form_title)) {
        ProjectNoteText(stringResource(R.string.project_billing_form_subtitle))
        Spacer(Modifier.height(Spacing.sm))

        ProjectTextField(
            value = baseAmount,
            onValueChange = { baseAmount = it.decimalOnly() },
            label = stringResource(R.string.project_billing_amount),
            error = errors[ProjectBillingFormValidator.FIELD_AMOUNT]
                ?.let { stringResource(billingFormError(it)) },
            keyboardType = KeyboardType.Decimal,
        )
        ProjectNoteText(stringResource(R.string.project_billing_amount_helper))

        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.project_billing_currency), style = MaterialTheme.typography.bodyMedium)
                Text(currencyCode, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showCurrencyPicker = true }) {
                Text(stringResource(R.string.project_billing_currency))
            }
        }

        // Live preview of what the client is being asked for, so the three amounts
        // are visible before saving rather than only afterwards.
        val preview = remember(input) { ProjectBillingFormValidator.validate(input) }
        (preview as? BillingFormResult.Valid)?.let { valid ->
            Text(
                text = stringResource(R.string.project_billing_preview, valid.fee.clientTotal.formatted()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ProjectFeeBreakdown(valid.fee)
        }

        if (initialInput.taxMode != TaxMode.NONE) {
            ProjectTextField(
                value = taxRate,
                onValueChange = { taxRate = it.decimalOnly() },
                label = stringResource(R.string.project_form_tax_rate),
                error = errors[ProjectBillingFormValidator.FIELD_TAX_RATE]
                    ?.let { stringResource(billingFormError(it)) },
                keyboardType = KeyboardType.Decimal,
            )
            ProjectTextField(
                value = taxLabel,
                onValueChange = { taxLabel = it },
                label = stringResource(R.string.project_form_tax_label),
            )
        }

        ProjectDateRow(
            label = stringResource(R.string.project_billing_date),
            date = LocalDate.ofEpochDay(billedOnEpoch),
            onChange = { billedOnEpoch = (it ?: LocalDate.ofEpochDay(billedOnEpoch)).toEpochDay() },
        )
        ProjectDateRow(
            label = stringResource(R.string.project_billing_due_date),
            date = dueOnEpoch?.let { LocalDate.ofEpochDay(it) },
            onChange = { dueOnEpoch = it?.toEpochDay() },
            error = errors[ProjectBillingFormValidator.FIELD_DUE_ON]
                ?.let { stringResource(billingFormError(it)) },
        )
        ProjectNoteText(stringResource(R.string.project_billing_due_date_helper))

        ProjectTextField(
            value = reference,
            onValueChange = { reference = it },
            label = stringResource(R.string.project_billing_reference_label),
        )
        ProjectNoteText(stringResource(R.string.project_billing_reference_helper))

        ProjectTextField(
            value = notes,
            onValueChange = { notes = it },
            label = stringResource(R.string.project_billing_notes),
            singleLine = false,
        )

        Spacer(Modifier.height(Spacing.md))
        Button(onClick = { onSave(input) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.project_billing_save))
        }
      }
    }
    item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

/**
 * "Record payment". Multiple payments per billing record are how partial payment
 * is represented; the amount defaults to the outstanding balance because paying
 * it off is the common case.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectPaymentFormScreen(
    initialInput: ProjectPaymentFormInput,
    billingRecord: ProjectBillingRecord,
    existingPayments: List<ProjectPayment>,
    rejection: PaymentRejection?,
    onSave: (ProjectPaymentFormInput) -> Unit,
    onBack: () -> Unit,
) {
    val key = initialInput.paymentId ?: initialInput.billingRecordId
    var amount by rememberSaveable(key) { mutableStateOf(initialInput.amount) }
    var paidOnEpoch by rememberSaveable(key) { mutableStateOf(initialInput.paidOn.toEpochDay()) }
    var methodName by rememberSaveable(key) { mutableStateOf(initialInput.method?.name.orEmpty()) }
    var reference by rememberSaveable(key) { mutableStateOf(initialInput.externalReference) }
    var notes by rememberSaveable(key) { mutableStateOf(initialInput.notes) }

    val remaining = remember(billingRecord, existingPayments, initialInput.paymentId) {
        ProjectPaymentValidator.remainingPayable(
            billingRecord,
            existingPayments.filter { it.id != initialInput.paymentId },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
    item {
        SettingsDetailHeader(
            title = stringResource(R.string.project_payment_form_title),
            onBack = onBack,
        )
    }
    item {
      ProjectSectionCard(stringResource(R.string.project_payment_form_title)) {
        ProjectTextField(
            value = amount,
            onValueChange = { amount = it.decimalOnly() },
            label = stringResource(R.string.project_payment_amount),
            error = rejection?.let { paymentRejectionMessage(it) },
            keyboardType = KeyboardType.Decimal,
        )
        ProjectNoteText(stringResource(R.string.project_payment_amount_helper, remaining.formatted()))

        ProjectDateRow(
            label = stringResource(R.string.project_payment_date),
            date = LocalDate.ofEpochDay(paidOnEpoch),
            onChange = { paidOnEpoch = (it ?: LocalDate.ofEpochDay(paidOnEpoch)).toEpochDay() },
        )

        Text(
            stringResource(R.string.project_payment_method),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaymentMethod.selectable.forEach { method ->
                FilterChip(
                    selected = methodName == method.name,
                    // Tapping the selected chip clears it: the method is optional.
                    onClick = { methodName = if (methodName == method.name) "" else method.name },
                    label = { Text(stringResource(paymentMethodLabel(method))) },
                )
            }
        }

        ProjectTextField(
            value = reference,
            onValueChange = { reference = it },
            label = stringResource(R.string.project_payment_reference),
        )
        ProjectTextField(
            value = notes,
            onValueChange = { notes = it },
            label = stringResource(R.string.project_payment_notes),
            singleLine = false,
        )

        Spacer(Modifier.height(Spacing.md))
        Button(
            onClick = {
                onSave(
                    initialInput.copy(
                        amount = amount,
                        paidOn = LocalDate.ofEpochDay(paidOnEpoch),
                        method = PaymentMethod.fromPersisted(methodName),
                        externalReference = reference,
                        notes = notes,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.project_payment_save))
        }
      }
    }
    item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

/** Localised text for a rejected payment, including the amounts that caused it. */
@Composable
fun paymentRejectionMessage(rejection: PaymentRejection): String = when (rejection) {
    is PaymentRejection.CurrencyMismatch -> stringResource(
        R.string.project_payment_error_currency,
        BidiText.isolate(rejection.paymentCurrency),
        BidiText.isolate(rejection.billingCurrency),
    )
    PaymentRejection.ZeroAmount -> stringResource(R.string.project_payment_error_amount)
    PaymentRejection.NegativeAmount -> stringResource(R.string.project_payment_error_negative)
    is PaymentRejection.Overpayment -> stringResource(
        R.string.project_payment_error_overpayment,
        rejection.maximumAllowed.formatted(),
    )
    PaymentRejection.BillingRecordUnavailable -> stringResource(R.string.project_payment_error_no_record)
}

/** Why a billing record cannot be corrected. */
@Composable
fun correctionBlockedMessage(block: ProjectBillingCorrection.Block): String = when (block) {
    ProjectBillingCorrection.Block.HAS_PAYMENTS ->
        stringResource(R.string.project_billing_blocked_has_payments)
    ProjectBillingCorrection.Block.ALREADY_CANCELLED ->
        stringResource(R.string.project_billing_blocked_cancelled)
}

/**
 * How late the money is, or when it falls due. Days only — a due date is a
 * calendar fact, and counting in hours would read differently in each timezone.
 */
@Composable
fun dueStatusText(state: ProjectBillingState): String? {
    val days = state.daysOverdue
    if (days != null) {
        return if (days == 1L) {
            stringResource(R.string.project_billing_overdue_one_day)
        } else {
            stringResource(R.string.project_billing_overdue_days, days.toInt())
        }
    }
    val until = state.daysUntilDue ?: return null
    if (!state.outstanding.isPositive) return null
    return when {
        until == 0L -> stringResource(R.string.project_billing_due_today)
        until > 0L -> stringResource(R.string.project_billing_due_in_days, until.toInt())
        else -> null
    }
}

/** One payment in the history list, with a confirmed delete. */
@Composable
fun PaymentHistoryRow(
    payment: ProjectPayment,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable(payment.id) { mutableStateOf(false) }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.project_payment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.project_payment_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.project_payment_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.project_payment_delete_keep))
                }
            },
        )
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.project_payment_row,
                    payment.amount.formatted(),
                    payment.paidOn.formattedMedium(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val method = PaymentMethod.fromPersisted(payment.method)
            // The reference is isolated: it is the user's own token, often
            // digits and hyphens, and in Hebrew it would otherwise be reordered
            // by the method name beside it.
            val detail = listOfNotNull(
                method?.let { stringResource(paymentMethodLabel(it)) },
                payment.externalReference?.let { BidiText.isolate(it) },
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            payment.notes?.let { ProjectNoteText(it) }
        }
        TextButton(onClick = { confirming = true }) {
            Text(stringResource(R.string.project_payment_delete))
        }
    }
}
