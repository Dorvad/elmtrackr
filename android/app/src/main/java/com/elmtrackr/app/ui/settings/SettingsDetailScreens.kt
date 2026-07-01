@file:OptIn(ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.BuildConfig
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

@Composable
internal fun SettingsHub(
    state: SettingsUiState.Ready,
    displayName: String,
    hourlyRateText: String,
    currency: CurrencyCode,
    dailyOtText: String,
    weeklyOtText: String,
    weekendDays: List<Int>,
    timezone: String,
    clockStyle: ClockStyle,
    travelRefunds: Boolean,
    insights: Boolean,
    clockStyles: Boolean,
    overtimeReminders: Boolean,
    authState: AuthUiState?,
    onNavigate: (SettingsDestination) -> Unit,
    onSignOut: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsPageHeader() }
        item {
            SettingsProfileHeroCard(
                displayName = displayName,
                email = state.profile?.email,
                onClick = { onNavigate(SettingsDestination.PROFILE) },
            )
        }
        item {
            SettingsGroupedSection(title = "TRACKING & PAY") {
                SettingsHubNavRow(
                    title = "Pay & overtime",
                    subtitle = payrollSummary(
                        hourlyRateText = hourlyRateText,
                        currency = currency,
                        dailyOtText = dailyOtText,
                        weeklyOtText = weeklyOtText,
                        weekendDays = weekendDays,
                        timezone = timezone,
                    ),
                    onClick = { onNavigate(SettingsDestination.PAY) },
                    showDivider = false,
                )
            }
        }
        item {
            SettingsGroupedSection(title = "APP") {
                SettingsHubNavRow(
                    title = "Appearance & clock",
                    subtitle = appearanceSummary(state.selectedTheme, clockStyle, clockStyles),
                    onClick = { onNavigate(SettingsDestination.APPEARANCE) },
                )
                SettingsHubNavRow(
                    title = "Features",
                    subtitle = featuresSummary(travelRefunds, insights, clockStyles, overtimeReminders),
                    onClick = { onNavigate(SettingsDestination.FEATURES) },
                )
                SettingsHubNavRow(
                    title = "Security",
                    subtitle = "App lock & encrypted storage",
                    onClick = { onNavigate(SettingsDestination.SECURITY) },
                    showDivider = false,
                )
            }
        }
        item {
            SettingsGroupedSection(title = "SUPPORT") {
                SettingsHubNavRow(
                    title = "Help & about",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} · sync & legal",
                    onClick = { onNavigate(SettingsDestination.HELP) },
                    showDivider = false,
                )
            }
        }
        if (authState is AuthUiState.SignedIn) {
            item {
                TextButton(
                    onClick = onSignOut,
                    enabled = !authState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        if (authState.isLoading) "Signing out…" else "Sign out",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileDetailScreen(
    state: SettingsUiState.Ready,
    authState: AuthUiState?,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onResetPassword: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var showDeleteSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    if (showDeleteSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDeleteSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH)
                    .padding(bottom = Spacing.xl),
            ) {
                Text(
                    "Delete account?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "This permanently deletes your cloud account, shifts, settings, refund claims, " +
                        "and receipt photos. Local data on this device will also be removed. " +
                        "This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        showDeleteSheet = false
                        onDeleteAccount()
                    },
                    enabled = !state.isDeletingAccount,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(if (state.isDeletingAccount) "Deleting account…" else "Delete account")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showDeleteSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = "Profile", onBack = onBack) }
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(AuroraIndigo.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initial,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = AuroraIndigo,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.Medium),
            )
        }
        item {
            OutlinedTextField(
                value = state.profile?.email ?: "",
                onValueChange = {},
                label = { Text("Email") },
                readOnly = true,
                enabled = false,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.Medium),
            )
        }
        if (authState is AuthUiState.SignedIn) {
            item {
                OutlinedButton(onClick = onResetPassword, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset password")
                }
                state.passwordResetFeedback?.let { feedback ->
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { showDeleteSheet = true },
                    enabled = !authState.isLoading && !state.isDeletingAccount,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete account")
                }
            }
        } else if (authState is AuthUiState.NotConfigured) {
            item {
                Text(
                    "Running in local-only mode (Supabase not configured).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
internal fun PayDetailScreen(
    state: SettingsUiState.Ready,
    hourlyRateText: String,
    onHourlyRateChange: (String) -> Unit,
    currency: CurrencyCode,
    onCurrencyChange: (CurrencyCode) -> Unit,
    dailyOtText: String,
    onDailyOtChange: (String) -> Unit,
    weeklyOtText: String,
    onWeeklyOtChange: (String) -> Unit,
    weekendDays: List<Int>,
    onWeekendDaysChange: (List<Int>) -> Unit,
    timezone: String,
    onTimezoneChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenCompensation: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = "Pay & overtime", onBack = onBack) }
        item {
            SettingsNavRow(
                title = "Compensation rules",
                subtitle = "Region presets, overtime tiers, and premiums",
                onClick = onOpenCompensation,
            )
        }
        item {
            SettingsNavRow(
                title = "Tasks",
                subtitle = "Optional task labels for clock-in",
                onClick = onOpenTasks,
            )
        }
        item {
            Column {
                SettingsSubsectionLabel("Pay rate")
                Spacer(Modifier.height(8.dp))
                HoursField(
                    label = "Hourly rate",
                    value = hourlyRateText,
                    onValueChange = onHourlyRateChange,
                    error = state.validationErrors["hourlyRate"],
                )
                Spacer(Modifier.height(12.dp))
                CurrencyDropdown(selected = currency, onSelect = onCurrencyChange)
            }
        }
        item {
            Column {
                SettingsSubsectionLabel("Overtime thresholds")
                Spacer(Modifier.height(8.dp))
                HoursField(
                    label = "Daily overtime (hours)",
                    value = dailyOtText,
                    onValueChange = onDailyOtChange,
                    error = state.validationErrors["dailyOt"],
                )
                Spacer(Modifier.height(8.dp))
                HoursField(
                    label = "Weekly overtime (hours)",
                    value = weeklyOtText,
                    onValueChange = onWeeklyOtChange,
                    error = state.validationErrors["weeklyOt"],
                )
            }
        }
        item {
            Column {
                SettingsSubsectionLabel("Weekend days")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Selected days count as weekends for overtime and reports.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                WeekendDaysSelector(selected = weekendDays, onChange = onWeekendDaysChange)
            }
        }
        item {
            Column {
                SettingsSubsectionLabel("Location")
                Spacer(Modifier.height(8.dp))
                IanaTimezonePicker(selected = timezone, onSelect = onTimezoneChange)
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
internal fun AppearanceDetailScreen(
    state: SettingsUiState.Ready,
    clockStyle: ClockStyle,
    onClockStyleChange: (ClockStyle) -> Unit,
    clockStylesEnabled: Boolean,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = "Appearance & clock", onBack = onBack) }
        item {
            ThemeSegmentedControl(selected = state.selectedTheme, onSelect = onTheme)
        }
        if (clockStylesEnabled) {
            item {
                ClockStyleDropdown(selected = clockStyle, onSelect = onClockStyleChange)
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
internal fun FeaturesDetailScreen(
    travelRefunds: Boolean,
    onTravelRefundsChange: (Boolean) -> Unit,
    insights: Boolean,
    onInsightsChange: (Boolean) -> Unit,
    clockStyles: Boolean,
    onClockStylesChange: (Boolean) -> Unit,
    overtimeReminders: Boolean,
    onOvertimeRemindersChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = "Features", onBack = onBack) }
        item {
            SettingsToggleRow(
                title = "Travel refunds",
                description = "Track and manage travel refund claims",
                checked = travelRefunds,
                onCheckedChange = onTravelRefundsChange,
            )
            SettingsToggleRow(
                title = "Insights",
                description = "View trends and patterns in your work history",
                checked = insights,
                onCheckedChange = onInsightsChange,
            )
            SettingsToggleRow(
                title = "Clock styles",
                description = "Choose from different clock display styles",
                checked = clockStyles,
                onCheckedChange = onClockStylesChange,
            )
            SettingsToggleRow(
                title = "Overtime reminders",
                description = "Notify 30 minutes before overtime and hourly while in overtime",
                checked = overtimeReminders,
                onCheckedChange = onOvertimeRemindersChange,
            )
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
internal fun HelpDetailScreen(
    state: SettingsUiState.Ready,
    authState: AuthUiState?,
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenSyncDetails: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = "Help & about", onBack = onBack) }
        item {
            SettingsSectionCard(title = "ABOUT") {
                SettingsInfoRow("Version", BuildConfig.VERSION_NAME)
                Spacer(Modifier.height(8.dp))
                SettingsNavRow(
                    title = "Privacy Policy",
                    subtitle = "How we handle your data",
                    onClick = { openExternalUrl(context, LegalDocuments.PRIVACY_POLICY_URL) },
                )
                Spacer(Modifier.height(8.dp))
                SettingsNavRow(
                    title = "Terms of Service",
                    subtitle = "Usage terms and conditions",
                    onClick = onOpenTerms,
                )
                Spacer(Modifier.height(8.dp))
                SettingsNavRow(
                    title = "Replay onboarding",
                    subtitle = "Review feature setup and choices",
                    onClick = onReplayOnboarding,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Support: ${LegalDocuments.CONTACT_EMAIL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (authState != null) {
            item {
                SettingsSectionCard(title = "SYNC") {
                    val statusLine = when {
                        state.isSyncing -> "Syncing…"
                        state.syncFailedCount > 0 ->
                            "${state.syncPendingCount} pending · ${state.syncFailedCount} failed"
                        state.syncPendingCount > 0 -> "${state.syncPendingCount} changes waiting to sync"
                        else -> "All changes synced"
                    }
                    SettingsInfoRow("Status", statusLine)
                    state.lastSyncStatus?.let { last ->
                        Spacer(Modifier.height(8.dp))
                        SettingsInfoRow("Last sync", last)
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingsNavRow(
                        title = "Sync details",
                        subtitle = when {
                            state.syncFailedCount > 0 -> "${state.syncFailedCount} failed · view pending rows"
                            state.syncPendingCount > 0 -> "${state.syncPendingCount} pending · view breakdown"
                            else -> "Local vs synced status and backup export"
                        },
                        onClick = onOpenSyncDetails,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onSyncNow,
                        enabled = !state.isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isSyncing) "Syncing…" else "Sync now")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
