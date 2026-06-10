package com.elmtrackr.app.ui.shifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ShiftsScreen(
    viewModel: ShiftsViewModel = viewModel(factory = ShiftsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val state = uiState) {
            is ShiftsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            is ShiftsUiState.Empty -> ShiftsEmpty()
            is ShiftsUiState.Ready -> ShiftsList(state.shifts)
            is ShiftsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ShiftsEmpty() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No shifts yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Clock in from the Dashboard to start tracking",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShiftsList(shifts: List<Shift>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(16.dp)) }
        items(shifts, key = { it.id }) { shift ->
            ShiftRow(shift)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("MMM d, HH:mm")

@Composable
private fun ShiftRow(shift: Shift) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = shift.startTime.atZone(ZoneOffset.UTC).format(dateFmt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (shift.isCompleted) {
                val net = ShiftDurationCalculator.netMinutes(shift)
                Text(
                    text = "Duration: ${ShiftDurationCalculator.formatMinutes(net ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShiftsScreenPreview() {
    ElmTrackrTheme { ShiftsEmpty() }
}
