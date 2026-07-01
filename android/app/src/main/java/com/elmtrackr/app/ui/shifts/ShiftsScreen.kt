package com.elmtrackr.app.ui.shifts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.ElmEmptyState
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth

@Composable
fun ShiftsScreen(
    viewModel: ShiftsViewModel = viewModel(factory = ShiftsViewModel.Factory),
    pendingEditShiftId: String? = null,
    onPendingEditConsumed: () -> Unit = {},
    onFormVisibilityChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val formTarget by viewModel.formTarget.collectAsState()
    val formErrors by viewModel.formErrors.collectAsState()
    val featuresTravelRefunds by viewModel.featuresTravelRefunds.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()

    LaunchedEffect(formTarget) {
        onFormVisibilityChanged(formTarget != null)
    }

    LaunchedEffect(pendingEditShiftId) {
        pendingEditShiftId?.let { shiftId ->
            viewModel.showEditForm(shiftId)
            onPendingEditConsumed()
        }
    }

    BackHandler(enabled = formTarget != null) { viewModel.closeForm() }

    if (formTarget != null) {
        ShiftFormContent(
            navState = formTarget!!,
            settings = (uiState as? ShiftsUiState.Ready)?.settings,
            profiles = (uiState as? ShiftsUiState.Ready)?.profiles.orEmpty(),
            tasks = (uiState as? ShiftsUiState.Ready)?.tasks.orEmpty(),
            errors = formErrors,
            featuresTravelRefunds = featuresTravelRefunds,
            onSuggestTaskForStart = viewModel::suggestTaskForStart,
            onSave = { input ->
                when (val t = formTarget!!) {
                    is ShiftFormNavState.Create -> viewModel.createShift(input)
                    is ShiftFormNavState.Edit -> viewModel.saveEditedShift(t.shift.id, input)
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

    AuroraListScreen {
        when (val state = uiState) {
            is ShiftsUiState.Loading -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH),
            ) {
                ShiftsPageHeader(onAddShift = viewModel::showCreateForm)
                ShiftsMonthPicker(
                    month = selectedMonth,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
                ShiftsSkeleton()
            }

            is ShiftsUiState.Empty -> Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .padding(horizontal = Spacing.screenH),
            ) {
                ShiftsPageHeader(onAddShift = viewModel::showCreateForm)
                ShiftsMonthPicker(
                    month = selectedMonth,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
                ShiftsHeroSummaryCard(
                    shifts = emptyList(),
                    activeShift = null,
                    settings = null,
                    month = selectedMonth,
                )
                Spacer(Modifier.height(Spacing.md))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ElmEmptyState(
                        icon = Icons.Filled.AccessTime,
                        title = "No shifts this month",
                        subtitle = "Clock in from the home screen or add a shift manually.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ShiftsAddPastShiftButton(
                    onClick = viewModel::showCreateForm,
                    modifier = Modifier.padding(bottom = Spacing.xl),
                )
            }

            is ShiftsUiState.Ready -> ShiftsListContent(
                state = state,
                selectedMonth = selectedMonth,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onAddShift = viewModel::showCreateForm,
                onEditShift = viewModel::showEditForm,
            )

            is ShiftsUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::retry,
            )
        }
    }
}

@Composable
private fun ShiftsListContent(
    state: ShiftsUiState.Ready,
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddShift: () -> Unit,
    onEditShift: (String) -> Unit,
) {
    val weekSections = ShiftWeekGrouper.groupByWeek(
        shifts = state.shifts,
        activeShift = state.activeShift,
        month = selectedMonth,
        settings = state.settings,
        profiles = state.profiles,
    )
    var entranceIndex = 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ShiftsPageHeader(onAddShift = onAddShift) }
        item {
            ShiftsMonthPicker(
                month = selectedMonth,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
            )
        }
        item {
            ShiftsHeroSummaryCard(
                shifts = state.shifts,
                activeShift = state.activeShift,
                settings = state.settings,
                month = selectedMonth,
                profiles = state.profiles,
            )
        }

        weekSections.forEach { section ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(com.elmtrackr.app.ui.theme.CornerRadius.Medium),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column {
                        ShiftsWeekSectionHeader(section = section, settings = state.settings)
                        section.shifts.forEach { shift ->
                            val rowIndex = entranceIndex++
                            ShiftRow(
                                shift = shift,
                                settings = state.settings,
                                profiles = state.profiles,
                                allShiftsForPay = state.shifts,
                                showRefunds = state.featuresTravelRefunds,
                                grouped = true,
                                entranceIndex = rowIndex,
                                onClick = { onEditShift(shift.id) },
                            )
                        }
                    }
                }
            }
        }

        item {
            ShiftsAddPastShiftButton(
                onClick = onAddShift,
                modifier = Modifier.padding(bottom = Spacing.xl),
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ShiftFormContent(
    navState: ShiftFormNavState,
    settings: com.elmtrackr.app.domain.model.UserSettings?,
    profiles: List<com.elmtrackr.app.domain.model.CompensationProfile>,
    tasks: List<com.elmtrackr.app.domain.model.Task>,
    errors: Map<String, String>,
    featuresTravelRefunds: Boolean,
    onSuggestTaskForStart: suspend (Instant) -> String? = { null },
    onSave: (ShiftFormInput) -> Unit,
    onDelete: (shiftId: String) -> Unit,
    onClose: () -> Unit,
) {
    val initialShift = (navState as? ShiftFormNavState.Edit)?.shift
    val zone = ZoneId.systemDefault()
    val now = Instant.now()

    val defaultStart = initialShift?.startTime ?: now.minusSeconds(3600)
    val defaultEnd = initialShift?.endTime ?: now

    var startMillis by rememberSaveable { mutableStateOf(defaultStart.toEpochMilli()) }
    var hasEndTime by rememberSaveable { mutableStateOf(initialShift?.isCompleted ?: true) }
    var endMillis by rememberSaveable { mutableStateOf(defaultEnd.toEpochMilli()) }
    var breakMinutes by rememberSaveable { mutableStateOf(initialShift?.breakMinutes ?: 0) }
    var notesText by rememberSaveable { mutableStateOf(initialShift?.notes ?: "") }
    var isSpecialDay by rememberSaveable { mutableStateOf(initialShift?.isSpecialDay ?: false) }
    var compensationProfileId by rememberSaveable {
        mutableStateOf(initialShift?.compensationProfileId ?: settings?.defaultCompensationProfileId)
    }
    var taskId by rememberSaveable { mutableStateOf(initialShift?.taskId) }

    LaunchedEffect(navState, startMillis) {
        if (navState is ShiftFormNavState.Create && taskId == null) {
            taskId = onSuggestTaskForStart(Instant.ofEpochMilli(startMillis))
        }
    }

    var showStartDatePicker by rememberSaveable { mutableStateOf(false) }
    var showStartTimePicker by rememberSaveable { mutableStateOf(false) }
    var showEndDatePicker by rememberSaveable { mutableStateOf(false) }
    var showEndTimePicker by rememberSaveable { mutableStateOf(false) }

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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ShiftEditFormContent(
            navState = navState,
            settings = settings,
            errors = errors,
            featuresTravelRefunds = featuresTravelRefunds,
            onSave = onSave,
            onDelete = onDelete,
            onClose = onClose,
            onPickStartDate = { showStartDatePicker = true },
            onPickStartTime = { showStartTimePicker = true },
            onPickEndDate = { showEndDatePicker = true },
            onPickEndTime = { showEndTimePicker = true },
            startMillis = startMillis,
            endMillis = endMillis,
            hasEndTime = hasEndTime,
            onHasEndTimeChange = { hasEndTime = it },
            breakMinutes = breakMinutes,
            onBreakMinutesChange = { breakMinutes = it },
            notesText = notesText,
            onNotesChange = { notesText = it },
            isSpecialDay = isSpecialDay,
            onSpecialDayChange = { isSpecialDay = it },
            profiles = profiles,
            compensationProfileId = compensationProfileId,
            onCompensationProfileIdChange = { compensationProfileId = it },
            tasks = tasks,
            taskId = taskId,
            onTaskIdChange = { taskId = it },
            showRefundSection = shouldShowRefundSection(featuresTravelRefunds, initialShift),
            initialShift = initialShift,
        )
    }
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
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerWrapper(
    currentMillis: Long,
    zone: ZoneId,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val zdt = Instant.ofEpochMilli(currentMillis).atZone(zone)
    val state = rememberTimePickerState(initialHour = zdt.hour, initialMinute = zdt.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun applyDate(currentMillis: Long, utcMidnightMillis: Long, zone: ZoneId): Long {
    val currentTime = Instant.ofEpochMilli(currentMillis).atZone(zone).toLocalTime()
    val newDate = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return LocalDateTime.of(newDate, currentTime).atZone(zone).toInstant().toEpochMilli()
}

private fun applyTime(currentMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val currentDate = Instant.ofEpochMilli(currentMillis).atZone(zone).toLocalDate()
    return LocalDateTime.of(currentDate, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

@Preview(showBackground = true)
@Composable
private fun ShiftsScreenPreview() {
    ElmTrackrTheme {
        Column(Modifier.padding(Spacing.md)) {
            ShiftsPageHeader(onAddShift = {})
            ShiftsMonthPicker(month = YearMonth.now(), onPrevious = {}, onNext = {})
            Spacer(Modifier.height(Spacing.md))
            ElmEmptyState(
                icon = Icons.Filled.AccessTime,
                title = "No shifts this month",
                subtitle = "Clock in from the home screen or add a shift manually.",
            )
        }
    }
}
