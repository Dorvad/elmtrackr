@file:OptIn(ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.elmtrackr.app.BuildConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.components.states.LoadingState
import androidx.compose.foundation.layout.widthIn
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmSyncPill
import com.elmtrackr.app.ui.sync.resolveSyncStatus
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import java.time.Instant

private val SUPPORTED_CLOCK_STYLES = ClockStyle.entries

private fun minutesToHours(minutes: Int): String {
    val h = minutes / 60.0
    return if (h == h.toLong().toDouble()) h.toLong().toString() else "%.2f".format(h)
}

private fun supportedClockStyleOf(style: ClockStyle): ClockStyle = style

private val THEME_OPTIONS = listOf("system" to "System default", "light" to "Light", "dark" to "Dark")
private val DAY_LABELS    = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

private enum class LegalDoc { PRIVACY, TERMS }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    authState: AuthUiState?      = null,
    onSignOut: () -> Unit        = {},
    onReplayOnboarding: () -> Unit = {},
) {
    var showCompensation by rememberSaveable { mutableStateOf(false) }
    var legalDoc by rememberSaveable { mutableStateOf<LegalDoc?>(null) }
    if (showCompensation) {
        CompensationSettingsScreen(onBack = { showCompensation = false })
        return
    }
    when (legalDoc) {
        LegalDoc.PRIVACY -> LegalDocumentScreen(
            title = "Privacy Policy",
            sections = LegalDocuments.privacyPolicy,
            lastUpdated = LegalDocuments.LAST_UPDATED,
            onBack = { legalDoc = null },
        )
        LegalDoc.TERMS -> LegalDocumentScreen(
            title = "Terms of Service",
            sections = LegalDocuments.termsOfService,
            lastUpdated = LegalDocuments.LAST_UPDATED,
            onBack = { legalDoc = null },
        )
        null -> Unit
    }
    if (legalDoc != null) return

    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureSettingsExist() }

    AuroraListScreen {
        when (val state = uiState) {
            is SettingsUiState.Loading -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH),
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Spacing.lg))
                Spacer(Modifier.height(Spacing.md))
                SettingsSkeleton()
            }
            is SettingsUiState.Ready -> SettingsContent(
                state         = state,
                authState     = authState,
                onSave        = viewModel::saveSettings,
                onSignOut     = onSignOut,
                onTheme       = viewModel::saveTheme,
                onSync        = viewModel::triggerSync,
                onResetPassword = viewModel::resetPassword,
                onReplayOnboarding = onReplayOnboarding,
                onOpenCompensation = { showCompensation = true },
                onDismissSaveFeedback = viewModel::clearSaveFeedback,
                onDeleteAccount = viewModel::deleteAccount,
                onDismissAccountFeedback = viewModel::clearAccountActionFeedback,
                onOpenPrivacy = { legalDoc = LegalDoc.PRIVACY },
                onOpenTerms = { legalDoc = LegalDoc.TERMS },
            )
            is SettingsUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::ensureSettingsExist,
            )
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Ready,
    authState: AuthUiState?,
    onSave: (String, Double, Double, Double?, String, ClockStyle, CurrencyCode, List<Int>, SettingsFeatureFlags) -> Unit,
    onSignOut: () -> Unit,
    onTheme: (String) -> Unit,
    onSync: () -> Unit,
    onResetPassword: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenCompensation: () -> Unit = {},
    onDismissSaveFeedback: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onDismissAccountFeedback: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
) {
    var displayName   by remember(state.profile?.fullName)                       { mutableStateOf(state.profile?.fullName ?: "") }
    var dailyOtText   by remember(state.settings.dailyOvertimeThresholdMinutes)  { mutableStateOf(minutesToHours(state.settings.dailyOvertimeThresholdMinutes)) }
    var weeklyOtText  by remember(state.settings.weeklyOvertimeThresholdMinutes) { mutableStateOf(minutesToHours(state.settings.weeklyOvertimeThresholdMinutes)) }
    var hourlyRateText by remember(state.settings.hourlyRate)                    { mutableStateOf(state.settings.hourlyRate?.toString() ?: "") }
    var timezone      by remember(state.settings.timezone)                       { mutableStateOf(state.settings.timezone) }
    var clockStyle    by remember(state.settings.clockStyle)                     { mutableStateOf(supportedClockStyleOf(state.settings.clockStyle)) }
    var currency      by remember(state.settings.currency)                       { mutableStateOf(state.settings.currency) }
    var travelRefunds by remember(state.settings.featuresTravelRefunds)          { mutableStateOf(state.settings.featuresTravelRefunds) }
    var insights      by remember(state.settings.featuresInsights)               { mutableStateOf(state.settings.featuresInsights) }
    var clockStyles   by remember(state.settings.featuresClockStyles)            { mutableStateOf(state.settings.featuresClockStyles) }
    var weekendDays   by remember(state.settings.weekendDays)                    { mutableStateOf(state.settings.weekendDays) }

    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var payrollExpanded by rememberSaveable { mutableStateOf(false) }
    var featuresExpanded by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.saveFeedback) {
        val feedback = state.saveFeedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = feedback.message,
            duration = if (feedback.isError) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        onDismissSaveFeedback()
    }
    LaunchedEffect(state.accountActionFeedback) {
        val feedback = state.accountActionFeedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = feedback, duration = SnackbarDuration.Long)
        onDismissAccountFeedback()
        if (feedback == "Account deleted" || feedback == "Local data cleared") {
            onSignOut()
        }
    }

    val saveAction: () -> Unit = {
        onSave(
            displayName,
            dailyOtText.toDoubleOrNull() ?: 0.0,
            weeklyOtText.toDoubleOrNull() ?: 0.0,
            hourlyRateText.toDoubleOrNull(),
            timezone,
            clockStyle,
            currency,
            weekendDays,
            SettingsFeatureFlags(
                travelRefunds = travelRefunds,
                paidProjects = state.settings.featuresPaidProjects,
                insights = insights,
                clockStyles = clockStyles,
            ),
        )
    }

    val isDirty = displayName.trim() != (state.profile?.fullName ?: "").trim() ||
        dailyOtText != minutesToHours(state.settings.dailyOvertimeThresholdMinutes) ||
        weeklyOtText != minutesToHours(state.settings.weeklyOvertimeThresholdMinutes) ||
        hourlyRateText != (state.settings.hourlyRate?.toString() ?: "") ||
        timezone != state.settings.timezone ||
        clockStyle != supportedClockStyleOf(state.settings.clockStyle) ||
        currency != state.settings.currency ||
        weekendDays.sorted() != state.settings.weekendDays.sorted() ||
        travelRefunds != state.settings.featuresTravelRefunds ||
        insights != state.settings.featuresInsights ||
        clockStyles != state.settings.featuresClockStyles

    val syncStatus = resolveSyncStatus(
        isRemoteConfigured = state.isRemoteConfigured,
        isOnline = state.isOnline,
        isSyncing = state.isSyncing,
        pendingCount = state.pendingCount,
        lastSyncStatus = state.lastSyncStatus,
        syncError = state.syncError,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(visible = isDirty) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenH, vertical = 12.dp),
                    ) {
                        Text(
                            "You have unsaved changes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        ElmGradientButton(
                            onClick = saveAction,
                            enabled = !state.isSaving,
                            accessibilityLabel = "Save settings",
                        ) {
                            Text(if (state.isSaving) "Saving..." else "Save Settings", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.screenH),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { Spacer(Modifier.height(Spacing.lg)) }
            item {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Theme saves immediately. Everything else uses the save bar when you have unsaved changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(Spacing.md)) }

            item {
                SettingsSectionCard("Profile") {
                    OutlinedTextField(
                        value         = displayName,
                        onValueChange = { displayName = it },
                        label         = { Text("Display name") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                    state.profile?.let { profile ->
                        Spacer(Modifier.height(8.dp))
                        Text(profile.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                CollapsibleSettingsSection(
                    title = "Appearance",
                    summary = appearanceSummary(state.selectedTheme, clockStyle, clockStyles),
                    expanded = appearanceExpanded,
                    onExpandedChange = { appearanceExpanded = it },
                ) {
                    ThemeDropdown(selected = state.selectedTheme, onSelect = onTheme)
                    if (clockStyles) {
                        Spacer(Modifier.height(12.dp))
                        ClockStyleDropdown(selected = clockStyle, onSelect = { clockStyle = it })
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                        Text("Review feature setup")
                    }
                }
            }

            item {
                CollapsibleSettingsSection(
                    title = "Payroll",
                    summary = payrollSummary(
                        hourlyRateText = hourlyRateText,
                        currency = currency,
                        dailyOtText = dailyOtText,
                        weeklyOtText = weeklyOtText,
                        weekendDays = weekendDays,
                        timezone = timezone,
                    ),
                    expanded = payrollExpanded,
                    onExpandedChange = { payrollExpanded = it },
                ) {
                    OutlinedButton(onClick = onOpenCompensation, modifier = Modifier.fillMaxWidth()) {
                        Text("Compensation rules")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Configure region presets, overtime tiers, premiums, and currency for pay estimates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    PayrollSubsectionTitle("Pay rate")
                    Spacer(Modifier.height(8.dp))
                    HoursField(
                        label         = "Hourly rate",
                        value         = hourlyRateText,
                        onValueChange = { hourlyRateText = it },
                        error         = state.validationErrors["hourlyRate"],
                    )
                    Spacer(Modifier.height(12.dp))
                    CurrencyDropdown(selected = currency, onSelect = { currency = it })
                    Spacer(Modifier.height(16.dp))
                    PayrollSubsectionTitle("Overtime thresholds")
                    Spacer(Modifier.height(8.dp))
                    HoursField(
                        label         = "Daily overtime (hours)",
                        value         = dailyOtText,
                        onValueChange = { dailyOtText = it },
                        error         = state.validationErrors["dailyOt"],
                    )
                    Spacer(Modifier.height(8.dp))
                    HoursField(
                        label         = "Weekly overtime (hours)",
                        value         = weeklyOtText,
                        onValueChange = { weeklyOtText = it },
                        error         = state.validationErrors["weeklyOt"],
                    )
                    Spacer(Modifier.height(16.dp))
                    PayrollSubsectionTitle("Weekend days")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Selected days count as weekends for overtime and reports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    WeekendDaysSelector(selected = weekendDays, onChange = { weekendDays = it })
                    Spacer(Modifier.height(16.dp))
                    PayrollSubsectionTitle("Location")
                    Spacer(Modifier.height(8.dp))
                    IanaTimezonePicker(selected = timezone, onSelect = { timezone = it })
                }
            }

            item {
                CollapsibleSettingsSection(
                    title = "Features",
                    summary = featuresSummary(travelRefunds, insights, clockStyles),
                    expanded = featuresExpanded,
                    onExpandedChange = { featuresExpanded = it },
                ) {
                    ToggleRow(
                        title         = "Travel Refunds",
                        description   = "Track and manage travel refund claims",
                        checked       = travelRefunds,
                        onCheckedChange = { travelRefunds = it },
                    )
                    ToggleRow(
                        title         = "Insights",
                        description   = "View trends and patterns in your work history",
                        checked       = insights,
                        onCheckedChange = { insights = it },
                    )
                    ToggleRow(
                        title         = "Clock Styles",
                        description   = "Choose from different clock display styles",
                        checked       = clockStyles,
                        onCheckedChange = { clockStyles = it },
                    )
                }
            }

            item {
                SettingsSectionCard("Sync") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Status", style = MaterialTheme.typography.bodyMedium)
                        ElmSyncPill(
                            status = syncStatus,
                            onClick = if (syncStatus.isActionable) onSync else null,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        syncStatus.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (syncStatus.isActionable) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap the status pill to sync now.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                SettingsSectionCard("About & Legal") {
                    InfoRow("Version", BuildConfig.VERSION_NAME)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
                        Text("Privacy Policy")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenTerms, modifier = Modifier.fillMaxWidth()) {
                        Text("Terms of Service")
                    }
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
                    SettingsSectionCard("Account") {
                        AccountSection(
                            authState       = authState,
                            passwordResetFeedback = state.passwordResetFeedback,
                            isDeletingAccount = state.isDeletingAccount,
                            onResetPassword = onResetPassword,
                            onSignOut       = onSignOut,
                            onDeleteAccount = onDeleteAccount,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(if (isDirty) 96.dp else 32.dp)) }
        }
    }
}

private fun appearanceSummary(theme: String, clockStyle: ClockStyle, clockStylesEnabled: Boolean): String {
    val themeLabel = THEME_OPTIONS.firstOrNull { it.first == theme }?.second ?: theme
    return if (clockStylesEnabled) {
        "$themeLabel · ${clockStyle.name.lowercase().replaceFirstChar(Char::uppercase)} face"
    } else {
        themeLabel
    }
}

private fun payrollSummary(
    hourlyRateText: String,
    currency: CurrencyCode,
    dailyOtText: String,
    weeklyOtText: String,
    weekendDays: List<Int>,
    timezone: String,
): String {
    val ratePart = hourlyRateText.toDoubleOrNull()?.let { "${currency.symbol}$it/hr" } ?: "No hourly rate"
    val weekendPart = weekendDays.sorted().joinToString(", ") { DAY_LABELS[it] }
    return "$ratePart · OT $dailyOtText/$weeklyOtText h · $weekendPart · $timezone"
}

private fun featuresSummary(
    travelRefunds: Boolean,
    insights: Boolean,
    clockStyles: Boolean,
): String {
    val enabled = listOfNotNull(
        "Travel Refunds".takeIf { travelRefunds },
        "Insights".takeIf { insights },
        "Clock Styles".takeIf { clockStyles },
    )
    return when (enabled.size) {
        0 -> "No optional features enabled"
        in 1..2 -> enabled.joinToString(" · ")
        else -> "${enabled.size} features enabled"
    }
}

@Composable
private fun PayrollSubsectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CollapsibleSettingsSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElmCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerRadius.Small))
                    .clickable { onExpandedChange(!expanded) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    ElmSectionHeader(title, modifier = Modifier.fillMaxWidth())
                    AnimatedVisibility(
                        visible = !expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

// â”€â”€ Section card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElmCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            ElmSectionHeader(title, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// â”€â”€ Theme / clock dropdowns â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ThemeDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label    = THEME_OPTIONS.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = label,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Theme") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            THEME_OPTIONS.forEach { (value, display) ->
                DropdownMenuItem(text = { Text(display) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun CurrencyDropdown(selected: CurrencyCode, onSelect: (CurrencyCode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "${selected.symbol}  ${selected.name} - ${selected.displayName}",
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CurrencyCode.entries.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.symbol}  ${currency.name} - ${currency.displayName}") },
                    onClick = { onSelect(currency); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ClockStyleDropdown(selected: ClockStyle, onSelect: (ClockStyle) -> Unit) {
    Column {
        Text("Watch face", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Choose an animated face for the Dashboard clock.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        SUPPORTED_CLOCK_STYLES.chunked(2).forEach { styles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                styles.forEach { style ->
                    val isSelected = style == selected
                    Card(
                        onClick = { onSelect(style) },
                        modifier = Modifier.weight(1f).then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(CornerRadius.Medium))
                            else Modifier,
                        ),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            WatchFacePreview(style, isSelected)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                style.name.lowercase().replaceFirstChar(Char::uppercase),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                watchFaceDescription(style),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (styles.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun WatchFacePreview(style: ClockStyle, selected: Boolean) {
    if (style == ClockStyle.FELLOWSHIP) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(CornerRadius.Medium)),
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.elmtrackr.app.R.drawable.fellowship_bg_shire),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "01:23",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    color = Color(0xFFD4AF37),
                )
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "watch-${style.name}")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (selected) 900 else 1800), RepeatMode.Reverse),
        label = "watch-pulse",
    )
    val darkFace = style in listOf(ClockStyle.FOCUS, ClockStyle.NIGHT, ClockStyle.RETRO, ClockStyle.PULSE)
    val faceBackground = if (darkFace) Color(0xFF11162A) else MaterialTheme.colorScheme.surface
    val accent = when (style) {
        ClockStyle.NIGHT, ClockStyle.PULSE -> Color(0xFF54D8E1)
        ClockStyle.RETRO -> Color(0xFFFFC857)
        ClockStyle.PRISM, ClockStyle.AURORA -> Color(0xFF9B7CFF)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier.fillMaxWidth().height(68.dp).background(faceBackground, RoundedCornerShape(CornerRadius.Medium)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * .39f
            when (style) {
                ClockStyle.CLASSIC, ClockStyle.AURORA -> {
                    drawCircle(accent.copy(alpha = .18f), radius, center, style = Stroke(4f))
                    drawArc(accent, -90f, 260f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(5f, cap = StrokeCap.Round))
                }
                ClockStyle.NIGHT -> repeat(12) { i ->
                    drawCircle(Color.White.copy(alpha = if (i % 3 == 0) pulse else .35f), 1.5f, Offset((i * 31 % 97) / 100f * size.width, (i * 47 % 89) / 100f * size.height))
                }
                ClockStyle.RETRO -> drawRoundRect(accent.copy(alpha = .55f), style = Stroke(2f))
                ClockStyle.PULSE -> repeat(3) { i ->
                    drawCircle(accent.copy(alpha = pulse / (i + 2)), radius * (.55f + i * .28f), center, style = Stroke(2f))
                }
                ClockStyle.DIAL -> repeat(12) { i ->
                    val a = Math.toRadians((i * 30 - 90).toDouble())
                    drawLine(accent, Offset(center.x + kotlin.math.cos(a).toFloat() * radius * .75f, center.y + kotlin.math.sin(a).toFloat() * radius * .75f), Offset(center.x + kotlin.math.cos(a).toFloat() * radius, center.y + kotlin.math.sin(a).toFloat() * radius), 2f)
                }
                ClockStyle.STRAND -> repeat(12) { i ->
                    val x = (i + 1) * size.width / 13f
                    drawLine(if (i < 7) accent else accent.copy(alpha = .2f), Offset(x, 5f), Offset(x, size.height - 5f), if (i == 7) 3f else 1.5f)
                }
                ClockStyle.PRISM -> {
                    val path = Path().apply { moveTo(center.x, 3f); lineTo(8f, size.height - 4f); lineTo(size.width - 8f, size.height - 4f); close() }
                    drawPath(path, accent.copy(alpha = .65f), style = Stroke(2.5f))
                }
                ClockStyle.SAND -> {
                    val top = 8.dp.toPx()
                    val bottom = size.height - 8.dp.toPx()
                    val mid = size.height / 2f
                    val bulbW = size.width * 0.5f
                    val neck = size.width * 0.14f
                    val glass = Path().apply {
                        moveTo(center.x - bulbW / 2f, top)
                        lineTo(center.x + bulbW / 2f, top)
                        lineTo(center.x + neck / 2f, mid)
                        lineTo(center.x + bulbW / 2f, bottom)
                        lineTo(center.x - bulbW / 2f, bottom)
                        lineTo(center.x - neck / 2f, mid)
                        close()
                    }
                    drawPath(glass, accent.copy(alpha = .35f), style = Stroke(2f))
                    val fillH = (bottom - mid) * 0.55f
                    val bottomSand = Path().apply {
                        moveTo(center.x - neck / 2f, bottom - fillH)
                        lineTo(center.x + neck / 2f, bottom - fillH)
                        lineTo(center.x + bulbW / 2f - 6.dp.toPx(), bottom - 4.dp.toPx())
                        lineTo(center.x - bulbW / 2f + 6.dp.toPx(), bottom - 4.dp.toPx())
                        close()
                    }
                    drawPath(bottomSand, accent.copy(alpha = .55f))
                    repeat(2) { i ->
                        drawCircle(accent.copy(alpha = .4f + pulse * .3f), 1.5f, Offset(center.x, mid + (i - 0.5f) * 4.dp.toPx()))
                    }
                }
                ClockStyle.BLOCKS -> {
                    val blockCount = 8
                    val gap = 3.dp.toPx()
                    val blockW = (size.width - gap * (blockCount - 1)) / blockCount
                    val baseY = size.height - 14.dp.toPx()
                    repeat(blockCount) { index ->
                        val x = index * (blockW + gap)
                        val lit = index < 5
                        drawRoundRect(
                            if (lit) accent else accent.copy(alpha = .15f),
                            Offset(x, baseY),
                            Size(blockW, 10.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                }
                ClockStyle.ORBIT -> {
                    val radius = size.minDimension * .34f
                    drawCircle(accent.copy(alpha = .2f), radius, center, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))))
                    val angle = Math.toRadians(45.0)
                    val sat = Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius)
                    drawCircle(accent.copy(alpha = .2f), 8.dp.toPx(), sat)
                    drawCircle(accent, 4.dp.toPx(), sat)
                }
                else -> Unit
            }
        }
        Text(
            "01:23",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (style == ClockStyle.MINIMAL) FontWeight.Light else FontWeight.ExtraBold,
            color = if (darkFace) accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun watchFaceDescription(style: ClockStyle): String = when (style) {
    ClockStyle.CLASSIC -> "Progress ring"
    ClockStyle.MINIMAL -> "Clean display"
    ClockStyle.FOCUS -> "Distraction free"
    ClockStyle.BOLD -> "Large and clear"
    ClockStyle.NIGHT -> "Cyan night glow"
    ClockStyle.RETRO -> "Amber terminal"
    ClockStyle.AURORA -> "Gradient ring"
    ClockStyle.PULSE -> "Glowing rings"
    ClockStyle.DIAL -> "Analog timer"
    ClockStyle.STRAND -> "Linear progress"
    ClockStyle.PRISM -> "Rising spectrum"
    ClockStyle.SAND -> "Flowing hourglass"
    ClockStyle.BLOCKS -> "Hour-by-hour blocks"
    ClockStyle.ORBIT -> "Orbiting satellite"
    ClockStyle.FELLOWSHIP -> "Quest through Middle-earth"
}

// â”€â”€ Hours field â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun HoursField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
) {
    OutlinedTextField(
        value          = value,
        onValueChange  = onValueChange,
        label          = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine     = true,
        isError        = error != null,
        supportingText = error?.let { msg -> { Text(msg) } },
        modifier       = Modifier.fillMaxWidth(),
    )
}

// â”€â”€ Weekend days â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekendDaysSelector(selected: List<Int>, onChange: (List<Int>) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DAY_LABELS.forEachIndexed { day, label ->
            WeekendDayChip(
                label = label,
                selected = day in selected,
                onClick = {
                    val updated = if (day in selected) selected.filter { it != day } else (selected + day).sorted()
                    onChange(updated)
                },
            )
        }
    }
}

@Composable
private fun WeekendDayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(CornerRadius.Small)
    val background = if (selected) AuroraIndigo else auroraSurfaceSub()
    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.shadow(4.dp, shape, ambientColor = AuroraIndigo.copy(alpha = 0.25f), spotColor = AuroraIndigo.copy(alpha = 0.25f))
                } else {
                    Modifier
                },
            )
            .background(background, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = textColor,
    )
}

// â”€â”€ Toggle row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// â”€â”€ Info row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
    }
}

// â”€â”€ Account section â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun AccountSection(
    authState: AuthUiState,
    passwordResetFeedback: String? = null,
    isDeletingAccount: Boolean = false,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showSignOutConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?") },
            text = { Text("Your local changes are kept and will sync the next time you sign in.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSignOutConfirm = false; onSignOut() }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") } },
        )
    }
    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "This permanently deletes your cloud account, shifts, settings, refund claims, and receipt photos. " +
                        "Local data on this device will also be removed. This cannot be undone.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
    when (authState) {
        is AuthUiState.NotConfigured -> Text(
            text  = "Running in local-only mode (Supabase not configured).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is AuthUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        is AuthUiState.SignedIn -> {
            Text(authState.profile.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            authState.profile.fullName?.let { name ->
                Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onResetPassword, modifier = Modifier.fillMaxWidth()) {
                Text("Reset password")
            }
            passwordResetFeedback?.let { feedback ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = { showSignOutConfirm = true },
                enabled  = !authState.isLoading && !isDeletingAccount,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (authState.isLoading) "Signing out..." else "Sign out")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = { showDeleteConfirm = true },
                enabled  = !authState.isLoading && !isDeletingAccount,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isDeletingAccount) "Deleting account..." else "Delete account")
            }
        }
        is AuthUiState.SignedOut       -> Text("Not signed in.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is AuthUiState.PasswordResetSent -> Text("Password reset email sent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is AuthUiState.PasswordRecovery -> Text("Complete password reset on the sign-in screen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is AuthUiState.SignUpConfirmation -> Text("Confirmation sent to ${authState.email}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ElmTrackrTheme {
        SettingsContent(
            state = SettingsUiState.Ready(
                settings = UserSettings(id = "s1", userId = "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
            ),
            authState       = AuthUiState.NotConfigured,
            onSave          = { _, _, _, _, _, _, _, _, _ -> },
            onSignOut       = {},
            onTheme         = {},
            onSync          = {},
            onResetPassword = {},
            onReplayOnboarding = {},
        )
    }
}

