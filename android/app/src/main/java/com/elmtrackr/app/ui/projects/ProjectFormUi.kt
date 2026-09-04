package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.money.CurrencyScales
import com.elmtrackr.app.domain.projects.ProjectAmountEntryMode
import com.elmtrackr.app.domain.projects.ProjectFormField
import com.elmtrackr.app.domain.projects.ProjectFormInput
import com.elmtrackr.app.domain.projects.ProjectFormValidator
import com.elmtrackr.app.domain.text.BidiText
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.settings.SettingsDetailHeader
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing
import java.time.LocalDate
import java.util.Currency

/**
 * Create and edit form.
 *
 * Every field lives in a `rememberSaveable` string, so a half-typed amount
 * survives rotation and process recreation. Validation is shown inline on the
 * offending field rather than in a dialog.
 */
@Composable
fun ProjectFormScreen(
    existing: Project?,
    initialInput: ProjectFormInput,
    isBilled: Boolean,
    isSaving: Boolean,
    onSave: (ProjectFormInput) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(initialInput.name) }
    var clientName by rememberSaveable(existing?.id) { mutableStateOf(initialInput.clientName) }
    var description by rememberSaveable(existing?.id) { mutableStateOf(initialInput.description) }
    var currencyCode by rememberSaveable(existing?.id) { mutableStateOf(initialInput.currencyCode) }
    var amountText by rememberSaveable(existing?.id) { mutableStateOf(initialInput.amountText) }
    var entryModeName by rememberSaveable(existing?.id) { mutableStateOf(initialInput.entryMode.name) }
    var taxEnabled by rememberSaveable(existing?.id) { mutableStateOf(initialInput.taxEnabled) }
    var taxLabel by rememberSaveable(existing?.id) { mutableStateOf(initialInput.taxLabel) }
    var taxRateText by rememberSaveable(existing?.id) { mutableStateOf(initialInput.taxRateText) }
    var hourBudgetText by rememberSaveable(existing?.id) { mutableStateOf(initialInput.hourBudgetText) }
    var targetRateText by rememberSaveable(existing?.id) { mutableStateOf(initialInput.targetHourlyRateText) }
    var startDateEpoch by rememberSaveable(existing?.id) {
        mutableStateOf(initialInput.startDate?.toEpochDay())
    }
    var deadlineEpoch by rememberSaveable(existing?.id) {
        mutableStateOf(initialInput.deadline?.toEpochDay())
    }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(initialInput.notes) }
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }
    // Errors appear only after a save attempt, so a fresh form is not covered in
    // red before the user has typed anything.
    var showErrors by rememberSaveable(existing?.id) { mutableStateOf(false) }

    val input = ProjectFormInput(
        name = name,
        clientName = clientName,
        description = description,
        currencyCode = currencyCode,
        amountText = amountText,
        entryMode = ProjectAmountEntryMode.entries.firstOrNull { it.name == entryModeName }
            ?: ProjectAmountEntryMode.FEE_BEFORE_TAX,
        taxEnabled = taxEnabled,
        taxLabel = taxLabel,
        taxRateText = taxRateText,
        hourBudgetText = hourBudgetText,
        targetHourlyRateText = targetRateText,
        startDate = startDateEpoch?.let { LocalDate.ofEpochDay(it) },
        deadline = deadlineEpoch?.let { LocalDate.ofEpochDay(it) },
        notes = notes,
        workStatus = initialInput.workStatus,
    )
    val validation = remember(input) { ProjectFormValidator.validate(input) }

    // One comparison against the untouched input is the whole dirty check — the form
    // already rebuilds `input` from its fields on every recomposition.
    val requestClose = rememberProjectDiscardGuard(
        isDirty = input != initialInput,
        onConfirmClose = onBack,
    )

    @Composable
    fun errorFor(field: ProjectFormField): String? =
        validation[field]?.takeIf { showErrors }?.let { stringResource(ProjectLabels.formError(it)) }

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
        modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            SettingsDetailHeader(
                title = stringResource(
                    if (existing == null) R.string.project_form_new_title else R.string.project_form_edit_title,
                ),
                onBack = requestClose,
            )
        }

        if (isBilled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.project_form_billed_warning),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
        }

        item {
            ProjectSectionCard(stringResource(R.string.project_form_section_basics)) {
                ProjectTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.project_form_name),
                    error = errorFor(ProjectFormField.NAME),
                )
                Spacer(Modifier.height(Spacing.sm))
                ProjectTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = stringResource(R.string.project_form_client),
                    error = errorFor(ProjectFormField.CLIENT_NAME),
                )
                Spacer(Modifier.height(Spacing.sm))
                ProjectTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.project_form_description),
                    singleLine = false,
                )
            }
        }

        item {
            ProjectSectionCard(stringResource(R.string.project_form_section_amount)) {
                ProjectInfoRow(
                    label = stringResource(R.string.project_form_currency),
                    value = currencyCode,
                    emphasis = true,
                )
                OutlinedButton(
                    onClick = { showCurrencyPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.project_form_currency_change)) }
                errorFor(ProjectFormField.CURRENCY)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(Spacing.xs))
                ProjectNoteText(
                    stringResource(
                        R.string.project_form_currency_note,
                        BidiText.isolate(currencyCode),
                    ),
                )

                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(R.string.project_form_entry_mode),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ProjectAmountEntryMode.entries.forEach { mode ->
                        val selected = mode.name == entryModeName
                        Card(
                            onClick = { entryModeName = mode.name },
                            modifier = Modifier.weight(1f).then(
                                if (selected) {
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(CornerRadius.Medium),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        ) {
                            Text(
                                text = stringResource(
                                    if (mode == ProjectAmountEntryMode.FEE_BEFORE_TAX) {
                                        R.string.project_form_entry_fee
                                    } else {
                                        R.string.project_form_entry_total
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.sm))
                ProjectTextField(
                    value = amountText,
                    onValueChange = { amountText = it.decimalOnly() },
                    label = stringResource(
                        if (input.entryMode == ProjectAmountEntryMode.CLIENT_TOTAL) {
                            R.string.project_form_amount_total_label
                        } else {
                            R.string.project_form_amount_fee_label
                        },
                    ),
                    error = errorFor(ProjectFormField.AMOUNT),
                    keyboardType = KeyboardType.Decimal,
                )

                input.feePreview()?.let { fee ->
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        stringResource(R.string.project_form_breakdown_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    ProjectFeeBreakdown(fee)
                }
            }
        }

        item {
            ProjectSectionCard(stringResource(R.string.project_form_section_tax)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.project_form_tax_enabled),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = taxEnabled, onCheckedChange = { taxEnabled = it })
                }
                // With tax off there is nothing to configure, so the label and
                // rate fields stay out of the way entirely.
                if (taxEnabled) {
                    Spacer(Modifier.height(Spacing.sm))
                    ProjectTextField(
                        value = taxLabel,
                        onValueChange = { taxLabel = it },
                        label = stringResource(R.string.project_form_tax_label),
                        placeholder = stringResource(R.string.project_form_tax_label_hint),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    ProjectTextField(
                        value = taxRateText,
                        onValueChange = { taxRateText = it.decimalOnly() },
                        label = stringResource(R.string.project_form_tax_rate),
                        error = errorFor(ProjectFormField.TAX_RATE),
                        keyboardType = KeyboardType.Decimal,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    ProjectNoteText(stringResource(R.string.project_form_tax_note))
                }
            }
        }

        item {
            ProjectSectionCard(stringResource(R.string.project_form_section_time)) {
                ProjectTextField(
                    value = hourBudgetText,
                    onValueChange = { hourBudgetText = it.decimalOnly() },
                    label = stringResource(R.string.project_form_hour_budget),
                    error = errorFor(ProjectFormField.HOUR_BUDGET),
                    keyboardType = KeyboardType.Decimal,
                )
                Spacer(Modifier.height(Spacing.sm))
                ProjectTextField(
                    value = targetRateText,
                    onValueChange = { targetRateText = it.decimalOnly() },
                    label = stringResource(R.string.project_form_target_rate),
                    error = errorFor(ProjectFormField.TARGET_RATE),
                    keyboardType = KeyboardType.Decimal,
                )
            }
        }

        item {
            ProjectSectionCard(stringResource(R.string.project_form_section_dates)) {
                ProjectDateRow(
                    label = stringResource(R.string.project_form_start_date),
                    date = input.startDate,
                    onChange = { startDateEpoch = it?.toEpochDay() },
                )
                ProjectDateRow(
                    label = stringResource(R.string.project_form_deadline),
                    date = input.deadline,
                    onChange = { deadlineEpoch = it?.toEpochDay() },
                    error = errorFor(ProjectFormField.DEADLINE),
                )
            }
        }

        item {
            ProjectSectionCard(stringResource(R.string.project_form_section_notes)) {
                ProjectTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = stringResource(R.string.project_form_notes),
                    singleLine = false,
                )
            }
        }

        item {
            ElmGradientButton(
                onClick = {
                    showErrors = true
                    if (validation.isValid) onSave(input)
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.project_form_save), fontWeight = FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
internal fun ProjectTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = error?.let { { Text(it) } },
        isError = error != null,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/**
 * Date row with a plain picker dialog and a clear action, so an optional date
 * can be removed as easily as it is set.
 */
@Composable
internal fun ProjectDateRow(
    label: String,
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    error: String? = null,
) {
    var showPicker by rememberSaveable(label) { mutableStateOf(false) }
    if (showPicker) {
        ProjectDatePickerDialog(
            initial = date,
            onSelect = {
                onChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = date?.formattedMedium() ?: stringResource(R.string.project_form_date_none),
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            error?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = { showPicker = true }) { Text(label) }
        if (date != null) {
            TextButton(onClick = { onChange(null) }) {
                Text(stringResource(R.string.project_form_date_clear))
            }
        }
    }
}

/** Searchable ISO 4217 picker; the app's own currencies float to the top. */
@Composable
internal fun CurrencyPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val preferred = com.elmtrackr.app.domain.model.CurrencyCode.entries.map { it.name }
    val all = remember {
        Currency.getAvailableCurrencies()
            .map { it.currencyCode }
            .filter { CurrencyScales.isValidCode(it) }
            .sorted()
    }
    val ordered = remember(all) { preferred + all.filterNot { it in preferred } }
    val filtered = ordered.filter { it.contains(query.trim().uppercase()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_form_currency)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.project_form_currency_search)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(filtered, key = { it }) { code ->
                        TextButton(
                            onClick = { onSelect(code) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = code,
                                fontWeight = if (code == selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.project_form_date_clear)) }
        },
    )
}

/** Keeps only digits and a single decimal point, matching the onboarding inputs. */
internal fun String.decimalOnly(): String = buildString {
    var hasDecimal = false
    this@decimalOnly.forEach { char ->
        when {
            char.isDigit() -> append(char)
            (char == '.' || char == ',') && !hasDecimal && isNotEmpty() -> {
                append('.')
                hasDecimal = true
            }
        }
    }
}
