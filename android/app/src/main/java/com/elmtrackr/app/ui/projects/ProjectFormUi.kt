package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.elmtrackr.app.ui.design.ElmSegmentedPillRow
import com.elmtrackr.app.ui.settings.SettingsDetailHeader
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout
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

        // Everything about the money, in one card and in the order the questions
        // come: what currency, which figure you know, the figure, whether tax is
        // added, and what that comes to. Tax used to be a card of its own — a
        // whole white box for one switch, sitting below the amount it changes
        // and above the breakdown that depends on it.
        item {
            ProjectSectionCard(stringResource(R.string.project_form_section_amount)) {
                ProjectPickerRow(
                    label = stringResource(R.string.project_form_currency),
                    value = currencyCode,
                    placeholder = "",
                    onClick = { showCurrencyPicker = true },
                    error = errorFor(ProjectFormField.CURRENCY),
                )
                ProjectNoteText(
                    stringResource(
                        R.string.project_form_currency_note,
                        BidiText.isolate(currencyCode),
                    ),
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )

                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(R.string.project_form_entry_mode),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.xs))
                // The house segmented control rather than two bordered cards.
                // The cards were the size of buttons for a choice between two
                // words, and the selected one repeated, verbatim, the label of
                // the field directly beneath it.
                val modes = ProjectAmountEntryMode.entries
                ElmSegmentedPillRow(
                    options = modes.map { mode ->
                        stringResource(
                            if (mode == ProjectAmountEntryMode.FEE_BEFORE_TAX) {
                                R.string.project_form_entry_fee
                            } else {
                                R.string.project_form_entry_total
                            },
                        )
                    },
                    selectedIndex = modes.indexOfFirst { it.name == entryModeName }.coerceAtLeast(0),
                    onSelect = { entryModeName = modes[it].name },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Spacing.sm))
                // Labelled by its currency, not by the mode: the control above
                // already says which amount this is, and the breakdown below
                // names both in full.
                ProjectTextField(
                    value = amountText,
                    onValueChange = { amountText = it.decimalOnly() },
                    label = stringResource(
                        R.string.project_form_amount_in,
                        BidiText.isolate(currencyCode),
                    ),
                    error = errorFor(ProjectFormField.AMOUNT),
                    keyboardType = KeyboardType.Decimal,
                )

                Spacer(Modifier.height(Spacing.xs))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.project_form_tax_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = taxEnabled, onCheckedChange = { taxEnabled = it })
                }
                // With tax off there is nothing to configure, so the label and
                // rate fields stay out of the way entirely.
                if (taxEnabled) {
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        ProjectTextField(
                            value = taxLabel,
                            onValueChange = { taxLabel = it },
                            label = stringResource(R.string.project_form_tax_label),
                            placeholder = stringResource(R.string.project_form_tax_label_hint),
                            modifier = Modifier.weight(1.4f),
                        )
                        ProjectTextField(
                            value = taxRateText,
                            onValueChange = { taxRateText = it.decimalOnly() },
                            label = stringResource(R.string.project_form_tax_rate),
                            error = errorFor(ProjectFormField.TAX_RATE),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    ProjectNoteText(stringResource(R.string.project_form_tax_note))
                }

                // No heading. Three labelled amounts directly under the field
                // that produces them do not need a word above them saying they
                // are a breakdown.
                input.feePreview()?.let { fee ->
                    Spacer(Modifier.height(Spacing.sm))
                    ProjectFeeBreakdown(fee)
                }
            }
        }

        item {
            ProjectSectionCard(stringResource(R.string.project_form_section_time)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ProjectTextField(
                        value = hourBudgetText,
                        onValueChange = { hourBudgetText = it.decimalOnly() },
                        label = stringResource(R.string.project_form_hour_budget),
                        error = errorFor(ProjectFormField.HOUR_BUDGET),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    ProjectTextField(
                        value = targetRateText,
                        onValueChange = { targetRateText = it.decimalOnly() },
                        label = stringResource(R.string.project_form_target_rate),
                        error = errorFor(ProjectFormField.TARGET_RATE),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
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
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = error?.let { { Text(it) } },
        isError = error != null,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/**
 * One date, as a row that is itself the control.
 *
 * It used to print its own label twice — once beside the value and again as the
 * text button that opened the picker — so a blank form read "Start date / Not
 * set … Start date", and the only thing that looked tappable was the copy that
 * said nothing about what tapping would do. Now the row opens the picker and
 * Clear appears only once there is something to clear.
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
    ProjectPickerRow(
        label = label,
        value = date?.formattedMedium(),
        placeholder = stringResource(R.string.project_form_date_none),
        onClick = { showPicker = true },
        error = error,
        trailing = if (date == null) {
            null
        } else {
            {
                val clearLabel = stringResource(R.string.project_form_date_clear_a11y, label)
                IconButton(
                    onClick = { onChange(null) },
                    modifier = Modifier.size(Layout.minTouchTarget),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = clearLabel,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(Layout.inlineIcon),
                    )
                }
            }
        },
    )
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
            // Was the *date* clear string, so the currency picker's only button
            // read "Clear" — which sounds like it empties the field it is
            // attached to rather than closing the dialog.
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.project_form_cancel)) }
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
