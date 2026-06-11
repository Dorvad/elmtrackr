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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.theme.ElmTrackrTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    authState: AuthUiState? = null,
    onSignOut: () -> Unit = {},
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
                authState = authState,
                onSave = viewModel::saveSettings,
                onSignOut = onSignOut,
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
    authState: AuthUiState?,
    onSave: (UserSettings) -> Unit,
    onSignOut: () -> Unit,
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

        if (authState != null) {
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            AuthSection(authState = authState, onSignOut = onSignOut)
        }
    }
}

@Composable
private fun AuthSection(authState: AuthUiState, onSignOut: () -> Unit) {
    Text(
        text = "Account",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(12.dp))
    when (authState) {
        is AuthUiState.NotConfigured -> Text(
            text = "Supabase not configured — running in local-only mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is AuthUiState.Loading -> CircularProgressIndicator()
        is AuthUiState.SignedIn -> {
            Text(
                text = authState.profile.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            authState.profile.fullName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSignOut,
                enabled = !authState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (authState.isLoading) "Signing out…" else "Sign out")
            }
        }
        is AuthUiState.SignedOut -> Text(
            text = "Not signed in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is AuthUiState.PasswordResetSent -> Text(
            text = "Password reset email sent.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ElmTrackrTheme { SettingsContent(
        settings = UserSettings(id = "x", userId = "u1",
            createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH),
        isSaving = false,
        authState = AuthUiState.NotConfigured,
        onSave = {},
        onSignOut = {},
    )}
}
