package com.elmtrackr.app.ui.dashboard

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> DashboardLoading()
            is DashboardUiState.Ready -> DashboardReady(
                state = state,
                onClockIn = viewModel::clockIn,
                onClockOut = viewModel::clockOut,
                onEditStartTime = viewModel::editActiveShiftStartTime,
            )
            is DashboardUiState.Error -> DashboardError(state.message)
        }
    }
}

@Composable
private fun DashboardLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DashboardError(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Error: $message", color = MaterialTheme.colorScheme.error)
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val greeting = state.displayName?.let { "Hi, $it" } ?: "ElmTrackr"
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            SyncStatusRow(
                pendingCount = state.pendingSyncCount,
                isRemoteConfigured = state.isRemoteConfigured,
            )

            Spacer(Modifier.height(24.dp))

            when (clockStyle) {
                SupportedClockStyle.CLASSIC -> ClassicClockCard(
                    activeShift = activeShift,
                    elapsedSeconds = elapsedSeconds,
                    onClockIn = onClockIn,
                    onClockOut = { activeShift?.let { onClockOut(it.id) } },
                    onEditStartTime = { showEditDialog = true },
                )
                SupportedClockStyle.MINIMAL -> MinimalClockCard(
                    activeShift = activeShift,
                    elapsedSeconds = elapsedSeconds,
                    onClockIn = onClockIn,
                    onClockOut = { activeShift?.let { onClockOut(it.id) } },
                    onEditStartTime = { showEditDialog = true },
                )
                SupportedClockStyle.AURORA -> AuroraClockCard(
                    activeShift = activeShift,
                    elapsedSeconds = elapsedSeconds,
                    onClockIn = onClockIn,
                    onClockOut = { activeShift?.let { onClockOut(it.id) } },
                    onEditStartTime = { showEditDialog = true },
                )
            }

            Spacer(Modifier.height(32.dp))

            MonthSummarySection(
                report = state.monthlyReport,
                hourlyRate = state.settings?.hourlyRate,
            )

            Spacer(Modifier.height(24.dp))

            RecentShiftsSection(recentShifts = state.recentShifts)

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---- Sync status ----

@Composable
private fun SyncStatusRow(pendingCount: Int, isRemoteConfigured: Boolean) {
    val text = when {
        !isRemoteConfigured -> "Offline mode"
        pendingCount > 0 -> "↑ $pendingCount pending sync"
        else -> "✓ Synced"
    }
    val color = when {
        !isRemoteConfigured -> MaterialTheme.colorScheme.onSurfaceVariant
        pendingCount > 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

// ---- Clock cards ----

@Composable
private fun ClassicClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (activeShift != null) {
                Text(
                    text = formatElapsedTime(elapsedSeconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Started at ${formatInstantTime(activeShift.startTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = onEditStartTime,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit start time",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clock Out", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Not clocked in",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clock In", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MinimalClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (activeShift != null) formatElapsedTime(elapsedSeconds) else "00:00",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Thin,
            color = if (activeShift != null)
                MaterialTheme.colorScheme.onBackground
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (activeShift != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatInstantTime(activeShift.startTime),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = onEditStartTime,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit start time",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = if (activeShift != null) onClockOut else onClockIn,
            shape = CircleShape,
            colors = if (activeShift != null) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) else ButtonDefaults.buttonColors(),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.size(120.dp),
        ) {
            Text(
                text = if (activeShift != null) "OUT" else "IN",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AuroraClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val brush = Brush.linearGradient(listOf(primaryColor, tertiaryColor))

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
                    text = formatElapsedTime(elapsedSeconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Since ${formatInstantTime(activeShift.startTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    IconButton(
                        onClick = onEditStartTime,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit start time",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.25f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clock Out", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Ready",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.25f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clock In", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---- Month summary ----

@Composable
private fun MonthSummarySection(report: MonthlyReport?, hourlyRate: Double?) {
    val hoursLabel = if (report != null)
        ShiftDurationCalculator.formatMinutes(report.totalMinutes) else "—"
    val otLabel = if (report != null)
        ShiftDurationCalculator.formatMinutes(report.overtimeMinutes) else "—"
    val shiftsLabel = report?.shiftCount?.toString() ?: "—"
    val grossPay = if (report != null && hourlyRate != null && hourlyRate > 0)
        "%.2f".format((report.totalMinutes / 60.0) * hourlyRate)
    else null

    Text(
        text = "THIS MONTH",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(label = "Hours",    value = hoursLabel,  modifier = Modifier.weight(1f))
        StatCard(label = "Overtime", value = otLabel,     modifier = Modifier.weight(1f))
        StatCard(label = "Shifts",   value = shiftsLabel, modifier = Modifier.weight(1f))
        if (grossPay != null) {
            StatCard(label = "Earned", value = grossPay, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---- Recent shifts ----

@Composable
private fun RecentShiftsSection(recentShifts: List<Shift>) {
    Text(
        text = "RECENT SHIFTS",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    if (recentShifts.isEmpty()) {
        Text(
            text = "No completed shifts yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            recentShifts.forEach { shift -> RecentShiftRow(shift) }
        }
    }
}

@Composable
private fun RecentShiftRow(shift: Shift) {
    val zone = ZoneId.systemDefault()
    val dateText = shift.startTime.atZone(zone).format(dateFormatter)
    val startText = shift.startTime.atZone(zone).format(timeFormatter)
    val endText = shift.endTime?.atZone(zone)?.format(timeFormatter) ?: "—"
    val durationText = ShiftDurationCalculator.netMinutes(shift)
        ?.let { ShiftDurationCalculator.formatMinutes(it) } ?: "—"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (shift.isSpecialDay) {
                        Text(
                            text = " ★",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Text(
                    text = "$startText → $endText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---- Edit start time dialog ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStartTimeDialog(
    currentStartTime: Instant,
    onConfirm: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val zonedStart = currentStartTime.atZone(zone)
    val timePickerState = rememberTimePickerState(
        initialHour = zonedStart.hour,
        initialMinute = zonedStart.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit start time") },
        text = { TimePicker(state = timePickerState) },
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

// ---- Helpers ----

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
