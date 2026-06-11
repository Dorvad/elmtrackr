@file:OptIn(ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import java.time.Instant

private val SUPPORTED_CLOCK_STYLES = listOf(ClockStyle.CLASSIC, ClockStyle.MINIMAL, ClockStyle.AURORA)

private fun minutesToHours(minutes: Int): String {
    val h = minutes / 60.0
    return if (h == h.toLong().toDouble()) h.toLong().toString() else "%.2f".format(h)
}

private fun supportedClockStyleOf(style: ClockStyle): ClockStyle =
    if (style in SUPPORTED_CLOCK_STYLES) style else ClockStyle.CLASSIC

private val THEME_OPTIONS = listOf("system" to "System default", "light" to "Light", "dark" to "Dark")

private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

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
                state = state,
                authState = authState,
                onSave = viewModel::saveSettings,
                onSignOut = onSignOut,
                onFeatureFlag = viewModel::updateFeatureFlag,
                onWeekendDays = viewModel::updateWeekendDays,
                onTheme = viewModel::saveTheme,
                onSync = viewModel::triggerSync,
                onResetPassword = viewModel::resetPassword,
            )
            is SettingsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Ready,
    authState: AuthUiState?,
    onSave: (String, Double, Double, Double?, String, ClockStyle) -> Unit,
    onSignOut: () -> Unit,
    onFeatureFlag: (FeatureFlag, Boolean) -> Unit,
    onWeekendDays: (List<Int>) -> Unit,
    onTheme: (String) -> Unit,
    onSync: () -> Unit,
    onResetPassword: () -> Unit,
) {
    var displayName by remember(state.profile?.fullName) {
        mutableStateOf(state.profile?.fullName ?: "")
    }
    var dailyOtText by remember(state.settings.dailyOvertimeThresholdMinutes) {
        mutableStateOf(minutesToHours(state.settings.dailyOvertimeThresholdMinutes))
    }
    var weeklyOtText by remember(state.settings.weeklyOvertimeThresholdMinutes) {
        mutableStateOf(minutesToHours(state.settings.weeklyOvertimeThresholdMinutes))
    }
    var hourlyRateText by remember(state.settings.hourlyRate) {
        mutableStateOf(state.settings.hourlyRate?.toString() ?: "")
    }
    var timezone by remember(state.settings.timezone) {
        mutableStateOf(state.settings.timezone)
    }
    var clockStyle by remember(state.settings.clockStyle) {
        mutableStateOf(supportedClockStyleOf(state.settings.clockStyle))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // ── 1. Profile ───────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSectionCard("Profile") {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.profile?.let { profile ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = profile.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── 2. Appearance ────────────────────────────────────────────────────
        item {
            SettingsSectionCard("Appearance") {
                ThemeDropdown(selected = state.selectedTheme, onSelect = onTheme)
                if (state.settings.featuresClockStyles) {
                    Spacer(Modifier.height(12.dp))
                    ClockStyleDropdown(selected = clockStyle, onSelect = { clockStyle = it })
                }
            }
        }

        // ── 3. Overtime Thresholds ───────────────────────────────────────────
        item {
            SettingsSectionCard("Overtime Thresholds") {
                HoursField(
                    label = "Daily overtime (hours)",
                    value = dailyOtText,
                    onValueChange = { dailyOtText = it },
                    error = state.validationErrors["dailyOt"],
                )
                Spacer(Modifier.height(8.dp))
                HoursField(
                    label = "Weekly overtime (hours)",
                    value = weeklyOtText,
                    onValueChange = { weeklyOtText = it },
                    error = state.validationErrors["weeklyOt"],
                )
            }
        }

        // ── 4. Weekend Days ──────────────────────────────────────────────────
        item {
            SettingsSectionCard("Weekend Days") {
                WeekendDaysSelector(
                    selected = state.settings.weekendDays,
                    onChange = onWeekendDays,
                )
            }
        }

        // ── 5. Payroll ───────────────────────────────────────────────────────
        item {
            SettingsSectionCard("Payroll") {
                HoursField(
                    label = "Hourly rate",
                    value = hourlyRateText,
                    onValueChange = { hourlyRateText = it },
                    error = state.validationErrors["hourlyRate"],
                )
            }
        }

        // ── 6. Location ──────────────────────────────────────────────────────
        item {
            SettingsSectionCard("Location") {
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it },
                    label = { Text("Timezone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── 7. Features ──────────────────────────────────────────────────────
        item {
            SettingsSectionCard("Features") {
                ToggleRow(
                    title = "Travel Refunds",
                    description = "Track and manage travel refund claims",
                    checked = state.settings.featuresTravelRefunds,
                    onCheckedChange = { onFeatureFlag(FeatureFlag.TRAVEL_REFUNDS, it) },
                )
                ToggleRow(
                    title = "Paid Projects",
                    description = "Associate shifts with paid client projects",
                    checked = state.settings.featuresPaidProjects,
                    onCheckedChange = { onFeatureFlag(FeatureFlag.PAID_PROJECTS, it) },
                )
                ToggleRow(
                    title = "Insights",
                    description = "View trends and patterns in your work history",
                    checked = state.settings.featuresInsights,
                    onCheckedChange = { onFeatureFlag(FeatureFlag.INSIGHTS, it) },
                )
                ToggleRow(
                    title = "Clock Styles",
                    description = "Choose from different clock display styles",
                    checked = state.settings.featuresClockStyles,
                    onCheckedChange = { onFeatureFlag(FeatureFlag.CLOCK_STYLES, it) },
                )
            }
        }

        // ── 8. Save button ───────────────────────────────────────────────────
        item {
            Button(
                onClick = {
                    onSave(
                        displayName,
                        dailyOtText.toDoubleOrNull() ?: 0.0,
                        weeklyOtText.toDoubleOrNull() ?: 0.0,
                        hourlyRateText.toDoubleOrNull(),
                        timezone,
                        clockStyle,
                    )
                },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save Settings")
            }
        }

        // ── 9. Sync ──────────────────────────────────────────────────────────
        item {
            SettingsSectionCard("Sync") {
                InfoRow("Pending changes", state.pendingCount.toString())
                InfoRow("Last sync", state.lastSyncStatus ?: "Never")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSync,
                    enabled = !state.isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isSyncing) "Syncing…" else "Sync Now")
                }
            }
        }

        // ── 10. Account ──────────────────────────────────────────────────────
        if (authState != null) {
            item {
                SettingsSectionCard("Account") {
                    AccountSection(
                        authState = authState,
                        onResetPassword = onResetPassword,
                        onSignOut = onSignOut,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ── Section card ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Theme dropdown ────────────────────────────────────────────────────────────

@Composable
private fun ThemeDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = THEME_OPTIONS.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            THEME_OPTIONS.forEach { (value, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

// ── Clock style dropdown ──────────────────────────────────────────────────────

@Composable
private fun ClockStyleDropdown(selected: ClockStyle, onSelect: (ClockStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Clock style") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SUPPORTED_CLOCK_STYLES.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = { onSelect(style); expanded = false },
                )
            }
        }
    }
}

// ── Hours / decimal field ─────────────────────────────────────────────────────

@Composable
private fun HoursField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { msg -> { Text(msg) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Weekend days selector ─────────────────────────────────────────────────────

@Composable
private fun WeekendDaysSelector(selected: List<Int>, onChange: (List<Int>) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { day ->
                FilterChip(
                    selected = day in selected,
                    onClick = {
                        val updated = if (day in selected) selected.filter { it != day }
                                      else (selected + day).sorted()
                        onChange(updated)
                    },
                    label = { Text(DAY_LABELS[day]) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (4..6).forEach { day ->
                FilterChip(
                    selected = day in selected,
                    onClick = {
                        val updated = if (day in selected) selected.filter { it != day }
                                      else (selected + day).sorted()
                        onChange(updated)
                    },
                    label = { Text(DAY_LABELS[day]) },
                )
            }
        }
    }
}

// ── Toggle row ────────────────────────────────────────────────────────────────

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Info row ──────────────────────────────────────────────────────────────────

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ── Account section ───────────────────────────────────────────────────────────

@Composable
private fun AccountSection(
    authState: AuthUiState,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit,
) {
    when (authState) {
        is AuthUiState.NotConfigured -> Text(
            text = "Running in local-only mode (Supabase not configured).",
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
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onResetPassword,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset password")
            }
            Spacer(Modifier.height(8.dp))
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

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ElmTrackrTheme {
        SettingsContent(
            state = SettingsUiState.Ready(
                settings = UserSettings(
                    id = "s1",
                    userId = "u1",
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            ),
            authState = AuthUiState.NotConfigured,
            onSave = { _, _, _, _, _, _ -> },
            onSignOut = {},
            onFeatureFlag = { _, _ -> },
            onWeekendDays = {},
            onTheme = {},
            onSync = {},
            onResetPassword = {},
        )
    }
}
