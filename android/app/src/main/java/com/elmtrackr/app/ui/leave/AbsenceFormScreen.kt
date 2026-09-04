package com.elmtrackr.app.ui.leave

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.leave.LeaveConflict
import com.elmtrackr.app.domain.leave.LeaveEstimateGap
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraScreen
import com.elmtrackr.app.ui.design.AuroraScreenHeader
import com.elmtrackr.app.ui.design.ElmCardPadded
import com.elmtrackr.app.ui.design.ElmDecimalField
import com.elmtrackr.app.ui.design.ElmDropdownField
import com.elmtrackr.app.ui.design.ElmEmptyState
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmLoadingState
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.ElmSegmentedPillRow
import com.elmtrackr.app.ui.design.ElmTextField
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSecondaryText
import com.elmtrackr.app.ui.theme.auroraSemantics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.elmtrackr.app.ui.common.appLocale

/**
 * Reporting a vacation or a sick period.
 *
 * The screen's job is to be honest about three things at once: which days are
 * being claimed, what each is estimated to pay, and where the app is guessing. A
 * sick period shows its day numbers because those drive the pay ladder, and a day
 * the engine cannot value says so and offers a field rather than showing zero.
 */
@Composable
fun AbsenceFormScreen(
    type: AbsenceType,
    onClose: () -> Unit,
    editingEventId: String? = null,
    viewModel: AbsenceFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val closed by viewModel.closed.collectAsState()
    val message by viewModel.userMessage.collectAsState()

    LaunchedEffect(type, editingEventId) { viewModel.start(type, editingEventId) }
    LaunchedEffect(closed) { if (closed) onClose() }
    BackHandler { onClose() }

    when (val current = state) {
        is AbsenceFormUiState.Loading -> ElmLoadingState(Modifier.fillMaxWidth())

        is AbsenceFormUiState.Error -> ErrorState(
            message = current.message,
            onRetry = { viewModel.start(type, editingEventId) },
        )

        is AbsenceFormUiState.NoWorkplace -> AuroraScreen {
            AuroraScreenHeader(
                title = titleFor(type),
                onBack = onClose,
                backContentDescription = stringResource(R.string.leave_form_back),
            )
            ElmEmptyState(
                icon = Icons.Outlined.EventBusy,
                title = titleFor(type),
                subtitle = stringResource(R.string.leave_form_no_workplace),
            )
        }

        is AbsenceFormUiState.Ready -> AbsenceFormContent(
            state = current,
            messageText = message?.asString(),
            onMessageShown = viewModel::consumeUserMessage,
            onClose = onClose,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun AbsenceFormContent(
    state: AbsenceFormUiState.Ready,
    messageText: String?,
    onMessageShown: () -> Unit,
    onClose: () -> Unit,
    viewModel: AbsenceFormViewModel,
) {
    var showCalculation by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    LaunchedEffect(messageText) { if (messageText != null) onMessageShown() }

    AuroraScreen {
        AuroraScreenHeader(
            title = titleFor(state.type),
            onBack = onClose,
            backContentDescription = stringResource(R.string.leave_form_back),
        )

        if (state.showWorkplacePicker) {
            ElmCardPadded {
                ElmDropdownField(
                    label = stringResource(R.string.leave_form_workplace),
                    selected = state.selectedWorkplaceId,
                    options = state.workplaces.map { it.id },
                    onSelect = viewModel::selectWorkplace,
                    displayName = { id -> state.workplaces.firstOrNull { it.id == id }?.name.orEmpty() },
                )
            }
        }

        // ── When ──────────────────────────────────────────────────────────────
        ElmSectionHeader(stringResource(R.string.leave_form_when))
        ElmCardPadded {
            ElmSegmentedPillRow(
                options = listOf(
                    stringResource(R.string.leave_form_length_single),
                    stringResource(R.string.leave_form_length_range),
                ),
                selectedIndex = if (state.isRange) 1 else 0,
                onSelect = { index -> viewModel.setRange(index == 1) },
            )
            Spacer(Modifier.height(Layout.rowGap))
            val startLabel = when {
                !state.isRange -> stringResource(R.string.leave_form_date)
                state.type == AbsenceType.SICK -> stringResource(R.string.leave_form_first_day)
                else -> stringResource(R.string.leave_form_start_date)
            }
            DateRow(
                label = startLabel,
                value = state.startDate.format(dateFormat),
                onClick = { pickingStart = true },
            )
            if (state.isRange) {
                Spacer(Modifier.height(Layout.rowGap))
                DateRow(
                    label = stringResource(R.string.leave_form_end_date),
                    value = state.endDate.format(dateFormat),
                    onClick = { pickingEnd = true },
                )
            }
        }

        // ── Duration ──────────────────────────────────────────────────────────
        ElmSectionHeader(stringResource(R.string.leave_form_duration))
        ElmCardPadded {
            ElmSegmentedPillRow(
                options = listOf(
                    stringResource(R.string.leave_form_full_day),
                    stringResource(R.string.leave_form_partial_day),
                ),
                selectedIndex = if (state.fullDay) 0 else 1,
                onSelect = { index -> viewModel.setFullDay(index == 0) },
            )
            if (!state.fullDay) {
                Spacer(Modifier.height(Layout.rowGap))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ElmDecimalField(
                        label = if (state.partialUnit == LeaveBalanceUnit.HOURS) {
                            stringResource(R.string.leave_form_amount_hours)
                        } else {
                            stringResource(R.string.leave_form_amount_days)
                        },
                        value = state.partialAmount,
                        onValueChange = viewModel::setPartialAmount,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Layout.inlineGap))
                    ElmSegmentedPillRow(
                        options = listOf(
                            stringResource(R.string.leave_form_amount_days),
                            stringResource(R.string.leave_form_amount_hours),
                        ),
                        selectedIndex = if (state.partialUnit == LeaveBalanceUnit.HOURS) 1 else 0,
                        onSelect = { index ->
                            viewModel.setPartialUnit(
                                if (index == 1) LeaveBalanceUnit.HOURS else LeaveBalanceUnit.DAYS,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Affected days ─────────────────────────────────────────────────────
        ElmSectionHeader(stringResource(R.string.leave_form_affected_days))
        ElmCardPadded {
            Text(
                text = stringResource(R.string.leave_form_affected_days_help),
                style = MaterialTheme.typography.bodySmall,
                color = auroraSecondaryText(),
            )
            if (state.days.none { it.selected }) {
                Spacer(Modifier.height(Spacing.s6))
                Text(
                    text = stringResource(R.string.leave_form_no_pattern),
                    style = MaterialTheme.typography.bodySmall,
                    color = auroraSemantics.warningInk,
                )
            }
            Spacer(Modifier.height(Layout.rowGap))
            state.days.forEachIndexed { index, day ->
                if (index > 0) {
                    HorizontalDivider(
                        Modifier.padding(vertical = Spacing.s6),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                DayRow(
                    day = day,
                    isSick = state.type == AbsenceType.SICK,
                    currencyCode = state.currencyCode,
                    dateText = day.date.format(dateFormat),
                    onToggle = { viewModel.toggleDay(day.date) },
                    onManualAmount = { amount -> viewModel.setManualAmount(day.date, amount) },
                )
            }
        }

        // ── Estimate ──────────────────────────────────────────────────────────
        ElmCardPadded {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.leave_form_estimated_total),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.isEstimating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Spacing.s16),
                        strokeWidth = Spacing.s2,
                    )
                } else {
                    Text(
                        // Prefixed everywhere it appears: this is an estimate from the
                        // settings and history the user gave us, not a payroll figure.
                        text = stringResource(
                            R.string.leave_history_estimated,
                            MoneyFormatter.format(state.estimatedTotal, state.currencyCode, appLocale()),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s4))
            TextButton(
                onClick = { showCalculation = true },
                modifier = Modifier.heightIn(min = Layout.minTouchTarget),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(Spacing.s16))
                Spacer(Modifier.width(Spacing.s4))
                Text(stringResource(R.string.leave_form_how_calculated))
            }
            Text(
                text = stringResource(R.string.leave_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = auroraSecondaryText(),
            )
        }

        // ── Conflicts ─────────────────────────────────────────────────────────
        val warnings = state.conflicts.filter { it !is LeaveConflict.AdjacentSickPeriod }
        if (warnings.isNotEmpty()) {
            ElmCardPadded {
                warnings.distinctBy { it::class to it.date }.forEach { conflict ->
                    Text(
                        text = conflictText(conflict, conflict.date.format(dateFormat)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (conflict is LeaveConflict.DuplicateLeave) {
                            MaterialTheme.colorScheme.error
                        } else {
                            auroraSemantics.warningInk
                        },
                    )
                }
            }
        }

        // ── Note ──────────────────────────────────────────────────────────────
        ElmCardPadded {
            ElmTextField(
                label = stringResource(R.string.leave_form_note),
                value = state.notes,
                onValueChange = viewModel::setNotes,
                singleLine = false,
            )
        }

        state.validationError?.let { error ->
            Text(
                text = error.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        ElmGradientButton(onClick = viewModel::save, enabled = state.canSave) {
            Text(stringResource(R.string.leave_form_save), fontWeight = FontWeight.SemiBold)
        }
        if (state.editingEventId != null) {
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Layout.minTouchTarget),
            ) {
                Text(stringResource(R.string.leave_form_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(Layout.bottomNavInset))
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (pickingStart || pickingEnd) {
        AbsenceDatePickerDialog(
            initial = if (pickingStart) state.startDate else state.endDate,
            onConfirm = { date ->
                if (pickingStart) viewModel.setStartDate(date) else viewModel.setEndDate(date)
                pickingStart = false
                pickingEnd = false
            },
            onDismiss = {
                pickingStart = false
                pickingEnd = false
            },
        )
    }

    if (showCalculation) {
        CalculationExplanationDialog(
            days = state.selectedDays,
            currencyCode = state.currencyCode,
            onDismiss = { showCalculation = false },
        )
    }

    // Offered, never applied on its own: merging rewrites the sick-day numbers the
    // user has already seen, and two separate illnesses a day apart do happen.
    state.mergeOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMergeOffer,
            title = { Text(stringResource(R.string.leave_conflict_merge_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.leave_conflict_merge_body,
                        offer.existingStart.format(dateFormat),
                        offer.existingEnd.format(dateFormat),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::mergeWithAdjacent) {
                    Text(stringResource(R.string.leave_conflict_merge_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMergeOffer) {
                    Text(stringResource(R.string.leave_conflict_merge_keep))
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.leave_form_delete_title)) },
            text = { Text(stringResource(R.string.leave_form_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(R.string.leave_form_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.leave_form_cancel))
                }
            },
        )
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Layout.minTouchTarget)
            .auroraRowClickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(Layout.inlineGap))
        Icon(
            Icons.Outlined.CalendarMonth,
            contentDescription = stringResource(R.string.leave_form_pick_date, label),
            modifier = Modifier.size(Spacing.s20),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * One candidate day.
 *
 * A sick day shows its ordinal and its percentage because that is what decides
 * the money, and someone reconciling a payslip needs to see "sick day 2, 50%"
 * rather than only a number. A day with no estimate gets a field, never a zero.
 */
@Composable
private fun DayRow(
    day: AbsenceDayRow,
    isSick: Boolean,
    currencyCode: String,
    dateText: String,
    onToggle: () -> Unit,
    onManualAmount: (String) -> Unit,
) {
    val stateLabel = if (day.selected) {
        stringResource(R.string.leave_a11y_day_included, dateText)
    } else {
        stringResource(R.string.leave_a11y_day_excluded, dateText)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Layout.minTouchTarget)
                // The whole row toggles, announced once, with the checkbox as the
                // visual state only. A bare Checkbox announces "checkbox, not
                // checked" with no idea which day it means.
                .toggleable(
                    value = day.selected,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .clearAndSetSemantics { contentDescription = stateLabel },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = day.selected, onCheckedChange = null)
            Spacer(Modifier.width(Layout.inlineGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(dateText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (isSick && day.sickDayOrdinal != null) {
                    val percent = day.multiplier?.let { (it * 100).toInt() }
                    Text(
                        text = buildString {
                            append(stringResource(R.string.leave_form_sick_day, day.sickDayOrdinal))
                            if (percent != null) {
                                append(" · ")
                                append(stringResource(R.string.leave_form_pays_percent, percent))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = auroraSecondaryText(),
                    )
                }
            }
            when {
                day.estimatedGross != null -> Text(
                    text = MoneyFormatter.format(day.estimatedGross, currencyCode, appLocale()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                day.gap != null -> Text(
                    text = stringResource(R.string.leave_history_needs_value),
                    style = MaterialTheme.typography.labelSmall,
                    color = auroraSemantics.warningInk,
                    textAlign = TextAlign.End,
                )
            }
        }
        if (day.selected && day.gap != null) {
            Text(
                text = gapText(day.gap),
                style = MaterialTheme.typography.bodySmall,
                color = auroraSecondaryText(),
            )
            Spacer(Modifier.height(Spacing.s4))
            ElmDecimalField(
                label = stringResource(R.string.leave_gap_action_amount),
                value = day.manualAmount,
                onValueChange = onManualAmount,
                supportingText = stringResource(R.string.leave_gap_saved_anyway),
            )
        }
    }
}

@Composable
private fun titleFor(type: AbsenceType): String = when (type) {
    AbsenceType.VACATION -> stringResource(R.string.leave_form_title_vacation)
    AbsenceType.SICK -> stringResource(R.string.leave_form_title_sick)
}

@Composable
private fun gapText(gap: LeaveEstimateGap): String = stringResource(
    when (gap) {
        LeaveEstimateGap.NO_POLICY -> R.string.leave_gap_no_policy
        LeaveEstimateGap.LEAVE_TYPE_DISABLED -> R.string.leave_gap_disabled
        LeaveEstimateGap.NO_WAGE -> R.string.leave_gap_no_wage
        LeaveEstimateGap.NO_PAY_HISTORY -> R.string.leave_gap_no_history
        LeaveEstimateGap.NO_EXPECTED_HOURS -> R.string.leave_gap_no_hours
        LeaveEstimateGap.NO_STANDARD_DAY -> R.string.leave_gap_no_standard_day
        LeaveEstimateGap.NO_MATCHING_TIER -> R.string.leave_gap_no_tier
        LeaveEstimateGap.NO_MANUAL_AMOUNT -> R.string.leave_gap_no_manual_amount
    },
)

@Composable
private fun conflictText(conflict: LeaveConflict, dateText: String): String = when (conflict) {
    is LeaveConflict.DuplicateLeave -> stringResource(R.string.leave_conflict_duplicate, dateText)
    is LeaveConflict.WorkShiftSameDate -> stringResource(R.string.leave_conflict_shift, dateText)
    is LeaveConflict.ArchivedWorkplace -> stringResource(R.string.leave_conflict_archived)
    is LeaveConflict.AdjacentSickPeriod -> ""
}
