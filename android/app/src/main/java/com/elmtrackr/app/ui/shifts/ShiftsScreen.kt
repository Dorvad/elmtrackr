package com.elmtrackr.app.ui.shifts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.ui.common.AppTimePickerDialog
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.AuroraStateCrossfade
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.design.auroraSubScreenTransition
import com.elmtrackr.app.ui.design.ElmEmptyState
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import androidx.compose.runtime.CompositionLocalProvider
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.ui.common.LocalWorkZone
import com.elmtrackr.app.ui.leave.AbsenceFormScreen
import com.elmtrackr.app.ui.leave.ReportEntryTypeSheet
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth

@Composable
fun ShiftsScreen(
    viewModel: ShiftsViewModel = hiltViewModel(),
    pendingEditShiftId: String? = null,
    onPendingEditConsumed: () -> Unit = {},
    onFormVisibilityChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val motionEnabled = auroraMotionEnabled()
    val formTarget by viewModel.formTarget.collectAsState()
    val formErrors by viewModel.formErrors.collectAsState()
    val featuresTravelRefunds by viewModel.featuresTravelRefunds.collectAsState()
    val formProjects by viewModel.formProjects.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // The manual-add action now asks what kind of entry this is. Clock in is
    // untouched — this only stands where "add a shift manually" used to.
    //
    // The absence type is held as a String because rememberSaveable cannot put an
    // enum in a Bundle, the same reason ShiftFormContent stores its nullable
    // fields as "".
    var showEntryTypeSheet by rememberSaveable { mutableStateOf(false) }
    var absenceTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    val absenceType = absenceTypeName?.let(AbsenceType::fromPersisted)
    val openEntryTypeSheet = { showEntryTypeSheet = true }

    val userMessageText = userMessage?.asString()
    LaunchedEffect(userMessageText) {
        userMessageText?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUserMessage()
        }
    }

    LaunchedEffect(formTarget, absenceType) {
        onFormVisibilityChanged(formTarget != null || absenceType != null)
    }

    LaunchedEffect(pendingEditShiftId) {
        pendingEditShiftId?.let { shiftId ->
            viewModel.showEditForm(shiftId)
            onPendingEditConsumed()
        }
    }

    // The open form owns the back gesture (see ShiftEditFormContent): it must be able to
    // intercept it and confirm before discarding unsaved edits.

    if (absenceType != null) {
        // Full screen, and it owns its own back handling, so it replaces the list
        // rather than sitting over it.
        AbsenceFormScreen(type = absenceType, onClose = { absenceTypeName = null })
        return
    }

    Box(Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = formTarget,
        transitionSpec = { auroraSubScreenTransition(targetState != null, motionEnabled) },
        modifier = Modifier.fillMaxSize(),
        label = "shifts-form",
    ) { navState ->
        if (navState != null) {
            ShiftFormContent(
                navState = navState,
                settings = (uiState as? ShiftsUiState.Ready)?.settings,
                premiumProfiles = (uiState as? ShiftsUiState.Ready)?.premiumProfiles.orEmpty(),
                profiles = (uiState as? ShiftsUiState.Ready)?.profiles.orEmpty(),
                allShiftsForPay = (uiState as? ShiftsUiState.Ready)?.shifts.orEmpty(),
                tasks = (uiState as? ShiftsUiState.Ready)?.tasks.orEmpty(),
                projects = formProjects,
                errors = formErrors,
                featuresTravelRefunds = featuresTravelRefunds,
                onSuggestTaskForStart = viewModel::suggestTaskForStart,
                onSave = { input ->
                    when (navState) {
                        is ShiftFormNavState.Create -> viewModel.createShift(input)
                        is ShiftFormNavState.Edit -> viewModel.saveEditedShift(navState.shift.id, input)
                    }
                },
                onDelete = { shiftId ->
                    viewModel.deleteShift(shiftId)
                    viewModel.closeForm()
                },
                onClose = viewModel::closeForm,
                onCreateCompensationProfile = viewModel::createCompensationProfile,
            )
        } else {
            AuroraListScreen {
                AuroraStateCrossfade(
                    targetState = uiState,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { state ->
                        when (state) {
                            is ShiftsUiState.Loading -> "loading"
                            is ShiftsUiState.Empty -> "empty-${state.month}"
                            is ShiftsUiState.Ready -> "ready-${state.month}"
                            is ShiftsUiState.Error -> "error"
                        }
                    },
                ) { state ->
                    when (state) {
                        is ShiftsUiState.Loading -> Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.screenH),
                        ) {
                            ShiftsPageHeader(onAddShift = openEntryTypeSheet)
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
                            ShiftsPageHeader(onAddShift = openEntryTypeSheet)
                            Spacer(Modifier.height(Spacing.sm))
                            ShiftsHeroSummaryCard(
                                shifts = emptyList(),
                                activeShift = null,
                                settings = null,
                                month = state.month,
                                onPreviousMonth = viewModel::previousMonth,
                                onNextMonth = viewModel::nextMonth,
                            )
                            Spacer(Modifier.height(Spacing.md))
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                ElmEmptyState(
                                    icon = Icons.Filled.AccessTime,
                                    title = stringResource(R.string.shifts_empty_title),
                                    subtitle = stringResource(R.string.shifts_empty_subtitle),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            ShiftsAddPastShiftButton(
                                onClick = openEntryTypeSheet,
                                modifier = Modifier.padding(bottom = Spacing.xl),
                            )
                        }

                        is ShiftsUiState.Ready -> ShiftsListContent(
                            state = state,
                            onPreviousMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                            onAddShift = openEntryTypeSheet,
                            onEditShift = viewModel::showEditForm,
                        )

                        is ShiftsUiState.Error -> ErrorState(
                            message = state.message,
                            onRetry = viewModel::retry,
                        )
                    }
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = Spacing.md),
    )
    }

    if (showEntryTypeSheet) {
        ReportEntryTypeSheet(
            onDismiss = { showEntryTypeSheet = false },
            onWork = {
                showEntryTypeSheet = false
                viewModel.showCreateForm()
            },
            onAbsence = { type ->
                showEntryTypeSheet = false
                absenceTypeName = type.persistedValue
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShiftsListContent(
    state: ShiftsUiState.Ready,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddShift: () -> Unit,
    onEditShift: (String) -> Unit,
) {
    val itemsLocale = appLocale()
    val itemsZone = state.settings?.let { com.elmtrackr.app.domain.time.WorkTimezone.zoneFor(it) }
        ?: ZoneId.systemDefault()
    val listItems = remember(
        state.shifts,
        state.activeShift,
        state.month,
        state.settings,
        state.profiles,
        state.premiumProfiles,
        state.payContextShifts,
        state.payFacts,
        itemsZone,
        itemsLocale,
    ) {
        buildShiftsLazyListItems(
            shifts = state.shifts,
            activeShift = state.activeShift,
            month = state.month,
            settings = state.settings,
            profiles = state.profiles,
            premiumProfiles = state.premiumProfiles,
            zone = itemsZone,
            locale = itemsLocale,
            payContextShifts = state.payContextShifts,
            payFacts = state.payFacts,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenH),
    ) {
        item(key = "header") {
            ShiftsPageHeader(onAddShift = onAddShift)
        }
        item(key = "hero") {
            Spacer(Modifier.height(Spacing.md))
            ShiftsHeroSummaryCard(
                shifts = state.shifts,
                activeShift = state.activeShift,
                settings = state.settings,
                month = state.month,
                profiles = state.profiles,
                premiumProfiles = state.premiumProfiles,
                payContextShifts = state.payContextShifts,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
            Spacer(Modifier.height(Spacing.md))
        }

        items(
            items = listItems,
            key = { it.key },
            contentType = { item ->
                when (item) {
                    is ShiftsLazyListItem.SectionHeader -> "header"
                    is ShiftsLazyListItem.ShiftEntry -> "shift"
                }
            },
        ) { item ->
            when (item) {
                is ShiftsLazyListItem.SectionHeader -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = com.elmtrackr.app.ui.theme.CornerRadius.Medium,
                            topEnd = com.elmtrackr.app.ui.theme.CornerRadius.Medium,
                        ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        ShiftsWeekSectionHeader(section = item.section, settings = state.settings)
                    }
                }

                is ShiftsLazyListItem.ShiftEntry -> {
                    val bottomShape = if (item.isLastInSection) {
                        RoundedCornerShape(
                            bottomStart = com.elmtrackr.app.ui.theme.CornerRadius.Medium,
                            bottomEnd = com.elmtrackr.app.ui.theme.CornerRadius.Medium,
                        )
                    } else {
                        RoundedCornerShape(0.dp)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = bottomShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isLastInSection) 1.dp else 0.dp),
                    ) {
                        ShiftRow(
                            shift = item.shift,
                            settings = state.settings,
                            profiles = state.profiles,
                            allShiftsForPay = state.shifts,
                            premiumProfiles = state.premiumProfiles,
                            showRefunds = state.featuresTravelRefunds,
                            grouped = true,
                            display = item.display,
                            onClick = { onEditShift(item.shift.id) },
                        )
                    }
                    if (item.isLastInSection) {
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }
        }

        item(key = "add-past") {
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
    premiumProfiles: List<com.elmtrackr.app.domain.model.PremiumProfile>,
    profiles: List<com.elmtrackr.app.domain.model.CompensationProfile>,
    allShiftsForPay: List<com.elmtrackr.app.domain.model.Shift> = emptyList(),
    tasks: List<com.elmtrackr.app.domain.model.Task>,
    projects: List<com.elmtrackr.app.domain.model.Project> = emptyList(),
    errors: Map<String, com.elmtrackr.app.domain.model.UiText>,
    featuresTravelRefunds: Boolean,
    onSuggestTaskForStart: suspend (Instant) -> String? = { null },
    onSave: (ShiftFormInput) -> Unit,
    onDelete: (shiftId: String) -> Unit,
    onClose: () -> Unit,
    onCreateCompensationProfile: ((name: String, onCreated: (String?) -> Unit) -> Unit)? = null,
) {
    val initialShift = (navState as? ShiftFormNavState.Edit)?.shift
    val zone = settings?.let { com.elmtrackr.app.domain.time.WorkTimezone.zoneFor(it) }
        ?: ZoneId.systemDefault()
    val now = Instant.now()

    val defaultStart = initialShift?.startTime ?: now.minusSeconds(3600)
    val defaultEnd = initialShift?.endTime ?: now

    var startMillis by rememberSaveable { mutableStateOf(defaultStart.toEpochMilli()) }
    var hasEndTime by rememberSaveable { mutableStateOf(initialShift?.isCompleted ?: true) }
    var endMillis by rememberSaveable { mutableStateOf(defaultEnd.toEpochMilli()) }
    var breakMinutes by rememberSaveable { mutableStateOf(initialShift?.breakMinutes ?: 0) }
    var notesText by rememberSaveable { mutableStateOf(initialShift?.notes ?: "") }
    // rememberSaveable cannot persist null in a Bundle; use empty string for "no premium".
    var premiumProfileIdRaw by rememberSaveable {
        mutableStateOf(initialShift?.premiumProfileId.orEmpty())
    }
    val premiumProfileId = premiumProfileIdRaw.takeIf { it.isNotEmpty() }
    // "auto" follows the weekend-days setting; "on"/"off" are explicit per-shift
    // choices made with the weekend/holiday switch.
    var premiumMode by rememberSaveable {
        mutableStateOf(
            when {
                initialShift?.forceRegularRate == true -> "off"
                initialShift?.premiumProfileId != null -> "on"
                else -> "auto"
            },
        )
    }
    var compensationProfileId by rememberSaveable {
        mutableStateOf(initialShift?.compensationProfileId ?: settings?.defaultCompensationProfileId)
    }
    var taskId by rememberSaveable { mutableStateOf(initialShift?.taskId) }
    // Seeded from the shift so an existing project link survives rotation and is
    // never dropped by a save that only touched other fields.
    var compensationSourceName by rememberSaveable {
        mutableStateOf(
            (initialShift?.compensationSource ?: CompensationSource.EMPLOYEE).name,
        )
    }
    val compensationSource = CompensationSource.fromPersisted(compensationSourceName)
    var projectIdRaw by rememberSaveable { mutableStateOf(initialShift?.projectId.orEmpty()) }
    val projectId = projectIdRaw.takeIf { it.isNotEmpty() }

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

    // Provided for the whole form so the nested travel-refund dialog resolves its
    // ride date in the same zone the shift's own fields use. It reaches that
    // dialog through several composables that have no reason to carry a ZoneId.
    CompositionLocalProvider(LocalWorkZone provides zone) {
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
            premiumProfileId = premiumProfileId,
            onPremiumProfileIdChange = {
                premiumProfileIdRaw = it.orEmpty()
                if (it != null) premiumMode = "on"
            },
            premiumMode = premiumMode,
            onPremiumModeChange = { mode ->
                premiumMode = mode
                if (mode != "on") premiumProfileIdRaw = ""
            },
            premiumProfiles = premiumProfiles,
            profiles = profiles,
            allShiftsForPay = allShiftsForPay,
            compensationProfileId = compensationProfileId,
            onCompensationProfileIdChange = { compensationProfileId = it },
            onCreateCompensationProfile = onCreateCompensationProfile,
            tasks = tasks,
            taskId = taskId,
            onTaskIdChange = { taskId = it },
            projects = projects,
            compensationSource = compensationSource,
            onCompensationSourceChange = { compensationSourceName = it.name },
            projectId = projectId,
            onProjectIdChange = { projectIdRaw = it.orEmpty() },
            showRefundSection = shouldShowRefundSection(featuresTravelRefunds, initialShift),
            initialShift = initialShift,
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerWrapper(currentMillis: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    // The work zone, not the device's. Deciding which calendar day a stored
    // instant falls on is exactly where the two differ: a shift at 23:30 work
    // time is already tomorrow in a zone an hour ahead, so the picker opened on
    // the wrong day and confirming it moved the shift a day.
    val initUtcMidnight = Instant.ofEpochMilli(currentMillis)
        .atZone(LocalWorkZone.current).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initUtcMidnight)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss() }) { Text(stringResource(R.string.shifts_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.shifts_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TimePickerWrapper(
    currentMillis: Long,
    zone: ZoneId,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val zdt = Instant.ofEpochMilli(currentMillis).atZone(zone)
    AppTimePickerDialog(
        initialHour = zdt.hour,
        initialMinute = zdt.minute,
        title = stringResource(R.string.shifts_select_time),
        confirmLabel = stringResource(R.string.shifts_ok),
        cancelLabel = stringResource(R.string.shifts_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
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
                title = stringResource(R.string.shifts_empty_title),
                subtitle = stringResource(R.string.shifts_empty_subtitle),
            )
        }
    }
}
