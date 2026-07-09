@file:OptIn(ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.BuildConfig
import com.elmtrackr.app.R
import com.elmtrackr.app.monitoring.CrashReporting
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.AuroraPlum
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
            SettingsGroupedSection(title = stringResource(R.string.settings_section_tracking_pay)) {
                SettingsHubNavRow(
                    title = stringResource(R.string.settings_pay_overtime),
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
                    icon = Icons.Filled.CreditCard,
                    iconTint = AuroraIndigo,
                )
            }
        }
        item {
            SettingsGroupedSection(title = stringResource(R.string.settings_section_app)) {
                SettingsHubNavRow(
                    title = stringResource(R.string.settings_appearance_clock),
                    subtitle = appearanceSummary(state.selectedTheme, clockStyle, clockStyles),
                    onClick = { onNavigate(SettingsDestination.APPEARANCE) },
                    icon = Icons.Filled.Schedule,
                    iconTint = AuroraPlum,
                )
                SettingsHubNavRow(
                    title = stringResource(R.string.settings_features),
                    subtitle = featuresSummary(travelRefunds, insights, clockStyles, overtimeReminders),
                    onClick = { onNavigate(SettingsDestination.FEATURES) },
                    icon = Icons.Filled.Tune,
                    iconTint = AuroraAqua,
                )
                SettingsHubNavRow(
                    title = stringResource(R.string.settings_security),
                    subtitle = stringResource(R.string.settings_security_subtitle),
                    onClick = { onNavigate(SettingsDestination.SECURITY) },
                    showDivider = false,
                    icon = Icons.Filled.Shield,
                    iconTint = AuroraIndigo,
                )
            }
        }
        item {
            SettingsGroupedSection(title = stringResource(R.string.settings_section_support)) {
                SettingsHubNavRow(
                    title = stringResource(R.string.settings_help_about),
                    subtitle = stringResource(R.string.settings_help_about_subtitle, BuildConfig.VERSION_NAME),
                    onClick = { onNavigate(SettingsDestination.HELP) },
                    showDivider = false,
                    icon = Icons.Outlined.Info,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (authState.isLoading) R.string.settings_signing_out else R.string.settings_sign_out),
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(CornerRadius.Medium),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.settings_delete_account_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_delete_account_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        showDeleteSheet = false
                        onDeleteAccount()
                    },
                    enabled = !state.isDeletingAccount,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(
                        stringResource(if (state.isDeletingAccount) R.string.settings_deleting_account else R.string.settings_delete_account),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showDeleteSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_cancel))
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
        item { SettingsDetailHeader(title = stringResource(R.string.settings_profile), onBack = onBack) }
        item {
            SettingsSectionCardPlain {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(SettingsAvatarGradient, RoundedCornerShape(percent = 30)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initial,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        item {
            SettingsSectionCardPlain {
                SettingsSubsectionLabel(stringResource(R.string.settings_display_name))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_display_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsSectionCardPlain {
                SettingsSubsectionLabel(stringResource(R.string.settings_security))
                Spacer(Modifier.height(8.dp))
                SettingsInfoRow(stringResource(R.string.settings_email), state.profile?.email ?: "—")
                if (authState is AuthUiState.SignedIn) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onResetPassword,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                    ) {
                        Text(stringResource(R.string.settings_send_password_reset), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.passwordResetFeedback?.asString() ?: stringResource(R.string.settings_password_reset_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.passwordResetFeedback != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        if (authState is AuthUiState.SignedIn) {
            item {
                SettingsSectionCardPlain {
                    Text(
                        stringResource(R.string.settings_danger_zone),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_delete_account_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showDeleteSheet = true },
                        enabled = !authState.isLoading && !state.isDeletingAccount,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_delete_account), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (authState is AuthUiState.NotConfigured) {
            item {
                Text(
                    stringResource(R.string.settings_local_only_mode),
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
    onOpenPremiumProfiles: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = stringResource(R.string.settings_pay_overtime), onBack = onBack) }
        if (state.compensationProfileCount > 1) {
            item {
                // Post-migration, these fields mirror only the DEFAULT job
                // profile; without this note a user editing "daily overtime"
                // here expects it to affect their second job too.
                Text(
                    stringResource(
                        R.string.settings_pay_default_job_note,
                        state.defaultCompensationProfileName.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsNavRow(
                title = stringResource(R.string.settings_compensation_rules),
                subtitle = stringResource(R.string.settings_compensation_rules_subtitle),
                onClick = onOpenCompensation,
            )
        }
        item {
            SettingsNavRow(
                title = stringResource(R.string.settings_premium_profiles),
                subtitle = stringResource(R.string.settings_premium_profiles_subtitle),
                onClick = onOpenPremiumProfiles,
            )
        }
        item {
            SettingsNavRow(
                title = stringResource(R.string.settings_tasks),
                subtitle = stringResource(R.string.settings_tasks_subtitle),
                onClick = onOpenTasks,
            )
        }
        item {
            Column {
                SettingsSubsectionLabel(stringResource(R.string.settings_pay_rate))
                Spacer(Modifier.height(8.dp))
                HoursField(
                    label = stringResource(R.string.settings_hourly_rate),
                    value = hourlyRateText,
                    onValueChange = onHourlyRateChange,
                    error = state.validationErrors["hourlyRate"]?.asString(),
                )
                Spacer(Modifier.height(12.dp))
                CurrencyDropdown(selected = currency, onSelect = onCurrencyChange)
            }
        }
        item {
            Column {
                SettingsSubsectionLabel(stringResource(R.string.settings_overtime_thresholds))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_overtime_thresholds_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                HoursField(
                    label = stringResource(R.string.settings_daily_overtime_hours),
                    value = dailyOtText,
                    onValueChange = onDailyOtChange,
                    error = state.validationErrors["dailyOt"]?.asString(),
                )
                Spacer(Modifier.height(8.dp))
                HoursField(
                    label = stringResource(R.string.settings_weekly_overtime_hours),
                    value = weeklyOtText,
                    onValueChange = onWeeklyOtChange,
                    error = state.validationErrors["weeklyOt"]?.asString(),
                )
            }
        }
        item {
            Column {
                SettingsSubsectionLabel(stringResource(R.string.settings_weekend_days))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_weekend_days_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                WeekendDaysSelector(selected = weekendDays, onChange = onWeekendDaysChange)
            }
        }
        item {
            Column {
                SettingsSubsectionLabel(stringResource(R.string.settings_location))
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
    onClockStylesEnabledChange: (Boolean) -> Unit,
    reduceMotionEnabled: Boolean,
    onReduceMotionChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = stringResource(R.string.settings_appearance_clock), onBack = onBack) }
        item {
            LanguageSegmentedControl()
        }
        item {
            ThemeSegmentedControl(selected = state.selectedTheme, onSelect = onTheme)
        }
        item {
            SettingsToggleRow(
                title = stringResource(R.string.settings_clock_faces),
                description = stringResource(R.string.settings_clock_faces_desc),
                checked = clockStylesEnabled,
                onCheckedChange = onClockStylesEnabledChange,
                icon = Icons.Filled.Schedule,
                iconTint = AuroraPlum,
            )
        }
        item {
            SettingsToggleRow(
                title = stringResource(R.string.settings_reduce_motion),
                description = stringResource(R.string.settings_reduce_motion_desc),
                checked = reduceMotionEnabled,
                onCheckedChange = onReduceMotionChange,
                icon = Icons.Filled.MotionPhotosOff,
                iconTint = AuroraPlum,
            )
        }
        if (clockStylesEnabled) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_preview),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    AnimatedContent(
                        targetState = clockStyle,
                        transitionSpec = {
                            fadeIn(tween(220, easing = AuroraEaseOut)) togetherWith
                                fadeOut(tween(160, easing = AuroraEaseOut))
                        },
                        label = "appearance-clock-hero",
                    ) { style ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                WatchFacePreview(style, selected = true)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    clockStyleDisplayName(style),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    watchFaceDescription(style),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
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
        item { SettingsDetailHeader(title = stringResource(R.string.settings_features), onBack = onBack) }
        item {
            SettingsToggleRow(
                title = stringResource(R.string.settings_travel_refunds),
                description = stringResource(R.string.settings_travel_refunds_desc),
                checked = travelRefunds,
                onCheckedChange = onTravelRefundsChange,
                icon = Icons.Filled.LocalShipping,
                iconTint = AuroraPeachDeep,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_insights_analytics),
                description = stringResource(R.string.settings_insights_analytics_desc),
                checked = insights,
                onCheckedChange = onInsightsChange,
                icon = Icons.AutoMirrored.Filled.ShowChart,
                iconTint = FeaturesInsightsGreen,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_overtime_reminders),
                description = stringResource(R.string.settings_overtime_reminders_desc),
                checked = overtimeReminders,
                onCheckedChange = onOvertimeRemindersChange,
                icon = Icons.Filled.NotificationsActive,
                iconTint = AuroraIndigo,
            )
        }
        item {
            Text(
                stringResource(R.string.settings_features_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

private val FeaturesInsightsGreen = Color(0xFF1E9E63)

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
        item { SettingsDetailHeader(title = stringResource(R.string.settings_help_about), onBack = onBack) }
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                Spacer(Modifier.height(8.dp))
                SettingsNavRow(
                    title = stringResource(R.string.settings_privacy_policy),
                    subtitle = stringResource(R.string.settings_privacy_policy_subtitle),
                    onClick = { openExternalUrl(context, LegalDocuments.PRIVACY_POLICY_URL) },
                )
                if (CrashReporting.isAvailable()) {
                    Spacer(Modifier.height(8.dp))
                    var crashReportsEnabled by remember {
                        mutableStateOf(CrashReporting.isEnabledByUser(context))
                    }
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_crash_reports),
                        description = stringResource(R.string.settings_crash_reports_desc),
                        checked = crashReportsEnabled,
                        onCheckedChange = { enabled ->
                            crashReportsEnabled = enabled
                            CrashReporting.setEnabledByUser(context, enabled)
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                SettingsNavRow(
                    title = stringResource(R.string.settings_terms_of_service),
                    subtitle = stringResource(R.string.settings_terms_subtitle),
                    onClick = onOpenTerms,
                )
                Spacer(Modifier.height(8.dp))
                SettingsNavRow(
                    title = stringResource(R.string.settings_replay_onboarding),
                    subtitle = stringResource(R.string.settings_replay_onboarding_subtitle),
                    onClick = onReplayOnboarding,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_support_email, LegalDocuments.CONTACT_EMAIL),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (authState != null) {
            item {
                SettingsSectionCard(title = stringResource(R.string.settings_section_sync)) {
                    val statusLine = when {
                        state.isSyncing -> stringResource(R.string.settings_syncing)
                        state.syncFailedCount > 0 ->
                            stringResource(R.string.settings_sync_pending_failed, state.syncPendingCount, state.syncFailedCount)
                        state.syncPendingCount > 0 -> stringResource(R.string.settings_sync_changes_waiting, state.syncPendingCount)
                        else -> stringResource(R.string.settings_all_changes_synced)
                    }
                    SettingsInfoRow(stringResource(R.string.settings_status), statusLine)
                    state.lastSyncStatus?.let { last ->
                        Spacer(Modifier.height(8.dp))
                        SettingsInfoRow(stringResource(R.string.settings_last_sync), SyncStatusText.format(last)?.asString() ?: last)
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingsNavRow(
                        title = stringResource(R.string.settings_sync_details),
                        subtitle = when {
                            state.syncFailedCount > 0 -> stringResource(R.string.settings_sync_failed_view_rows, state.syncFailedCount)
                            state.syncPendingCount > 0 -> stringResource(R.string.settings_sync_pending_view_breakdown, state.syncPendingCount)
                            else -> stringResource(R.string.settings_sync_details_subtitle)
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
                        Text(stringResource(if (state.isSyncing) R.string.settings_syncing else R.string.settings_sync_now))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
