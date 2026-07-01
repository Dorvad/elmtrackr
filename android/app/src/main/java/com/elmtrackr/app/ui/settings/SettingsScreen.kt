@file:OptIn(ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.security.BiometricAuthPrompt
import com.elmtrackr.app.security.BiometricCapability
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.AuroraStateCrossfade
import com.elmtrackr.app.ui.design.auroraSubScreenTransition
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import java.time.Instant

internal enum class SettingsDestination {
    HUB,
    PROFILE,
    PAY,
    APPEARANCE,
    FEATURES,
    HELP,
    SECURITY,
    COMPENSATION,
    TASKS,
    TERMS,
    SYNC_DETAILS,
}

internal fun SettingsDestination.backDestination(): SettingsDestination? = when (this) {
    SettingsDestination.HUB -> null
    SettingsDestination.COMPENSATION, SettingsDestination.TASKS -> SettingsDestination.PAY
    SettingsDestination.TERMS, SettingsDestination.SYNC_DETAILS -> SettingsDestination.HELP
    else -> SettingsDestination.HUB
}

/** Used to pick slide direction for [AnimatedContent] transitions between settings screens. */
internal fun SettingsDestination.motionOrder(): Int = when (this) {
    SettingsDestination.HUB -> 0
    SettingsDestination.PROFILE -> 1
    SettingsDestination.PAY -> 2
    SettingsDestination.COMPENSATION -> 3
    SettingsDestination.TASKS -> 4
    SettingsDestination.APPEARANCE -> 5
    SettingsDestination.FEATURES -> 6
    SettingsDestination.HELP -> 7
    SettingsDestination.TERMS -> 8
    SettingsDestination.SYNC_DETAILS -> 9
    SettingsDestination.SECURITY -> 10
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    authState: AuthUiState? = null,
    onSignOut: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.HUB) }

    BackHandler(enabled = destination != SettingsDestination.HUB) {
        destination = destination.backDestination() ?: SettingsDestination.HUB
    }

    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureSettingsExist() }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            auroraSubScreenTransition(targetState.motionOrder() > initialState.motionOrder())
        },
        modifier = Modifier.fillMaxSize(),
        label = "settings-destination",
    ) { dest ->
        when (dest) {
            SettingsDestination.COMPENSATION -> {
                CompensationSettingsScreen(onBack = { destination = SettingsDestination.PAY })
            }
            SettingsDestination.TASKS -> {
                com.elmtrackr.app.ui.tasks.TaskManagementScreen(onBack = { destination = SettingsDestination.PAY })
            }
            SettingsDestination.TERMS -> {
                LegalDocumentScreen(
                    title = "Terms of Service",
                    sections = LegalDocuments.termsOfService,
                    lastUpdated = LegalDocuments.LAST_UPDATED,
                    onBack = { destination = SettingsDestination.HELP },
                )
            }
            SettingsDestination.SYNC_DETAILS -> {
                SyncDetailsScreen(onBack = { destination = SettingsDestination.HELP })
            }
            else -> AuroraListScreen {
                AuroraStateCrossfade(
                    targetState = uiState,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { state ->
                        when (state) {
                            is SettingsUiState.Loading -> "loading"
                            is SettingsUiState.Ready -> "ready"
                            is SettingsUiState.Error -> "error"
                        }
                    },
                ) { key ->
                    when (key) {
                        "loading" -> Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.screenH),
                        ) {
                            SettingsPageHeader()
                            SettingsSkeleton()
                        }
                        "ready" -> SettingsFormHost(
                            state = uiState as SettingsUiState.Ready,
                            destination = dest,
                            authState = authState,
                            onNavigate = { destination = it },
                            onNavigateBack = { destination = SettingsDestination.HUB },
                            onSave = viewModel::saveSettings,
                            onSignOut = onSignOut,
                            onTheme = viewModel::saveTheme,
                            onResetPassword = viewModel::resetPassword,
                            onReplayOnboarding = onReplayOnboarding,
                            onDismissSaveFeedback = viewModel::clearSaveFeedback,
                            onDeleteAccount = viewModel::deleteAccount,
                            onDismissAccountFeedback = viewModel::clearAccountActionFeedback,
                            onSyncNow = viewModel::syncNow,
                            onSetAppLockEnabled = viewModel::setAppLockEnabled,
                            onReduceMotionChange = viewModel::setReduceMotion,
                        )
                        else -> ErrorState(
                            message = (uiState as SettingsUiState.Error).message,
                            onRetry = viewModel::ensureSettingsExist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsFormHost(
    state: SettingsUiState.Ready,
    destination: SettingsDestination,
    authState: AuthUiState?,
    onNavigate: (SettingsDestination) -> Unit,
    onNavigateBack: () -> Unit,
    onSave: (String, Double, Double, Double?, String, ClockStyle, CurrencyCode, List<Int>, SettingsFeatureFlags) -> Unit,
    onSignOut: () -> Unit,
    onTheme: (String) -> Unit,
    onResetPassword: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onDismissSaveFeedback: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismissAccountFeedback: () -> Unit,
    onSyncNow: () -> Unit,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val biometricAvailability = remember { BiometricCapability.check(context) }
    var displayName by remember(state.profile?.fullName) { mutableStateOf(state.profile?.fullName ?: "") }
    var dailyOtText by remember(state.settings.dailyOvertimeThresholdMinutes) {
        mutableStateOf(minutesToHours(state.settings.dailyOvertimeThresholdMinutes))
    }
    var weeklyOtText by remember(state.settings.weeklyOvertimeThresholdMinutes) {
        mutableStateOf(minutesToHours(state.settings.weeklyOvertimeThresholdMinutes))
    }
    var hourlyRateText by remember(state.settings.hourlyRate) { mutableStateOf(state.settings.hourlyRate?.toString() ?: "") }
    var timezone by remember(state.settings.timezone) { mutableStateOf(state.settings.timezone) }
    var clockStyle by remember(state.settings.clockStyle) { mutableStateOf(supportedClockStyleOf(state.settings.clockStyle)) }
    var currency by remember(state.settings.currency) { mutableStateOf(state.settings.currency) }
    var travelRefunds by remember(state.settings.featuresTravelRefunds) { mutableStateOf(state.settings.featuresTravelRefunds) }
    var insights by remember(state.settings.featuresInsights) { mutableStateOf(state.settings.featuresInsights) }
    var clockStyles by remember(state.settings.featuresClockStyles) { mutableStateOf(state.settings.featuresClockStyles) }
    var overtimeReminders by remember(state.settings.featuresOvertimeReminders) {
        mutableStateOf(state.settings.featuresOvertimeReminders)
    }
    var weekendDays by remember(state.settings.weekendDays) { mutableStateOf(state.settings.weekendDays) }

    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(state.saveFeedback) {
        val feedback = state.saveFeedback ?: return@LaunchedEffect
        if (!feedback.isError) {
            AuroraHaptics.success(haptic)
        }
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
                overtimeReminders = overtimeReminders,
            ),
        )
    }

    val unsavedCount = when (destination) {
        SettingsDestination.PROFILE -> listOf(
            displayName.trim() != (state.profile?.fullName ?: "").trim(),
        ).count { it }
        SettingsDestination.PAY -> listOf(
            dailyOtText != minutesToHours(state.settings.dailyOvertimeThresholdMinutes),
            weeklyOtText != minutesToHours(state.settings.weeklyOvertimeThresholdMinutes),
            hourlyRateText != (state.settings.hourlyRate?.toString() ?: ""),
            timezone != state.settings.timezone,
            currency != state.settings.currency,
            weekendDays.sorted() != state.settings.weekendDays.sorted(),
        ).count { it }
        SettingsDestination.APPEARANCE -> listOf(
            clockStyle != supportedClockStyleOf(state.settings.clockStyle),
            clockStyles != state.settings.featuresClockStyles,
        ).count { it }
        SettingsDestination.FEATURES -> listOf(
            travelRefunds != state.settings.featuresTravelRefunds,
            insights != state.settings.featuresInsights,
            overtimeReminders != state.settings.featuresOvertimeReminders,
        ).count { it }
        else -> 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (destination) {
            SettingsDestination.HUB -> SettingsHub(
                state = state,
                displayName = displayName,
                hourlyRateText = hourlyRateText,
                currency = currency,
                dailyOtText = dailyOtText,
                weeklyOtText = weeklyOtText,
                weekendDays = weekendDays,
                timezone = timezone,
                clockStyle = clockStyle,
                travelRefunds = travelRefunds,
                insights = insights,
                clockStyles = clockStyles,
                overtimeReminders = overtimeReminders,
                authState = authState,
                onNavigate = onNavigate,
                onSignOut = onSignOut,
            )
            SettingsDestination.PROFILE -> ProfileDetailScreen(
                state = state,
                authState = authState,
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                onBack = onNavigateBack,
                onResetPassword = onResetPassword,
                onDeleteAccount = onDeleteAccount,
            )
            SettingsDestination.PAY -> PayDetailScreen(
                state = state,
                hourlyRateText = hourlyRateText,
                onHourlyRateChange = { hourlyRateText = it },
                currency = currency,
                onCurrencyChange = { currency = it },
                dailyOtText = dailyOtText,
                onDailyOtChange = { dailyOtText = it },
                weeklyOtText = weeklyOtText,
                onWeeklyOtChange = { weeklyOtText = it },
                weekendDays = weekendDays,
                onWeekendDaysChange = { weekendDays = it },
                timezone = timezone,
                onTimezoneChange = { timezone = it },
                onBack = onNavigateBack,
                onOpenCompensation = { onNavigate(SettingsDestination.COMPENSATION) },
                onOpenTasks = { onNavigate(SettingsDestination.TASKS) },
            )
            SettingsDestination.APPEARANCE -> AppearanceDetailScreen(
                state = state,
                clockStyle = clockStyle,
                onClockStyleChange = { clockStyle = it },
                clockStylesEnabled = clockStyles,
                onClockStylesEnabledChange = { clockStyles = it },
                reduceMotionEnabled = state.reduceMotionEnabled,
                onReduceMotionChange = onReduceMotionChange,
                onBack = onNavigateBack,
                onTheme = onTheme,
            )
            SettingsDestination.FEATURES -> FeaturesDetailScreen(
                travelRefunds = travelRefunds,
                onTravelRefundsChange = { travelRefunds = it },
                insights = insights,
                onInsightsChange = { insights = it },
                overtimeReminders = overtimeReminders,
                onOvertimeRemindersChange = { overtimeReminders = it },
                onBack = onNavigateBack,
            )
            SettingsDestination.HELP -> HelpDetailScreen(
                state = state,
                authState = authState,
                onBack = onNavigateBack,
                onReplayOnboarding = onReplayOnboarding,
                onOpenTerms = { onNavigate(SettingsDestination.TERMS) },
                onOpenSyncDetails = { onNavigate(SettingsDestination.SYNC_DETAILS) },
                onSyncNow = onSyncNow,
            )
            SettingsDestination.SECURITY -> SecurityDetailScreen(
                appLockEnabled = state.appLockEnabled,
                biometricAvailability = biometricAvailability,
                onBack = onNavigateBack,
                onAppLockChange = { enabled ->
                    BiometricAuthPrompt.show(
                        activity = activity,
                        title = if (enabled) "Enable app lock" else "Disable app lock",
                        subtitle = if (enabled) {
                            "Confirm to require biometric unlock when opening ElmTrackr"
                        } else {
                            "Confirm to turn off biometric app lock"
                        },
                        onSuccess = { onSetAppLockEnabled(enabled) },
                        onFailure = { },
                    )
                },
            )
            else -> Unit
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (unsavedCount > 0) {
            SettingsFloatingSaveBar(
                unsavedCount = unsavedCount,
                isSaving = state.isSaving,
                onSave = saveAction,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ElmTrackrTheme {
        SettingsFormHost(
            state = SettingsUiState.Ready(
                settings = UserSettings(id = "s1", userId = "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
            ),
            destination = SettingsDestination.HUB,
            authState = AuthUiState.NotConfigured,
            onNavigate = {},
            onNavigateBack = {},
            onSave = { _, _, _, _, _, _, _, _, _ -> },
            onSignOut = {},
            onTheme = {},
            onResetPassword = {},
            onReplayOnboarding = {},
            onDismissSaveFeedback = {},
            onDeleteAccount = {},
            onDismissAccountFeedback = {},
            onSyncNow = {},
            onSetAppLockEnabled = {},
            onReduceMotionChange = {},
        )
    }
}
