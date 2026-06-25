package com.elmtrackr.app.ui.shifts

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.OvernightShiftDetector
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import androidx.compose.foundation.border
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmDashedButton
import com.elmtrackr.app.ui.theme.AuroraSurfaceSub
import com.elmtrackr.app.ui.design.ElmEmptyState
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.refunds.RefundClaimsSection
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraWeekendBg
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val dateLongFmt  = DateTimeFormatter.ofPattern("d MMM yyyy")
private val timeFmt      = DateTimeFormatter.ofPattern("HH:mm")
private val dayNumFmt    = DateTimeFormatter.ofPattern("dd")
private val weekdayFmt   = DateTimeFormatter.ofPattern("EEE")

@Composable
fun ShiftsScreen(
    viewModel: ShiftsViewModel = viewModel(factory = ShiftsViewModel.Factory),
    pendingEditShiftId: String? = null,
    onPendingEditConsumed: () -> Unit = {},
) {
    val uiState            by viewModel.uiState.collectAsState()
    val formTarget         by viewModel.formTarget.collectAsState()
    val formErrors         by viewModel.formErrors.collectAsState()
    val featuresTravelRefunds by viewModel.featuresTravelRefunds.collectAsState()
    val selectedMonth       by viewModel.selectedMonth.collectAsState()

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

    AuroraListScreen {
        when (val state = uiState) {
            is ShiftsUiState.Loading -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH),
            ) {
                ShiftsPageHeader(onAddShift = viewModel::showCreateForm)
                ShiftsSkeleton()
            }
            is ShiftsUiState.Empty -> Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .padding(horizontal = Spacing.screenH),
            ) {
                ShiftsPageHeader(onAddShift = viewModel::showCreateForm)
                ShiftsMonthCard(
                    month = selectedMonth,
                    shifts = emptyList(),
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
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
                ElmDashedButton(
                    label = "Add shift manually",
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
                onRetry = {},
            )
        }
    }
}

// ── List ──────────────────────────────────────────────────────────────────────

@Composable
private fun ShiftsPageHeader(onAddShift: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg, bottom = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("All shifts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ElmGradientButton(onClick = onAddShift, compact = true) {
            Text("+ New", fontWeight = FontWeight.SemiBold)
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ShiftsPageHeader(onAddShift = onAddShift) }
        item {
            ShiftsMonthCard(
                month = selectedMonth,
                shifts = state.shifts,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
            )
        }
        if (state.shifts.isNotEmpty()) {
            item {
                ElmCard {
                    state.shifts.forEachIndexed { index, shift ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        ShiftRow(
                            shift = shift,
                            settings = state.settings,
                            profiles = state.profiles,
                            showRefunds = state.featuresTravelRefunds,
                            grouped = true,
                            entranceIndex = index,
                            onClick = { onEditShift(shift.id) },
                        )
                    }
                }
            }
        }
        item {
            ElmDashedButton(
                label = "Add shift manually",
                onClick = onAddShift,
                modifier = Modifier.padding(bottom = Spacing.xl),
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
internal fun MonthShiftSummary(shifts: List<Shift>, settings: UserSettings?) {
    val completed = shifts.filter { it.isCompleted }
    val totalMinutes = completed.sumOf { ShiftDurationCalculator.netMinutes(it) ?: 0 }
    val pay = settings?.hourlyRate?.takeIf { it > 0 }?.let { PayrollCalculator.sumMonthlyPay(completed, settings) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("THIS MONTH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(ShiftDurationCalculator.formatMinutes(totalMinutes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("${completed.size} completed shift${if (completed.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            pay?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("GROSS PAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(MoneyFormatter.format(it.totalGross, settings?.currency ?: CurrencyCode.ILS), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
internal fun ShiftsMonthCard(
    month: YearMonth,
    shifts: List<Shift>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val completed = shifts.filter { it.isCompleted }
    val totalMinutes = completed.sumOf { ShiftDurationCalculator.netMinutes(it) ?: 0 }
    val canGoNext = month < YearMonth.now()
    val shape = RoundedCornerShape(CornerRadius.Large)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .size(32.dp)
                    .background(AuroraSurfaceSub, RoundedCornerShape(CornerRadius.Small)),
            ) {
                Icon(Icons.Filled.ChevronLeft, "Previous month", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (totalMinutes > 0) {
                    Text(
                        "${formatHoursDecimal(totalMinutes)}h · ${completed.size} shift${if (completed.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(
                onClick = onNext,
                enabled = canGoNext,
                modifier = Modifier
                    .size(32.dp)
                    .background(AuroraSurfaceSub, RoundedCornerShape(CornerRadius.Small)),
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    "Next month",
                    tint = if (canGoNext) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun formatHoursDecimal(minutes: Int): String = "%.1f".format(Locale.US, minutes / 60.0)

@Composable
internal fun ShiftRow(
    shift: Shift,
    settings: UserSettings?,
    profiles: List<CompensationProfile> = emptyList(),
    showRefunds: Boolean,
    grouped: Boolean = false,
    entranceIndex: Int = 0,
    onClick: () -> Unit,
) {
    val zone         = ZoneId.systemDefault()
    val zdt          = shift.startTime.atZone(zone)
    val dayNumber    = zdt.format(dayNumFmt)
    val weekday      = zdt.format(weekdayFmt).uppercase(Locale.getDefault())
    val startText    = zdt.format(timeFmt)
    val endText      = shift.endTime?.atZone(zone)?.format(timeFmt) ?: "Active"
    val durationText = if (shift.isCompleted)
        ShiftDurationCalculator.netMinutes(shift)?.let { ShiftDurationCalculator.formatMinutes(it) }
    else null
    val overnight = OvernightShiftDetector.isOvernight(shift)
    val weekend = settings?.let {
        CompensationResolver.isWeekendShift(shift, it, profiles)
    } == true
    val breakdown = settings?.takeIf { shift.isCompleted }?.let { com.elmtrackr.app.domain.MonthlyReportBuilder.buildShiftBreakdown(shift, it) }
    val pay = settings?.let { s ->
        PayrollCalculator.calculateShiftPay(shift, s, profiles)
    }

    val stripeColor = when {
        shift.isActive    -> AuroraAqua
        shift.isSpecialDay -> AuroraPlum
        else              -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

    val rowModifier = if (grouped) {
        Modifier.fillMaxWidth().auroraEnter(entranceIndex)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).auroraEnter(entranceIndex)
    }

    if (grouped) {
        Surface(onClick = onClick, modifier = rowModifier, color = MaterialTheme.colorScheme.surface) {
            ShiftRowContent(
                shift, settings, showRefunds, stripeColor, dayNumber, weekday,
                startText, endText, durationText, weekend, overnight, breakdown, pay,
            )
        }
    } else {
        Card(
            onClick = onClick,
            modifier = rowModifier,
            shape = RoundedCornerShape(CornerRadius.Medium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            ShiftRowContent(
                shift, settings, showRefunds, stripeColor, dayNumber, weekday,
                startText, endText, durationText, weekend, overnight, breakdown, pay,
            )
        }
    }
}

@Composable
private fun ShiftRowContent(
    shift: Shift,
    settings: UserSettings?,
    showRefunds: Boolean,
    stripeColor: Color,
    dayNumber: String,
    weekday: String,
    startText: String,
    endText: String,
    durationText: String?,
    weekend: Boolean,
    overnight: Boolean,
    breakdown: com.elmtrackr.app.domain.model.ShiftBreakdown?,
    pay: com.elmtrackr.app.domain.model.ShiftPayBreakdown?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
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
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Main info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text       = "$startText - $endText",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                if (shift.isActive || shift.isSpecialDay || weekend || overnight || shift.breakMinutes > 0 || (showRefunds && shift.refundAction != null)) {
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (shift.isActive) {
                            ActiveBadge()
                        }
                        if (shift.isSpecialDay) {
                            SpecialBadge()
                        }
                        if (weekend && !shift.isSpecialDay) ShiftBadge("Weekend", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        if (overnight) ShiftBadge("Overnight", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        if (shift.breakMinutes > 0 && !shift.isActive) {
                            Text(
                                text  = "${shift.breakMinutes}m brk",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (showRefunds) shift.refundAction?.let { action ->
                            ShiftBadge(
                                when (action) {
                                    RefundAction.SUBMITTED -> "Refund sent"
                                    RefundAction.NO_RIDE_TAKEN -> "No ride"
                                    RefundAction.REMIND_LATER -> "Refund later"
                                },
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                shift.notes?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }

            // Duration
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                if (shift.isActive) ActiveDuration(shift.startTime)
                else if (durationText != null) Text(durationText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                if ((breakdown?.overtimeMinutes ?: 0) > 0 && !shift.isSpecialDay && !weekend) {
                    Text("+${ShiftDurationCalculator.formatMinutes(breakdown!!.overtimeMinutes)} OT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                pay?.let { Text(MoneyFormatter.format(it.totalGross, settings?.currency ?: CurrencyCode.ILS), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            }

            // Chevron
            Icon(
                imageVector        = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp).padding(end = 6.dp),
        )
    }
}

@Composable
private fun SpecialBadge() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text       = "Special",
            style      = MaterialTheme.typography.labelSmall,
            color      = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActiveBadge() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text       = "Active",
            style      = MaterialTheme.typography.labelSmall,
            color      = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Form ──────────────────────────────────────────────────────────────────────

@Composable
private fun ShiftFormContent(
    navState: ShiftFormNavState,
    settings: UserSettings?,
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
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isSpecialDay, onCheckedChange = { isSpecialDay = it })
                }

                val previewStart = Instant.ofEpochMilli(startMillis)
                val previewEnd = if (hasEndTime) Instant.ofEpochMilli(endMillis) else null
                if (previewEnd?.isAfter(previewStart) == true) {
                    val previewShift = Shift(
                        id = initialShift?.id ?: "preview",
                        userId = initialShift?.userId ?: "preview",
                        startTime = previewStart,
                        endTime = previewEnd,
                        breakMinutes = breakText.toIntOrNull() ?: 0,
                        notes = notesText,
                        isSpecialDay = isSpecialDay,
                    )
                    val minutes = ShiftDurationCalculator.netMinutes(previewShift)
                    val previewPay = settings?.hourlyRate?.takeIf { it > 0 }
                        ?.let { PayrollCalculator.calculateShiftPay(previewShift, requireNotNull(settings)) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("SHIFT TOTAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                Text(minutes?.let(ShiftDurationCalculator::formatMinutes) ?: "-", fontWeight = FontWeight.ExtraBold)
                            }
                            previewPay?.let {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("ESTIMATED PAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                    Text(MoneyFormatter.format(it.totalGross, settings?.currency ?: CurrencyCode.ILS), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                val toEligibility = initialShift?.let(RefundPolicy::checkToWorkEligibility)
                val fromEligibility = initialShift?.let(RefundPolicy::checkFromWorkEligibility)
                if (
                    featuresTravelRefunds &&
                    isEdit &&
                    initialShift?.isCompleted == true &&
                    (toEligibility?.eligible == true || fromEligibility?.eligible == true)
                ) {
                    ElmSectionHeader("Travel Refund")
                    RefundClaimsSection(
                        shift = initialShift,
                        currency = settings?.currency ?: CurrencyCode.ILS,
                    )
                    errors["refund"]?.let { FieldError(it) }
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
                                refundAction = initialShift?.refundAction,
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
private fun ActiveDuration(start: Instant) {
    var seconds by remember(start) { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(start) {
        while (true) {
            seconds = ((Instant.now().toEpochMilli() - start.toEpochMilli()) / 1000L).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    Text(formatLiveDuration(seconds), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
}

@Composable
private fun ShiftBadge(label: String, background: Color, color: Color) {
    Box(Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RefundClaimsEditor(
    shift: Shift,
    claims: List<RefundClaim>,
    toEligibility: RefundPolicy.Eligibility?,
    fromEligibility: RefundPolicy.Eligibility?,
    onSave: (String, RefundDirection, RefundProvider, Double, Instant, String, ReceiptUpload?, (Boolean) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onActionChange: (String, RefundAction?) -> Unit,
    receiptUrl: suspend (String) -> String?,
    currency: CurrencyCode,
) {
    val directions = buildList {
        if (toEligibility?.eligible == true) add(RefundDirection.TO_WORK to toEligibility)
        if (fromEligibility?.eligible == true) add(RefundDirection.FROM_WORK to fromEligibility)
    }
    directions.forEach { (direction, eligibility) ->
        RefundClaimCard(
            shift = shift,
            direction = direction,
            eligibility = eligibility,
            existing = claims.firstOrNull { it.direction == direction },
            onSave = onSave,
            onDelete = onDelete,
            onActionChange = onActionChange,
            receiptUrl = receiptUrl,
            currency = currency,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RefundClaimCard(
    shift: Shift,
    direction: RefundDirection,
    eligibility: RefundPolicy.Eligibility,
    existing: RefundClaim?,
    onSave: (String, RefundDirection, RefundProvider, Double, Instant, String, ReceiptUpload?, (Boolean) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onActionChange: (String, RefundAction?) -> Unit,
    receiptUrl: suspend (String) -> String?,
    currency: CurrencyCode,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val defaultRideAt = existing?.rideAt ?: when (direction) {
        RefundDirection.TO_WORK -> shift.startTime
        RefundDirection.FROM_WORK -> shift.endTime ?: shift.startTime
    }
    var showForm by rememberSaveable(existing?.id, direction.name) { mutableStateOf(false) }
    var providerName by rememberSaveable(existing?.id, direction.name) {
        mutableStateOf(existing?.provider?.name ?: RefundProvider.LIME.name)
    }
    var amountText by rememberSaveable(existing?.id, direction.name) {
        mutableStateOf(existing?.amount?.toString() ?: "")
    }
    var notesText by rememberSaveable(existing?.id, direction.name) {
        mutableStateOf(existing?.notes ?: "")
    }
    var rideAtMillis by rememberSaveable(existing?.id, direction.name) {
        mutableStateOf(defaultRideAt.toEpochMilli())
    }
    var selectedReceipt by remember(existing?.id, direction.name) { mutableStateOf<ReceiptUpload?>(null) }
    var showRideDatePicker by rememberSaveable { mutableStateOf(false) }
    var showRideTimePicker by rememberSaveable { mutableStateOf(false) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    var receiptError by rememberSaveable { mutableStateOf<String?>(null) }

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                selectedReceipt = readReceiptUpload(context, uri)
                receiptError = if (selectedReceipt == null) "Choose an image smaller than 10 MB." else null
            }
        }
    }

    if (showRideDatePicker) {
        DatePickerWrapper(
            currentMillis = rideAtMillis,
            onConfirm = { rideAtMillis = applyDate(rideAtMillis, it, zone); showRideDatePicker = false },
            onDismiss = { showRideDatePicker = false },
        )
    }
    if (showRideTimePicker) {
        TimePickerWrapper(
            currentMillis = rideAtMillis,
            zone = zone,
            onConfirm = { hour, minute ->
                rideAtMillis = applyTime(rideAtMillis, hour, minute, zone)
                showRideTimePicker = false
            },
            onDismiss = { showRideTimePicker = false },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(CornerRadius.Medium),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (direction == RefundDirection.TO_WORK) "→ Ride to work" else "← Ride from work",
                fontWeight = FontWeight.Bold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                eligibility.reasons.forEach { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            if (!showForm && existing == null) {
                Button(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (shift.refundAction == RefundAction.REMIND_LATER) "Add receipt now" else "Add receipt claim")
                }
                if (direction == RefundDirection.FROM_WORK) {
                    when (shift.refundAction) {
                        null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onActionChange(shift.id, RefundAction.NO_RIDE_TAKEN) },
                                modifier = Modifier.weight(1f),
                            ) { Text("No ride") }
                            OutlinedButton(
                                onClick = { onActionChange(shift.id, RefundAction.REMIND_LATER) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Remind later") }
                        }
                        RefundAction.NO_RIDE_TAKEN -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("No ride taken", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onActionChange(shift.id, null) }) { Text("Undo") }
                        }
                        RefundAction.REMIND_LATER -> OutlinedButton(
                            onClick = { onActionChange(shift.id, RefundAction.NO_RIDE_TAKEN) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("No ride taken") }
                        RefundAction.SUBMITTED -> Unit
                    }
                }
            }

            if (!showForm && existing != null) {
                Text(
                    "${existing.provider.name.lowercase().replaceFirstChar { it.uppercase() }} · ${MoneyFormatter.format(existing.amount, currency)}",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    existing.rideAt.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                existing.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showForm = true }) { Text("Edit") }
                    existing.receiptPath?.let { path ->
                        TextButton(onClick = {
                            scope.launch {
                                receiptUrl(path)?.let { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        }) { Text("View receipt") }
                    }
                    TextButton(onClick = { onDelete(existing.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (showForm) {
                RideProviderSelector(
                    selected = RefundProvider.valueOf(providerName),
                    onSelect = { providerName = it.name },
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        if (value.count { it == '.' } <= 1 && value.all { it.isDigit() || it == '.' }) amountText = value
                    },
                    label = { Text("Amount (${currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateTimeRow(
                    dateLabel = "Ride date",
                    timeLabel = "Ride time",
                    millis = rideAtMillis,
                    zone = zone,
                    onPickDate = { showRideDatePicker = true },
                    onPickTime = { showRideTimePicker = true },
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Claim notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { receiptPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        selectedReceipt?.fileName
                            ?: if (existing?.receiptPath != null) "Replace receipt photo" else "Attach receipt photo",
                    )
                }
                receiptError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSave(
                                shift.id,
                                direction,
                                RefundProvider.valueOf(providerName),
                                amountText.toDoubleOrNull() ?: 0.0,
                                Instant.ofEpochMilli(rideAtMillis),
                                notesText,
                                selectedReceipt,
                                { success ->
                                    isSaving = false
                                    if (success) showForm = false
                                },
                            )
                            isSaving = true
                        },
                        enabled = !isSaving && (amountText.toDoubleOrNull() ?: 0.0) > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (existing == null) "Save claim" else "Update claim")
                    }
                    TextButton(onClick = { showForm = false }, enabled = !isSaving) { Text("Cancel") }
                }
            }
        }
    }
}

private suspend fun readReceiptUpload(context: android.content.Context, uri: Uri): ReceiptUpload? =
    withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: "receipt.jpg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RECEIPT_BYTES) return@withContext null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: return@withContext null
        ReceiptUpload(name, bytes, context.contentResolver.getType(uri))
    }

private const val MAX_RECEIPT_BYTES = 10 * 1024 * 1024

private fun formatLiveDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
    else "%02d:%02d".format(minutes, secs)
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
    ElmTrackrTheme {
        Column(Modifier.padding(Spacing.md)) {
            ShiftsPageHeader(onAddShift = {})
            ShiftsMonthCard(
                month = YearMonth.now(),
                shifts = emptyList(),
                onPrevious = {},
                onNext = {},
            )
            Spacer(Modifier.height(Spacing.md))
            ElmEmptyState(
                icon = Icons.Filled.AccessTime,
                title = "No shifts this month",
                subtitle = "Clock in from the home screen or add a shift manually.",
            )
        }
    }
}
