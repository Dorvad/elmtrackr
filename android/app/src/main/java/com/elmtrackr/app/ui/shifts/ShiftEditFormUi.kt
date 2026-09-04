package com.elmtrackr.app.ui.shifts

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.domain.text.BidiText
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.common.durationText
import com.elmtrackr.app.domain.HoursFormatter
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.WeekendRules
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.projects.ProjectClockInOptions
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.auroraExpandable
import com.elmtrackr.app.ui.design.ElmChoiceChip
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmSegmentedPillRow
import com.elmtrackr.app.ui.refunds.RefundClaimsSection
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.tasks.TaskChipColorLeading
import com.elmtrackr.app.domain.tasks.TaskSorting
import com.elmtrackr.app.ui.tasks.parseTaskColor
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeBoxFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun dateSubtitleFmt(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", appLocale())

@Composable
private fun dateBoxFmt(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", appLocale())

private val payGradient = Brush.linearGradient(
    colorStops = arrayOf(
        0.0f to AuroraIndigo,
        0.5f to AuroraPlum,
        1.0f to AuroraAqua,
    ),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ShiftEditFormContent(
    navState: ShiftFormNavState,
    settings: UserSettings?,
    errors: Map<String, com.elmtrackr.app.domain.model.UiText>,
    featuresTravelRefunds: Boolean,
    onSave: (ShiftFormInput) -> Unit,
    onDelete: (shiftId: String) -> Unit,
    onClose: () -> Unit,
    onPickStartDate: () -> Unit,
    onPickStartTime: () -> Unit,
    onPickEndDate: () -> Unit,
    onPickEndTime: () -> Unit,
    startMillis: Long,
    endMillis: Long,
    hasEndTime: Boolean,
    onHasEndTimeChange: (Boolean) -> Unit,
    breakMinutes: Int,
    onBreakMinutesChange: (Int) -> Unit,
    notesText: String,
    onNotesChange: (String) -> Unit,
    premiumProfileId: String?,
    onPremiumProfileIdChange: (String?) -> Unit,
    premiumMode: String = "auto",
    onPremiumModeChange: (String) -> Unit = {},
    premiumProfiles: List<PremiumProfile>,
    profiles: List<CompensationProfile>,
    allShiftsForPay: List<Shift> = emptyList(),
    compensationProfileId: String?,
    onCompensationProfileIdChange: (String) -> Unit,
    onCreateCompensationProfile: ((name: String, onCreated: (String?) -> Unit) -> Unit)? = null,
    tasks: List<Task>,
    taskId: String?,
    onTaskIdChange: (String?) -> Unit,
    /**
     * Every project the user has. Empty when Paid Projects is off, which hides the
     * compensation-source controls entirely.
     */
    projects: List<com.elmtrackr.app.domain.model.Project> = emptyList(),
    compensationSource: CompensationSource = CompensationSource.EMPLOYEE,
    onCompensationSourceChange: (CompensationSource) -> Unit = {},
    projectId: String? = null,
    onProjectIdChange: (String?) -> Unit = {},
    showRefundSection: Boolean,
    initialShift: Shift?,
) {
    val isEdit = navState is ShiftFormNavState.Edit
    val zone = settings?.let { com.elmtrackr.app.domain.time.WorkTimezone.zoneFor(it) }
        ?: ZoneId.systemDefault()
    val startZdt = Instant.ofEpochMilli(startMillis).atZone(zone)
    // Resolved from displayCurrencyCode(), the rule Dashboard, Reports and the PDF
    // exporter all use, rather than reading settings.currency directly. The currency
    // is stored twice — an enum and a string — and these screens were the ones still
    // trusting the enum, so a code the enum cannot represent showed the shift screens
    // one currency and every other surface another.
    val currency = CurrencyCode.from(settings?.displayCurrencyCode())
    val haptic = LocalHapticFeedback.current

    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showRefundExpanded by rememberSaveable { mutableStateOf(false) }

    val activeTasks = TaskSorting.byRecency(tasks.filter { !it.isArchived })
    val selectedTask = activeTasks.firstOrNull { it.id == taskId }

    val unsavedCount = remember(
        navState, startMillis, endMillis, hasEndTime, breakMinutes, notesText, premiumProfileId,
        premiumMode, compensationProfileId, taskId, compensationSource, projectId,
    ) {
        countUnsavedChanges(
            navState = navState,
            startMillis = startMillis,
            endMillis = endMillis,
            hasEndTime = hasEndTime,
            breakMinutes = breakMinutes,
            notesText = notesText,
            premiumProfileId = premiumProfileId,
            premiumMode = premiumMode,
            compensationProfileId = compensationProfileId,
            taskId = taskId,
            compensationSource = compensationSource,
            projectId = projectId,
        )
    }

    fun buildInput() = ShiftFormInput(
        startTime = Instant.ofEpochMilli(startMillis),
        endTime = if (hasEndTime) Instant.ofEpochMilli(endMillis) else null,
        breakMinutes = breakMinutes,
        notes = notesText,
        premiumProfileId = if (premiumMode == "on") premiumProfileId else null,
        forceRegularRate = premiumMode == "off",
        refundAction = initialShift?.refundAction,
        compensationProfileId = compensationProfileId,
        taskId = taskId,
        compensationSource = compensationSource,
        projectId = projectId,
    )

    // Closing with edits pending must ask first. The form already counts and displays
    // them ("N unsaved changes"), but both exits — the back arrow and the back
    // gesture — used to discard silently. Settings guards its detail screens the same
    // way; this form is the one that did not.
    var pendingDiscard by remember { mutableStateOf(false) }
    fun requestClose() {
        if (unsavedCount > 0) pendingDiscard = true else onClose()
    }

    // Owned here rather than by ShiftsScreen so the guard covers the back gesture too.
    BackHandler { requestClose() }

    if (pendingDiscard) {
        AlertDialog(
            onDismissRequest = { pendingDiscard = false },
            title = { Text(stringResource(R.string.shifts_discard_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.shifts_discard_text,
                        unsavedCount,
                        unsavedCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscard = false
                    onClose()
                }) {
                    Text(
                        stringResource(R.string.shifts_discard_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = false }) {
                    Text(stringResource(R.string.shifts_discard_keep))
                }
            },
        )
    }

    if (showDeleteConfirm && isEdit) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.shifts_delete_dialog_title)) },
            text = { Text(stringResource(R.string.shifts_delete_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    AuroraHaptics.destructive(haptic)
                    onDelete((navState as ShiftFormNavState.Edit).shift.id)
                }) { Text(stringResource(R.string.shifts_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.shifts_cancel)) } },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // A save affordance is always pinned to the bottom edge; reserve
                // room for it so the last card is never hidden underneath.
                .padding(bottom = 96.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { requestClose() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.shifts_back))
                }
                Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                    Text(
                        if (isEdit) stringResource(R.string.shifts_edit_shift_title) else stringResource(R.string.shifts_new_shift_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        startZdt.format(dateSubtitleFmt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(CornerRadius.Small))
                            .clickable(onClick = onPickStartDate)
                            .padding(vertical = 4.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = Spacing.screenH),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                LivePayPreviewCard(
                    startMillis = startMillis,
                    endMillis = endMillis,
                    hasEndTime = hasEndTime,
                    breakMinutes = breakMinutes,
                    premiumProfileId = if (premiumMode == "on") premiumProfileId else null,
                    forceRegularRate = premiumMode == "off",
                    settings = settings,
                    profiles = profiles,
                    premiumProfiles = premiumProfiles,
                    allShiftsForPay = allShiftsForPay,
                    compensationProfileId = compensationProfileId,
                    selectedTask = selectedTask,
                    initialShift = initialShift,
                    currency = currency,
                )

                FormSectionCard(title = stringResource(R.string.shifts_section_when)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.Top,
                        ) {
                            DateTimeFieldGroup(
                                label = stringResource(R.string.shifts_start_label),
                                date = Instant.ofEpochMilli(startMillis).atZone(zone).format(dateBoxFmt()),
                                time = Instant.ofEpochMilli(startMillis).atZone(zone).format(timeBoxFmt),
                                onPickDate = onPickStartDate,
                                onPickTime = onPickStartTime,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .padding(top = 36.dp)
                                    .size(20.dp),
                            )
                            if (hasEndTime) {
                                DateTimeFieldGroup(
                                    label = stringResource(R.string.shifts_end_label),
                                    date = Instant.ofEpochMilli(endMillis).atZone(zone).format(dateBoxFmt()),
                                    time = Instant.ofEpochMilli(endMillis).atZone(zone).format(timeBoxFmt),
                                    onPickDate = onPickEndDate,
                                    onPickTime = onPickEndTime,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                ActiveEndPlaceholder(
                                    onEnableEnd = { onHasEndTimeChange(true) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    if (isEdit || hasEndTime) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.sm),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.shifts_still_on_shift),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = !hasEndTime,
                                onCheckedChange = { active -> onHasEndTimeChange(!active) },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    errors["endTime"]?.let { FormFieldError(it.asString()) }

                    val previewStart = Instant.ofEpochMilli(startMillis)
                    val previewEnd = if (hasEndTime) Instant.ofEpochMilli(endMillis) else null
                    if (previewEnd?.isAfter(previewStart) == true || !hasEndTime) {
                        val previewShift = Shift(
                            id = initialShift?.id ?: "preview",
                            userId = initialShift?.userId ?: "preview",
                            startTime = previewStart,
                            endTime = previewEnd,
                            breakMinutes = breakMinutes,
                            isSpecialDay = premiumProfileId != null,
                            premiumProfileId = premiumProfileId,
                        )
                        val workedMin = if (previewEnd != null) {
                            ShiftDurationCalculator.grossMinutes(previewShift)
                        } else null
                        val netMin = if (previewEnd != null) {
                            ShiftDurationCalculator.netMinutes(previewShift)
                        } else null
                        WhenSummaryFooter(
                            workedMinutes = workedMin,
                            breakMinutes = breakMinutes,
                            paidMinutes = netMin,
                        )
                    }
                }

                ShiftCompensationSourceSection(
                    projects = projects,
                    compensationSource = compensationSource,
                    onCompensationSourceChange = onCompensationSourceChange,
                    projectId = projectId,
                    onProjectIdChange = onProjectIdChange,
                    initialShift = initialShift,
                )

                if (activeTasks.isNotEmpty()) {
                    FormSectionCard(title = stringResource(R.string.shifts_task_label)) {
                        if (isEdit) {
                            Text(
                                stringResource(R.string.shifts_task_edit_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = taskId == null,
                                onClick = { onTaskIdChange(null) },
                                label = { Text(stringResource(R.string.shifts_task_none)) },
                            )
                            activeTasks.forEach { task ->
                                FilterChip(
                                    selected = taskId == task.id,
                                    onClick = { onTaskIdChange(task.id) },
                                    label = { Text("${task.icon} ${task.name}") },
                                    leadingIcon = {
                                        TaskChipColorLeading(parseTaskColor(task.color))
                                    },
                                )
                            }
                        }
                        if (isEdit && initialShift?.taskNameSnapshot != null &&
                            activeTasks.none { it.id == initialShift.taskId }
                        ) {
                            Text(
                                stringResource(
                                    R.string.shifts_saved_task,
                                    initialShift.taskIconSnapshot.orEmpty(),
                                    initialShift.taskNameSnapshot.toString(),
                                    initialShift.taskHourlyRateSnapshot.toString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                FormSectionCard(title = stringResource(R.string.shifts_section_rate)) {
                    if (profiles.isNotEmpty() || onCreateCompensationProfile != null) {
                        CompensationProfilePicker(
                            profiles = profiles,
                            selectedId = compensationProfileId,
                            onSelect = onCompensationProfileIdChange,
                            onCreateProfile = onCreateCompensationProfile,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    // The switch mirrors your weekend-days setting until you make
                    // an explicit choice for this shift; off always wins.
                    val weekendDays = remember(compensationProfileId, profiles, settings) {
                        (
                            profiles.firstOrNull { it.id == compensationProfileId }
                                ?: profiles.firstOrNull { it.isDefault }
                            )?.rules?.weekendDays
                            ?: settings?.weekendDays
                            ?: emptyList()
                    }
                    val calendarWeekend = remember(startMillis, weekendDays) {
                        WeekendRules.isWeekendDate(
                            Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate().toString(),
                            weekendDays,
                        )
                    }
                    val premiumOn = premiumMode == "on" || (premiumMode == "auto" && calendarWeekend)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.shifts_premium_toggle),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                stringResource(
                                    when {
                                        premiumMode == "off" && calendarWeekend -> R.string.shifts_premium_forced_off
                                        premiumMode == "auto" && premiumOn -> R.string.shifts_premium_auto_on
                                        premiumMode == "on" -> R.string.shifts_premium_on_hint
                                        else -> R.string.shifts_premium_auto_off
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = premiumOn,
                            onCheckedChange = { onPremiumModeChange(if (it) "on" else "off") },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (premiumOn) {
                        PremiumProfilePicker(
                            profiles = premiumProfiles,
                            selectedId = premiumProfileId,
                            onSelect = onPremiumProfileIdChange,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }

                FormSectionCard(title = stringResource(R.string.shifts_section_break)) {
                    BreakStepper(
                        minutes = breakMinutes,
                        onChange = onBreakMinutesChange,
                    )
                    errors["breakMinutes"]?.let { FormFieldError(it.asString()) }
                }

                FormSectionCard(title = stringResource(R.string.shifts_section_notes)) {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = onNotesChange,
                        placeholder = { Text(stringResource(R.string.shifts_notes_placeholder)) },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (showRefundSection && isEdit && initialShift != null) {
                    TravelRefundCard(
                        expanded = showRefundExpanded,
                        onToggle = { showRefundExpanded = !showRefundExpanded },
                    )
                    if (showRefundExpanded) {
                        RefundClaimsSection(
                            shift = initialShift,
                            currency = currency,
                        )
                        errors["refund"]?.let { FormFieldError(it.asString()) }
                    }
                }

                if (isEdit) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                    ) {
                        Text(stringResource(R.string.shifts_delete_shift), color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
            }
        }

        if (unsavedCount > 0) {
            FloatingSaveBar(
                unsavedCount = unsavedCount,
                onSave = {
                    AuroraHaptics.success(haptic)
                    onSave(buildInput())
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.md),
            ) {
                ElmGradientButton(onClick = {
                    AuroraHaptics.success(haptic)
                    onSave(buildInput())
                }) {
                    Text(stringResource(R.string.shifts_save), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LivePayPreviewCard(
    startMillis: Long,
    endMillis: Long,
    hasEndTime: Boolean,
    breakMinutes: Int,
    premiumProfileId: String?,
    forceRegularRate: Boolean = false,
    settings: UserSettings?,
    profiles: List<CompensationProfile>,
    premiumProfiles: List<PremiumProfile>,
    allShiftsForPay: List<Shift> = emptyList(),
    compensationProfileId: String?,
    selectedTask: Task?,
    initialShift: Shift?,
    currency: CurrencyCode,
) {
    val previewStart = Instant.ofEpochMilli(startMillis)
    val previewEnd = if (hasEndTime) Instant.ofEpochMilli(endMillis) else null
    if (previewEnd?.isAfter(previewStart) != true) return

    val previewShift = Shift(
        id = initialShift?.id ?: "preview",
        userId = initialShift?.userId ?: "preview",
        startTime = previewStart,
        endTime = previewEnd,
        breakMinutes = breakMinutes,
        isSpecialDay = premiumProfileId != null,
        premiumProfileId = premiumProfileId,
        forceRegularRate = forceRegularRate,
        compensationProfileId = compensationProfileId,
        taskId = selectedTask?.id,
        taskNameSnapshot = selectedTask?.name,
        taskIconSnapshot = selectedTask?.icon,
        taskHourlyRateSnapshot = selectedTask?.hourlyRate,
    )
    // Use the same month-shift context as the saved value so weekly overtime
    // tiers match what the shift will actually display after saving.
    val previewPay = settings?.let {
        PayrollCalculator.calculateShiftPayInContext(
            previewShift,
            allShiftsForPay.filter { s -> s.isCompleted },
            it,
            profiles,
            premiumProfiles,
        )
    } ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(payGradient, RoundedCornerShape(CornerRadius.Large))
                .padding(Spacing.lg),
        ) {
            Text(
                stringResource(R.string.shifts_estimated_pay_header),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                MoneyFormatter.format(previewPay.totalGross, currency, appLocale()),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
            previewPay.brackets.forEach { bracket ->
                val hours = HoursFormatter.decimal(bracket.minutes, appLocale())
                Text(
                    stringResource(R.string.shifts_pay_bracket_line, hours, bracket.label, MoneyFormatter.format(bracket.amount, currency, appLocale())),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * Whether this shift is paid as wages or tracked against a project, and which
 * project.
 *
 * Rendered only when Paid Projects is on and there is something to pick, or when
 * the shift already *is* project time — a shift that carries a project must keep
 * showing it even after the feature is switched off, so the user can see why it
 * is not in their pay.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftCompensationSourceSection(
    projects: List<com.elmtrackr.app.domain.model.Project>,
    compensationSource: CompensationSource,
    onCompensationSourceChange: (CompensationSource) -> Unit,
    projectId: String?,
    onProjectIdChange: (String?) -> Unit,
    initialShift: Shift?,
) {
    val clockable = remember(projects) { projects.filter(ProjectClockInOptions::isClockable) }
    // A project already linked to this shift stays selectable even if it is no
    // longer clockable, so editing an unrelated field cannot drop the link.
    val linked = remember(projects, projectId) { projects.firstOrNull { it.id == projectId } }
    val selectable = remember(clockable, linked) {
        (clockable + listOfNotNull(linked)).distinctBy { it.id }
    }
    if (selectable.isEmpty() && !compensationSource.isProjectTime) return

    FormSectionCard(title = stringResource(R.string.project_edit_source_title)) {
        val sources = listOf(CompensationSource.EMPLOYEE, CompensationSource.PROJECT)
        ElmSegmentedPillRow(
            options = sources.map { source ->
                stringResource(
                    when (source) {
                        CompensationSource.EMPLOYEE -> R.string.project_time_source_hourly
                        CompensationSource.PROJECT -> R.string.project_time_source_project
                    },
                )
            },
            selectedIndex = sources.indexOf(compensationSource).coerceAtLeast(0),
            enabledOptions = sources.map { it.isEmployeePaid || selectable.isNotEmpty() },
            onSelect = { index ->
                val source = sources[index]
                onCompensationSourceChange(source)
                if (source.isEmployeePaid) {
                    onProjectIdChange(null)
                } else if (projectId == null) {
                    onProjectIdChange(selectable.firstOrNull()?.id)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.project_edit_source_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (compensationSource.isProjectTime) {
            Spacer(Modifier.height(Spacing.sm))
            if (selectable.isEmpty()) {
                // Reachable when the module was switched off while this shift was
                // linked. The project row is not loaded, so the shift's own name
                // snapshot is what tells the user which project the hours are on.
                val snapshotName = initialShift?.projectNameSnapshot
                Text(
                    text = snapshotName
                        ?.let {
                            stringResource(
                                R.string.project_time_shift_label_named,
                                BidiText.isolate(it),
                            )
                        }
                        ?: stringResource(R.string.project_time_no_projects),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectable.forEach { project ->
                        ElmChoiceChip(
                            selected = projectId == project.id,
                            onClick = { onProjectIdChange(project.id) },
                        ) {
                            Text(
                                project.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // Reassigning is the one edit that moves hours between the two sides, so
        // say which totals will change before the user saves.
        val initialSource = initialShift?.compensationSource
        if (initialSource != null && initialSource != compensationSource) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(
                    if (compensationSource.isProjectTime) {
                        R.string.project_edit_to_project_warning
                    } else {
                        R.string.project_edit_to_hourly_warning
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            content()
        }
    }
}

@Composable
private fun DateTimeFieldGroup(
    label: String,
    date: String,
    time: String,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        DateTimePickerRow(
            value = date,
            icon = Icons.Filled.CalendarToday,
            contentDescription = stringResource(R.string.shifts_pick_date, label),
            onClick = onPickDate,
        )
        Spacer(Modifier.height(8.dp))
        DateTimePickerRow(
            value = time,
            icon = Icons.Filled.Schedule,
            contentDescription = stringResource(R.string.shifts_pick_time, label),
            onClick = onPickTime,
        )
    }
}

@Composable
private fun DateTimePickerRow(
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(CornerRadius.Medium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(auroraSurfaceSub())
            .clickable(onClick = onClick)
            .padding(start = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActiveEndPlaceholder(onEnableEnd: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CornerRadius.Medium)
    Column(
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(auroraSurfaceSub())
            .clickable(onClick = onEnableEnd)
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.shifts_end_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.shifts_active), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Text(stringResource(R.string.shifts_tap_to_set_end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WhenSummaryFooter(
    workedMinutes: Int?,
    breakMinutes: Int,
    paidMinutes: Int?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        val worked = workedMinutes?.let { durationText(it) } ?: "-"
        val paid = paidMinutes?.let { stringResource(R.string.shifts_hours_paid, HoursFormatter.decimal(it, appLocale())) } ?: "-"
        Text(
            stringResource(R.string.shifts_when_summary, worked, breakMinutes, paid),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun BreakStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.shifts_unpaid_break), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(label = "−", onClick = { onChange((minutes - 15).coerceAtLeast(0)) })
            Text(
                stringResource(R.string.shifts_break_minutes_value, minutes),
                modifier = Modifier.padding(horizontal = Spacing.md),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            StepperButton(label = "+", onClick = { onChange(minutes + 15) })
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(CornerRadius.Small)
    // The click target is the 48dp outer box; the bordered 36dp square stays the
    // visual. Growing the border instead would have changed the form's look to
    // fix an accessibility problem, which is the wrong trade.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TravelRefundCard(expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .auroraExpandable(expanded)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                Text(stringResource(R.string.shifts_travel_refund), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.shifts_travel_refund_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onToggle) {
                Text(if (expanded) stringResource(R.string.shifts_close) else stringResource(R.string.shifts_claim))
            }
        }
    }
}

@Composable
private fun FloatingSaveBar(
    unsavedCount: Int,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH, vertical = Spacing.md),
        shape = RoundedCornerShape(CornerRadius.Large),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    if (unsavedCount == 1) R.string.shifts_unsaved_changes_one else R.string.shifts_unsaved_changes_other,
                    unsavedCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ElmGradientButton(onClick = onSave, compact = true) {
                Text(stringResource(R.string.shifts_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FormFieldError(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun countUnsavedChanges(
    navState: ShiftFormNavState,
    startMillis: Long,
    endMillis: Long,
    hasEndTime: Boolean,
    breakMinutes: Int,
    notesText: String,
    premiumProfileId: String?,
    premiumMode: String = "auto",
    compensationProfileId: String?,
    taskId: String?,
    compensationSource: CompensationSource = CompensationSource.EMPLOYEE,
    projectId: String? = null,
): Int {
    val initial = when (navState) {
        is ShiftFormNavState.Create -> null
        is ShiftFormNavState.Edit -> navState.shift
    }
    if (initial == null) {
        var count = 0
        val now = Instant.now()
        if (kotlin.math.abs(startMillis - now.minusSeconds(3600).toEpochMilli()) > 60_000) count++
        if (hasEndTime && kotlin.math.abs(endMillis - now.toEpochMilli()) > 60_000) count++
        if (breakMinutes > 0) count++
        if (notesText.isNotBlank()) count++
        if (premiumProfileId != null) count++
        if (premiumMode == "off") count++
        if (compensationProfileId != null) count++
        if (taskId != null) count++
        if (compensationSource.isProjectTime) count++
        return count
    }
    var count = 0
    if (startMillis != initial.startTime.toEpochMilli()) count++
    val initialHasEnd = initial.isCompleted
    if (hasEndTime != initialHasEnd) count++
    if (hasEndTime && endMillis != (initial.endTime?.toEpochMilli() ?: 0L)) count++
    if (breakMinutes != initial.breakMinutes) count++
    if (notesText != (initial.notes ?: "")) count++
    if (premiumProfileId != initial.premiumProfileId) count++
    val initialMode = when {
        initial.forceRegularRate -> "off"
        initial.premiumProfileId != null -> "on"
        else -> "auto"
    }
    if (premiumMode != initialMode && !(premiumMode == "on" && initialMode == "on")) count++
    if (compensationProfileId != initial.compensationProfileId) count++
    if (taskId != initial.taskId) count++
    if (compensationSource != initial.compensationSource) count++
    if (projectId != initial.projectId) count++
    return count
}

internal fun shouldShowRefundSection(
    featuresTravelRefunds: Boolean,
    shift: Shift?,
): Boolean = featuresTravelRefunds && shift != null
