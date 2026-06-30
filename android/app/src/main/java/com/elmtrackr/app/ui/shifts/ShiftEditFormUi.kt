package com.elmtrackr.app.ui.shifts

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.refunds.RefundClaimsSection
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.tasks.TaskChipColorLeading
import com.elmtrackr.app.ui.tasks.TaskSorting
import com.elmtrackr.app.ui.tasks.parseTaskColor
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateSubtitleFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
private val dateBoxFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
private val timeBoxFmt = DateTimeFormatter.ofPattern("HH:mm")

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
    errors: Map<String, String>,
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
    isSpecialDay: Boolean,
    onSpecialDayChange: (Boolean) -> Unit,
    profiles: List<CompensationProfile>,
    compensationProfileId: String?,
    onCompensationProfileIdChange: (String?) -> Unit,
    tasks: List<Task>,
    taskId: String?,
    onTaskIdChange: (String?) -> Unit,
    showRefundSection: Boolean,
    initialShift: Shift?,
) {
    val isEdit = navState is ShiftFormNavState.Edit
    val zone = ZoneId.systemDefault()
    val startZdt = Instant.ofEpochMilli(startMillis).atZone(zone)
    val currency = settings?.currency ?: CurrencyCode.ILS

    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showRefundExpanded by rememberSaveable { mutableStateOf(false) }

    val activeTasks = TaskSorting.byRecency(tasks.filter { !it.isArchived })
    val selectedTask = activeTasks.firstOrNull { it.id == taskId }

    val unsavedCount = remember(
        navState, startMillis, endMillis, hasEndTime, breakMinutes, notesText, isSpecialDay,
        compensationProfileId, taskId,
    ) {
        countUnsavedChanges(
            navState = navState,
            startMillis = startMillis,
            endMillis = endMillis,
            hasEndTime = hasEndTime,
            breakMinutes = breakMinutes,
            notesText = notesText,
            isSpecialDay = isSpecialDay,
            compensationProfileId = compensationProfileId,
            taskId = taskId,
        )
    }

    fun buildInput() = ShiftFormInput(
        startTime = Instant.ofEpochMilli(startMillis),
        endTime = if (hasEndTime) Instant.ofEpochMilli(endMillis) else null,
        breakMinutes = breakMinutes,
        notes = notesText,
        isSpecialDay = isSpecialDay,
        refundAction = initialShift?.refundAction,
        compensationProfileId = compensationProfileId,
        taskId = taskId,
    )

    if (showDeleteConfirm && isEdit) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete shift?") },
            text = { Text("This shift will be removed. The deletion will sync when online.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete((navState as ShiftFormNavState.Edit).shift.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (unsavedCount > 0) 88.dp else Spacing.xl),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                    Text(
                        if (isEdit) "Edit shift" else "New shift",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        startZdt.format(dateSubtitleFmt),
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
                    isSpecialDay = isSpecialDay,
                    settings = settings,
                    profiles = profiles,
                    compensationProfileId = compensationProfileId,
                    selectedTask = selectedTask,
                    initialShift = initialShift,
                    currency = currency,
                )

                FormSectionCard(title = "WHEN") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.Top,
                        ) {
                            DateTimeFieldGroup(
                                label = "Start",
                                date = Instant.ofEpochMilli(startMillis).atZone(zone).format(dateBoxFmt),
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
                                    label = "End",
                                    date = Instant.ofEpochMilli(endMillis).atZone(zone).format(dateBoxFmt),
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
                                "Still on shift",
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

                    errors["endTime"]?.let { FormFieldError(it) }

                    val previewStart = Instant.ofEpochMilli(startMillis)
                    val previewEnd = if (hasEndTime) Instant.ofEpochMilli(endMillis) else null
                    if (previewEnd?.isAfter(previewStart) == true || !hasEndTime) {
                        val previewShift = Shift(
                            id = initialShift?.id ?: "preview",
                            userId = initialShift?.userId ?: "preview",
                            startTime = previewStart,
                            endTime = previewEnd,
                            breakMinutes = breakMinutes,
                            isSpecialDay = isSpecialDay,
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

                FormSectionCard(title = "BREAK & DAY TYPE") {
                    BreakStepper(
                        minutes = breakMinutes,
                        onChange = onBreakMinutesChange,
                    )
                    errors["breakMinutes"]?.let { FormFieldError(it) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Holiday / Shabbat", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Higher-rate or special day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = isSpecialDay, onCheckedChange = onSpecialDayChange)
                    }
                }

                if (profiles.isNotEmpty() || activeTasks.isNotEmpty()) {
                    FormSectionCard(title = "PAY & TASK") {
                        if (profiles.isNotEmpty()) {
                            CompensationProfilePicker(
                                profiles = profiles,
                                selectedId = compensationProfileId,
                                onSelect = { onCompensationProfileIdChange(it) },
                            )
                        }
                        if (activeTasks.isNotEmpty()) {
                            if (profiles.isNotEmpty()) Spacer(Modifier.height(Spacing.sm))
                            Text("Task", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (isEdit) {
                                Text(
                                    "Change which task this shift used — pay updates when you save.",
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
                                    label = { Text("None") },
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
                                    "Saved task: ${initialShift.taskIconSnapshot.orEmpty()} ${initialShift.taskNameSnapshot} (${initialShift.taskHourlyRateSnapshot}/hr)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }

                FormSectionCard(title = "NOTES") {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = onNotesChange,
                        placeholder = { Text("Add notes about this shift…") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium),
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
                        errors["refund"]?.let { FormFieldError(it) }
                    }
                }

                if (isEdit) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                    ) {
                        Text("Delete shift", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
            }
        }

        if (unsavedCount > 0) {
            FloatingSaveBar(
                unsavedCount = unsavedCount,
                onSave = { onSave(buildInput()) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.md),
            ) {
                ElmGradientButton(onClick = { onSave(buildInput()) }) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
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
    isSpecialDay: Boolean,
    settings: UserSettings?,
    profiles: List<CompensationProfile>,
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
        isSpecialDay = isSpecialDay,
        compensationProfileId = compensationProfileId,
        taskId = selectedTask?.id,
        taskNameSnapshot = selectedTask?.name,
        taskIconSnapshot = selectedTask?.icon,
        taskHourlyRateSnapshot = selectedTask?.hourlyRate,
    )
    val previewPay = settings?.let {
        PayrollCalculator.calculateShiftPay(previewShift, it, profiles)
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
                "ESTIMATED PAY • UPDATES AS YOU EDIT",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                MoneyFormatter.format(previewPay.totalGross, currency),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
            previewPay.brackets.forEach { bracket ->
                val hours = formatHoursDecimal(bracket.minutes)
                Text(
                    "${hours}h ${bracket.label} - ${MoneyFormatter.format(bracket.amount, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
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
            contentDescription = "Pick $label date",
            onClick = onPickDate,
        )
        Spacer(Modifier.height(8.dp))
        DateTimePickerRow(
            value = time,
            icon = Icons.Filled.Schedule,
            contentDescription = "Pick $label time",
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
        Text("End", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Active", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Text("Tap to set end", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val worked = workedMinutes?.let { ShiftDurationCalculator.formatMinutes(it) } ?: "-"
        val paid = paidMinutes?.let { formatHoursDecimal(it) + "h paid" } ?: "-"
        Text(
            "$worked worked • ${breakMinutes}m break • $paid",
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
        Text("Unpaid break", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(label = "−", onClick = { onChange((minutes - 15).coerceAtLeast(0)) })
            Text(
                "$minutes min",
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
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                Text("Travel refund", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Claim a ride to or from work",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onToggle) {
                Text(if (expanded) "Close" else "Claim")
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
                "$unsavedCount unsaved change${if (unsavedCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ElmGradientButton(onClick = onSave, compact = true) {
                Text("Save", fontWeight = FontWeight.SemiBold)
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
    isSpecialDay: Boolean,
    compensationProfileId: String?,
    taskId: String?,
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
        if (isSpecialDay) count++
        if (compensationProfileId != null) count++
        if (taskId != null) count++
        return count
    }
    var count = 0
    if (startMillis != initial.startTime.toEpochMilli()) count++
    val initialHasEnd = initial.isCompleted
    if (hasEndTime != initialHasEnd) count++
    if (hasEndTime && endMillis != (initial.endTime?.toEpochMilli() ?: 0L)) count++
    if (breakMinutes != initial.breakMinutes) count++
    if (notesText != (initial.notes ?: "")) count++
    if (isSpecialDay != initial.isSpecialDay) count++
    if (compensationProfileId != initial.compensationProfileId) count++
    if (taskId != initial.taskId) count++
    return count
}

internal fun shouldShowRefundSection(
    featuresTravelRefunds: Boolean,
    shift: Shift?,
): Boolean {
    if (!featuresTravelRefunds || shift == null || !shift.isCompleted) return false
    val toEligibility = RefundPolicy.checkToWorkEligibility(shift)
    val fromEligibility = RefundPolicy.checkFromWorkEligibility(shift)
    return toEligibility.eligible || fromEligibility.eligible
}
