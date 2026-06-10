package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.theme.ElmTrackrTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.ensureSettingsExist() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val state = uiState) {
            is SettingsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            is SettingsUiState.Ready -> SettingsContent(
                settings = state.settings,
                isSaving = state.isSaving,
                onSave = viewModel::saveSettings,
            )
            is SettingsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SettingsContent(
    settings: UserSettings,
    isSaving: Boolean,
    onSave: (UserSettings) -> Unit,
) {
    var rateText by remember(settings.hourlyRate) {
        mutableStateOf(settings.hourlyRate?.toString() ?: "")
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = rateText,
            onValueChange = { rateText = it },
            label = { Text("Hourly rate (₪/hr)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Daily OT threshold: ${settings.dailyOvertimeThresholdMinutes / 60}h",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Weekly OT threshold: ${settings.weeklyOvertimeThresholdMinutes / 60}h",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Weekend days: ${settings.weekendDays.joinToString()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val rate = rateText.toDoubleOrNull()
                onSave(settings.copy(hourlyRate = rate))
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (isSaving) "Saving…" else "Save")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ElmTrackrTheme { SettingsContent(
        settings = UserSettings(id = "x", userId = "u1",
            createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH),
        isSaving = false,
        onSave = {},
    )}
}
