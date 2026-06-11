package com.elmtrackr.app.ui.shifts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.design.ElmEmptyState
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraWeekendBg
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateLongFmt  = DateTimeFormatter.ofPattern("d MMM yyyy")
private val timeFmt      = DateTimeFormatter.ofPattern("HH:mm")
private val dayNumFmt    = DateTimeFormatter.ofPattern("dd")
private val weekdayFmt   = DateTimeFormatter.ofPattern("EEE")

@Composable
fun ShiftsScreen(
    viewModel: ShiftsViewModel = viewModel(factory = ShiftsViewModel.Factory),
) {
    val uiState            by viewModel.uiState.collectAsState()
    val formTarget         by viewModel.formTarget.collectAsState()
    val formErrors         by viewModel.formErrors.collectAsState()
    val featuresTravelRefunds by viewModel.featuresTravelRefunds.collectAsState()

    BackHandler(enabled = formTarget != null) { viewModel.closeForm() }

    if (formTarget != null) {
        ShiftFormContent(
            navState = formTarget!!,
            errors   = formErrors,
            featuresTravelRefunds = featuresTravelRefunds,
            onSave = { input ->
                when (val t = formTarget!!) {
                    is ShiftFormNavState.Create -> viewModel.createShift(input)
                    is ShiftFormNavState.Edit   -> viewModel.saveEditedShift(t.shift.id, input)
                }
            },
            onDelete = { shiftId ->
                viewModel.deleteShift(shiftId)
                viewModel.closeForm()
            },
            onClose = viewModel::closeForm,
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is ShiftsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuroraIndigo)
            }
            is ShiftsUiState.Empty -> ShiftsEmptyContent(onAddShift = viewModel::showCreateForm)
            is ShiftsUiState.Ready -> ShiftsListContent(
                state      = state,
                onAddShift = viewModel::showCreateForm,
                onEditShift = viewModel::showEditForm,
            )
            is ShiftsUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

// ── List ──────────────────────────────────────────────────────────────────────

@Composable
private fun ShiftsEmptyContent(onAddShift: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        ElmEmptyState(
            icon     = Icons.Filled.AccessTime,
            title    = "No shifts yet",
            subtitle = "Clock in from Dashboard, or tap + to add manually",
            modifier = Modifier.align(Alignment.Center),
        )
        ElmFab(
            onClick  = onAddShift,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun ShiftsListContent(
    state: ShiftsUiState.Ready,
    onAddShift: () -> Unit,
    onEditShift: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            items(state.shifts, key = { it.id }) { shift ->
                ShiftRow(shift = shift, onClick = { onEditShift(shift.id) })
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
        ElmFab(
            onClick  = onAddShift,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun ElmFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick            = onClick,
        modifier           = modifier,
        containerColor     = AuroraIndigo,
        contentColor       = Color.White,
        elevation          = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = "New shift")
    }
}

@Composable
private fun ShiftRow(shift: Shift, onClick: () -> Unit) {
    val zone         = ZoneId.systemDefault()
    val zdt          = shift.startTime.atZone(zone)
    val dayNumber    = zdt.format(dayNumFmt)
    val weekday      = zdt.format(weekdayFmt).uppercase(Locale.getDefault())
    val startText    = zdt.format(timeFmt)
    val endText      = shift.endTime?.atZone(zone)?.format(timeFmt) ?: "Active"
    val durationText = if (shift.isCompleted)
        ShiftDurationCalculator.netMinutes(shift)?.let { ShiftDurationCalculator.formatMinutes(it) }
    else null

    val stripeColor = when {
        shift.isActive    -> Color(0xFF10B981)  // emerald active
        shift.isSpecialDay -> AuroraPlum
        else              -> AuroraIndigo.copy(alpha = 0.35f)
    }

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left colour stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )

            // Date block
            Column(
                modifier            = Modifier
                    .width(54.dp)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text       = dayNumber,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text       = weekday,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = AuroraFaint,
                )
            }

            // Main info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text       = "$startText — $endText",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                if (shift.isActive || shift.isSpecialDay || shift.breakMinutes > 0) {
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (shift.isActive) {
                            ActiveBadge()
                        }
                        if (shift.isSpecialDay) {
                            SpecialBadge()
                        }
                        if (shift.breakMinutes > 0 && !shift.isActive) {
                            Text(
                                text  = "${shift.breakMinutes}m brk",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraFaint,
                            )
                        }
                    }
                }
            }

            // Duration
            if (durationText != null) {
                Text(
                    text       = durationText,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.padding(end = 4.dp),
                )
            }

            // Chevron
            Icon(
                imageVector        = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint               = AuroraFaint,
                modifier           = Modifier.size(18.dp).padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun SpecialBadge() {
    Box(
        modifier = Modifier
            .background(AuroraWeekendBg, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text       = "Special",
            style      = MaterialTheme.typography.labelSmall,
            color      = AuroraPlum,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActiveBadge() {
    Box(
        modifier = Modifier
            .background(AuroraIndigo.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text       = "● Active",
            style      = MaterialTheme.typography.labelSmall,
            color      = AuroraIndigo,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Form ──────────────────────────────────────────────────────────────────────

@Composable
private fun ShiftFormContent(
    navState: ShiftFormNavState,
    errors: Map<String, String>,
    featuresTravelRefunds: Boolean,
    onSave: (ShiftFormInput) -> Unit,
    onDelete: (shiftId: String) -> Unit,
    onClose: () -> Unit,
) {
    val isEdit       = navState is ShiftFormNavState.Edit
    val initialShift = (navState as? ShiftFormNavState.Edit)?.shift
    val zone         = ZoneId.systemDefault()
    val now          = Instant.now()

    val defaultStart = initialShift?.startTime ?: now.minusSeconds(3600)
    val defaultEnd   = initialShift?.endTime   ?: now

    var startMillis       by rememberSaveable { mutableStateOf(defaultStart.toEpochMilli()) }
    var hasEndTime        by rememberSaveable { mutableStateOf(initialShift?.isCompleted ?: true) }
    var endMillis         by rememberSaveable { mutableStateOf(defaultEnd.toEpochMilli()) }
    var breakText         by rememberSaveable { mutableStateOf((initialShift?.breakMinutes ?: 0).toString()) }
    var notesText         by rememberSaveable { mutableStateOf(initialShift?.notes ?: "") }
    var isSpecialDay      by rememberSaveable { mutableStateOf(initialShift?.isSpecialDay ?: false) }
    var refundActionName  by rememberSaveable { mutableStateOf<String?>(initialShift?.refundAction?.name) }

    var showStartDatePicker by rememberSaveable { mutableStateOf(false) }
    var showStartTimePicker by rememberSaveable { mutableStateOf(false) }
    var showEndDatePicker   by rememberSaveable { mutableStateOf(false) }
    var showEndTimePicker   by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm   by rememberSaveable { mutableStateOf(false) }

    if (showStartDatePicker) {
        DatePickerWrapper(
            currentMillis = startMillis,
            onConfirm = { utcMidnight -> startMillis = applyDate(startMillis, utcMidnight, zone); showStartDatePicker = false },
            onDismiss = { showStartDatePicker = false },
        )
    }
    if (showStartTimePicker) {
        TimePickerWrapper(
            currentMillis = startMillis,
            zone = zone,
            onConfirm = { h, m -> startMillis = applyTime(startMillis, h, m, zone); showStartTimePicker = false },
            onDismiss = { showStartTimePicker = false },
        )
    }
    if (showEndDatePicker) {
        DatePickerWrapper(
            currentMillis = endMillis,
            onConfirm = { utcMidnight -> endMillis = applyDate(endMillis, utcMidnight, zone); showEndDatePicker = false },
            onDismiss = { showEndDatePicker = false },
        )
    }
    if (showEndTimePicker) {
        TimePickerWrapper(
            currentMillis = endMillis,
            zone = zone,
            onConfirm = { h, m -> endMillis = applyTime(endMillis, h, m, zone); showEndTimePicker = false },
            onDismiss = { showEndTimePicker = false },
        )
    }

    if (showDeleteConfirm && isEdit) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete shift?") },
            text  = { Text("This shift will be removed. The deletion will sync when online.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete((navState as ShiftFormNavState.Edit).shift.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = AuroraInk2)
                }
                Text(
                    text       = if (isEdit) "Edit Shift" else "New Shift",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier            = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ElmSectionHeader("Start Time")
                DateTimeRow(
                    dateLabel = "Date", timeLabel = "Time",
                    millis = startMillis, zone = zone,
                    onPickDate = { showStartDatePicker = true },
                    onPickTime = { showStartTimePicker = true },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    ElmSectionHeader("End Time", modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text  = if (hasEndTime) "Set" else "Active / no end",
                            style = MaterialTheme.typography.bodySmall,
                            color = AuroraInk2,
                        )
                        Switch(
                            checked         = hasEndTime,
                            onCheckedChange = { hasEndTime = it },
                            modifier        = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (hasEndTime) {
                    DateTimeRow(
                        dateLabel = "Date", timeLabel = "Time",
                        millis = endMillis, zone = zone,
                        onPickDate = { showEndDatePicker = true },
                        onPickTime = { showEndTimePicker = true },
                    )
                }
                errors["endTime"]?.let { FieldError(it) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value         = breakText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) breakText = it },
                    label         = { Text("Break (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                errors["breakMinutes"]?.let { FieldError(it) }

                OutlinedTextField(
                    value         = notesText,
                    onValueChange = { notesText = it },
                    label         = { Text("Notes (optional)") },
                    minLines      = 2,
                    maxLines      = 4,
                    modifier      = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Special day", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text  = "Weekend, holiday, or higher-rate day",
                            style = MaterialTheme.typography.bodySmall,
                            color = AuroraInk2,
                        )
                    }
                    Switch(checked = isSpecialDay, onCheckedChange = { isSpecialDay = it })
                }

                if (featuresTravelRefunds) {
                    ElmSectionHeader("Travel Refund")
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RefundAction.entries.forEach { action ->
                            val label = when (action) {
                                RefundAction.NO_RIDE_TAKEN -> "No ride"
                                RefundAction.REMIND_LATER  -> "Remind later"
                                RefundAction.SUBMITTED     -> "Submitted"
                            }
                            FilterChip(
                                selected  = refundActionName == action.name,
                                onClick   = {
                                    refundActionName = if (refundActionName == action.name) null else action.name
                                },
                                label     = { Text(label) },
                                modifier  = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                ElmGradientButton(
                    onClick = {
                        onSave(
                            ShiftFormInput(
                                startTime    = Instant.ofEpochMilli(startMillis),
                                endTime      = if (hasEndTime) Instant.ofEpochMilli(endMillis) else null,
                                breakMinutes = breakText.toIntOrNull() ?: 0,
                                notes        = notesText,
                                isSpecialDay = isSpecialDay,
                                refundAction = refundActionName?.let { RefundAction.valueOf(it) },
                            )
                        )
                    },
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }

                if (isEdit) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete shift")
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DateTimeRow(
    dateLabel: String,
    timeLabel: String,
    millis: Long,
    zone: ZoneId,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val zdt = Instant.ofEpochMilli(millis).atZone(zone)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value         = zdt.format(dateLongFmt),
            onValueChange = {},
            readOnly      = true,
            label         = { Text(dateLabel) },
            trailingIcon  = {
                IconButton(onClick = onPickDate) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = "Pick date", modifier = Modifier.size(18.dp))
                }
            },
            modifier = Modifier.weight(1.5f),
        )
        OutlinedTextField(
            value         = zdt.format(timeFmt),
            onValueChange = {},
            readOnly      = true,
            label         = { Text(timeLabel) },
            trailingIcon  = {
                IconButton(onClick = onPickTime) {
                    Icon(Icons.Filled.Schedule, contentDescription = "Pick time", modifier = Modifier.size(18.dp))
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FieldError(message: String) {
    Text(
        text     = message,
        style    = MaterialTheme.typography.bodySmall,
        color    = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerWrapper(currentMillis: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val initUtcMidnight = Instant.ofEpochMilli(currentMillis)
        .atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initUtcMidnight)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton    = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerWrapper(currentMillis: Long, zone: ZoneId, onConfirm: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val zdt   = Instant.ofEpochMilli(currentMillis).atZone(zone)
    val state = rememberTimePickerState(initialHour = zdt.hour, initialMinute = zdt.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text  = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun applyDate(currentMillis: Long, utcMidnightMillis: Long, zone: ZoneId): Long {
    val currentTime = Instant.ofEpochMilli(currentMillis).atZone(zone).toLocalTime()
    val newDate     = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return LocalDateTime.of(newDate, currentTime).atZone(zone).toInstant().toEpochMilli()
}

private fun applyTime(currentMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val currentDate = Instant.ofEpochMilli(currentMillis).atZone(zone).toLocalDate()
    return LocalDateTime.of(currentDate, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

@Preview(showBackground = true)
@Composable
private fun ShiftsScreenPreview() {
    ElmTrackrTheme { ShiftsEmptyContent(onAddShift = {}) }
}
