package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.ElmStatCard
import com.elmtrackr.app.ui.design.ElmSyncPill
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.AuroraOvertimeBg
import com.elmtrackr.app.ui.theme.AuroraOvertimeInk
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraSurface
import com.elmtrackr.app.ui.theme.AuroraSurfaceSub
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateHeaderFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
private val dateFormatter  = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val timeFormatter  = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = AuroraIndigo)
            }
            is DashboardUiState.Ready  -> DashboardReady(
                state           = state,
                onClockIn       = viewModel::clockIn,
                onClockOut      = viewModel::clockOut,
                onEditStartTime = viewModel::editActiveShiftStartTime,
            )
            is DashboardUiState.Error  -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DashboardReady(
    state: DashboardUiState.Ready,
    onClockIn: () -> Unit,
    onClockOut: (String) -> Unit,
    onEditStartTime: (shiftId: String, newStartTime: Instant) -> Unit,
) {
    val activeShift = state.activeShift
    var showEditDialog by rememberSaveable { mutableStateOf(false) }

    val elapsedSeconds by produceState(initialValue = 0L, key1 = activeShift?.id) {
        val shift = activeShift ?: return@produceState
        while (true) {
            value = (Instant.now().toEpochMilli() - shift.startTime.toEpochMilli()) / 1000L
            delay(1_000L)
        }
    }

    if (showEditDialog && activeShift != null) {
        EditStartTimeDialog(
            currentStartTime = activeShift.startTime,
            onConfirm = { newTime ->
                onEditStartTime(activeShift.id, newTime)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    val clockStyle = state.settings?.clockStyle?.toSupportedOrDefault()
        ?: SupportedClockStyle.CLASSIC

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Header ────────────────────────────────────────────────────────
            DashboardHeader(
                displayName        = state.displayName,
                pendingCount       = state.pendingSyncCount,
                isRemoteConfigured = state.isRemoteConfigured,
            )

            Spacer(Modifier.height(24.dp))

            // ── Clock card ────────────────────────────────────────────────────
            val dailyOtMinutes = state.settings?.dailyOvertimeThresholdMinutes ?: (8 * 60)

            when (clockStyle) {
                SupportedClockStyle.CLASSIC -> ClassicClockCard(
                    activeShift       = activeShift,
                    elapsedSeconds    = elapsedSeconds,
                    dailyOtMinutes    = dailyOtMinutes,
                    onClockIn         = onClockIn,
                    onClockOut        = { activeShift?.let { onClockOut(it.id) } },
                    onEditStartTime   = { showEditDialog = true },
                )
                SupportedClockStyle.MINIMAL -> MinimalClockCard(
                    activeShift     = activeShift,
                    elapsedSeconds  = elapsedSeconds,
                    onClockIn       = onClockIn,
                    onClockOut      = { activeShift?.let { onClockOut(it.id) } },
                    onEditStartTime = { showEditDialog = true },
                )
                SupportedClockStyle.AURORA -> AuroraClockCard(
                    activeShift     = activeShift,
                    elapsedSeconds  = elapsedSeconds,
                    onClockIn       = onClockIn,
                    onClockOut      = { activeShift?.let { onClockOut(it.id) } },
                    onEditStartTime = { showEditDialog = true },
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Month summary ─────────────────────────────────────────────────
            MonthSummarySection(
                report      = state.monthlyReport,
                hourlyRate  = state.settings?.hourlyRate,
                otMinutes   = dailyOtMinutes,
            )

            Spacer(Modifier.height(24.dp))

            // ── Recent shifts ─────────────────────────────────────────────────
            RecentShiftsSection(recentShifts = state.recentShifts)

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    displayName: String?,
    pendingCount: Int,
    isRemoteConfigured: Boolean,
) {
    val today = Instant.now().atZone(ZoneId.systemDefault()).format(dateHeaderFmt)
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment   = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text       = today.uppercase(),
                style      = MaterialTheme.typography.labelMedium,
                color      = AuroraInk2,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text       = if (displayName != null) "Hi, $displayName" else "ElmTrackr",
                style      = MaterialTheme.typography.headlineMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
        ElmSyncPill(pendingCount = pendingCount, isRemoteConfigured = isRemoteConfigured)
    }
}

// ── Classic clock card ────────────────────────────────────────────────────────

@Composable
private fun ClassicClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    dailyOtMinutes: Int,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val elapsedMinutes = elapsedSeconds / 60f
    val progress = if (dailyOtMinutes > 0) (elapsedMinutes / dailyOtMinutes).coerceIn(0f, 1f) else 0f
    val isOvertime = activeShift != null && elapsedMinutes > dailyOtMinutes

    val progressColor = if (isOvertime) AuroraPeach else AuroraIndigo
    val trackColor = AuroraFaint.copy(alpha = 0.3f)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.cardColors(containerColor = AuroraSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (activeShift != null) {
                // Progress ring with elapsed time in the centre
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp)) {
                    Canvas(modifier = Modifier.size(148.dp)) {
                        val strokeWidth = 10.dp.toPx()
                        val inset = strokeWidth / 2f
                        val diameter = size.minDimension - strokeWidth
                        // track
                        drawArc(
                            color      = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter  = false,
                            style      = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft    = Offset(inset, inset),
                            size       = Size(diameter, diameter),
                        )
                        // progress arc
                        if (progress > 0f) {
                            drawArc(
                                color      = progressColor,
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter  = false,
                                style      = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                topLeft    = Offset(inset, inset),
                                size       = Size(diameter, diameter),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = formatElapsedTime(elapsedSeconds),
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = if (isOvertime) AuroraOvertimeInk else AuroraIndigo,
                        )
                        if (isOvertime) {
                            Text(
                                text  = "overtime",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraPeach,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "Since ${formatInstantTime(activeShift.startTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AuroraInk2,
                    )
                    IconButton(onClick = onEditStartTime, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector     = Icons.Filled.Edit,
                            contentDescription = "Edit start time",
                            modifier        = Modifier.size(16.dp),
                            tint            = AuroraFaint,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockOut,
                    shape   = RoundedCornerShape(14.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = AuroraOvertimeBg,
                        contentColor   = AuroraOvertimeInk,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clock Out", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = "Ready to clock in",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = AuroraInk2,
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                ElmGradientButton(onClick = onClockIn) {
                    Text("Clock In", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Minimal clock card ────────────────────────────────────────────────────────

@Composable
private fun MinimalClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = if (activeShift != null) formatElapsedTime(elapsedSeconds) else "00:00",
            style      = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Thin,
            color      = if (activeShift != null) AuroraIndigo else AuroraFaint,
            textAlign  = TextAlign.Center,
        )
        if (activeShift != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = formatInstantTime(activeShift.startTime),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AuroraInk2,
                )
                IconButton(onClick = onEditStartTime, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector        = Icons.Filled.Edit,
                        contentDescription = "Edit start time",
                        modifier           = Modifier.size(18.dp),
                        tint               = AuroraFaint,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = if (activeShift != null) onClockOut else onClockIn,
            shape    = CircleShape,
            colors   = if (activeShift != null)
                ButtonDefaults.buttonColors(containerColor = AuroraOvertimeBg, contentColor = AuroraOvertimeInk)
            else
                ButtonDefaults.buttonColors(containerColor = AuroraIndigo, contentColor = Color.White),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.size(120.dp),
        ) {
            Text(
                text       = if (activeShift != null) "OUT" else "IN",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Aurora clock card ─────────────────────────────────────────────────────────

@Composable
private fun AuroraClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val brush = Brush.linearGradient(
        colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = brush, shape = RoundedCornerShape(24.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (activeShift != null) {
                Text(
                    text       = formatElapsedTime(elapsedSeconds),
                    style      = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "Since ${formatInstantTime(activeShift.startTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    IconButton(onClick = onEditStartTime, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector        = Icons.Filled.Edit,
                            contentDescription = "Edit start time",
                            modifier           = Modifier.size(16.dp),
                            tint               = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockOut,
                    shape   = RoundedCornerShape(14.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.22f),
                        contentColor   = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clock Out", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text       = "Ready",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                    color      = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockIn,
                    shape   = RoundedCornerShape(14.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.22f),
                        contentColor   = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clock In", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Month summary ─────────────────────────────────────────────────────────────

@Composable
private fun MonthSummarySection(
    report: MonthlyReport?,
    hourlyRate: Double?,
    otMinutes: Int,
) {
    val hoursLabel  = report?.let { ShiftDurationCalculator.formatMinutes(it.totalMinutes) } ?: "—"
    val otLabel     = report?.let { ShiftDurationCalculator.formatMinutes(it.overtimeMinutes) } ?: "—"
    val shiftsLabel = report?.shiftCount?.toString() ?: "—"
    val grossPay    = if (report != null && hourlyRate != null && hourlyRate > 0)
        "%.0f".format((report.totalMinutes / 60.0) * hourlyRate) else null

    val isOvertime = report != null && report.overtimeMinutes > 0
    val otValueColor = if (isOvertime) AuroraPeach else AuroraInk2

    ElmSectionHeader(title = "This Month")
    Spacer(Modifier.height(10.dp))
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ElmStatCard(label = "Hours",    value = hoursLabel,  modifier = Modifier.weight(1f))
        ElmStatCard(label = "Overtime", value = otLabel,     modifier = Modifier.weight(1f), valueColor = otValueColor)
        ElmStatCard(label = "Shifts",   value = shiftsLabel, modifier = Modifier.weight(1f))
        if (grossPay != null) {
            ElmStatCard(label = "Earned", value = grossPay, modifier = Modifier.weight(1f))
        }
    }
}

// ── Recent shifts ─────────────────────────────────────────────────────────────

@Composable
private fun RecentShiftsSection(recentShifts: List<Shift>) {
    ElmSectionHeader(title = "Recent Shifts")
    Spacer(Modifier.height(10.dp))
    if (recentShifts.isEmpty()) {
        Text(
            text  = "No completed shifts yet",
            style = MaterialTheme.typography.bodyMedium,
            color = AuroraInk2,
        )
    } else {
        Column(
            modifier              = Modifier.fillMaxWidth(),
            verticalArrangement   = Arrangement.spacedBy(6.dp),
        ) {
            recentShifts.forEach { shift -> RecentShiftRow(shift) }
        }
    }
}

@Composable
private fun RecentShiftRow(shift: Shift) {
    val zone         = ZoneId.systemDefault()
    val dateText     = shift.startTime.atZone(zone).format(dateFormatter)
    val startText    = shift.startTime.atZone(zone).format(timeFormatter)
    val endText      = shift.endTime?.atZone(zone)?.format(timeFormatter) ?: "—"
    val durationText = ShiftDurationCalculator.netMinutes(shift)
        ?.let { ShiftDurationCalculator.formatMinutes(it) } ?: "—"

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = AuroraSurfaceSub),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = dateText,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    if (shift.isSpecialDay) {
                        Text(
                            text  = " ★",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroraPlum,
                        )
                    }
                }
                Text(
                    text  = "$startText → $endText",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraInk2,
                )
            }
            Text(
                text       = durationText,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = AuroraIndigo,
            )
        }
    }
}

// ── Edit start time dialog ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStartTimeDialog(
    currentStartTime: Instant,
    onConfirm: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone        = ZoneId.systemDefault()
    val zonedStart  = currentStartTime.atZone(zone)
    val timePickerState = rememberTimePickerState(
        initialHour   = zonedStart.hour,
        initialMinute = zonedStart.minute,
        is24Hour      = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit start time") },
        text  = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = {
                val newInstant = LocalDateTime.of(
                    zonedStart.toLocalDate(),
                    LocalTime.of(timePickerState.hour, timePickerState.minute),
                ).atZone(zone).toInstant()
                onConfirm(newInstant)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatElapsedTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

private fun formatInstantTime(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(timeFormatter)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DashboardScreenPreview() {
    ElmTrackrTheme {
        DashboardError("preview only")
    }
}

@Composable
private fun DashboardError(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text("Error: $message", color = MaterialTheme.colorScheme.error)
    }
}
