package com.elmtrackr.app.ui.dashboard

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.ui.theme.ElmTrackrTheme

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
                onClockOut = { shiftId -> viewModel.clockOut(shiftId) },
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
) {
    val isClockedIn = state.activeShift != null
    val report = state.monthlyReport

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ElmTrackr",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isClockedIn) "Currently clocked in" else "Track shifts, overtime & earnings",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        ClockButton(
            isClockedIn = isClockedIn,
            onToggle = {
                if (isClockedIn) onClockOut(state.activeShift!!.id)
                else onClockIn()
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        if (state.pendingSyncCount > 0) {
            Text(
                text = "↑ ${state.pendingSyncCount} change${if (state.pendingSyncCount == 1) "" else "s"} pending sync",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = "THIS MONTH",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val hoursLabel = if (report != null)
                ShiftDurationCalculator.formatMinutes(report.totalMinutes) else "—"
            val otLabel = if (report != null)
                ShiftDurationCalculator.formatMinutes(report.overtimeMinutes) else "—"
            val shiftsLabel = report?.shiftCount?.toString() ?: "—"

            StatCard(label = "Hours",    value = hoursLabel,  modifier = Modifier.weight(1f))
            StatCard(label = "Overtime", value = otLabel,     modifier = Modifier.weight(1f))
            StatCard(label = "Shifts",   value = shiftsLabel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ClockButton(
    isClockedIn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Button(
            onClick = onToggle,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isClockedIn)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primary,
                contentColor = if (isClockedIn)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.size(164.dp),
        ) {
            Text(
                text = if (isClockedIn) "Clock\nOut" else "Clock\nIn",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
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
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DashboardScreenPreview() {
    ElmTrackrTheme {
        DashboardError("preview only")
    }
}
