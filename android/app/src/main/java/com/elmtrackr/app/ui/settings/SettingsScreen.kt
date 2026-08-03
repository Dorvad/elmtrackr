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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.BuildConfig
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.common.resolve
import com.elmtrackr.app.security.BiometricAuthPrompt
import com.elmtrackr.app.security.BiometricCapability
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.AuroraStateCrossfade
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.design.auroraSubScreenTransition
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import java.time.Instant

internal enum class SettingsDestination {
    HUB,
    PROFILE,
    PAY,
    APPEARANCE,
    CLOCK_FACES,
    FEATURES,
    HELP,
    SECURITY,
    COMPENSATION,
    PREMIUM,
    TASKS,
    TERMS,
    SYNC_DETAILS,
}

internal fun SettingsDestination.backDestination(): SettingsDestination? = when (this) {
    SettingsDestination.HUB -> null
    SettingsDestination.COMPENSATION, SettingsDestination.PREMIUM, SettingsDestination.TASKS -> SettingsDestination.PAY
    SettingsDestination.TERMS, SettingsDestination.SYNC_DETAILS -> SettingsDestination.HELP
    SettingsDestination.CLOCK_FACES -> SettingsDestination.APPEARANCE
    else -> SettingsDestination.HUB
}

/** Used to pick slide direction for [AnimatedContent] transitions between settings screens. */
internal fun SettingsDestination.motionOrder(): Int = when (this) {
    SettingsDestination.HUB -> 0
    SettingsDestination.PROFILE -> 1
    SettingsDestination.PAY -> 2
    SettingsDestination.COMPENSATION -> 3
    SettingsDestination.PREMIUM -> 4
    SettingsDestination.TASKS -> 5
    SettingsDestination.APPEARANCE -> 6
    // Between appearance and features so the gallery slides in from the side the
    // user came from, like every other second-level screen.
    SettingsDestination.CLOCK_FACES -> 7
    SettingsDestination.FEATURES -> 8
    SettingsDestination.HELP -> 9
    SettingsDestination.TERMS -> 10
    SettingsDestination.SYNC_DETAILS -> 11
    SettingsDestination.SECURITY -> 12
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    authState: AuthUiState? = null,
    onSignOut: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    pendingLaunch: SettingsLaunchRequest? = null,
    onPendingLaunchConsumed: () -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.HUB) }
    val motionEnabled = auroraMotionEnabled()
    var unsavedCount by remember { mutableStateOf(0) }
    var pendingDiscardTarget by remember { mutableStateOf<SettingsDestination?>(null) }

    // Leaving a detail screen with unsaved edits requires an explicit choice.
    fun navigateGuarded(target: SettingsDestination) {
        if (unsavedCount > 0 && target != destination) {
            pendingDiscardTarget = target
        } else {
            destination = target
        }
    }

    LaunchedEffect(pendingLaunch) {
        val launch = pendingLaunch ?: return@LaunchedEffect
        destination = launch.toDestination()
        onPendingLaunchConsumed()
    }

    BackHandler(enabled = destination != SettingsDestination.HUB) {
        navigateGuarded(destination.backDestination() ?: SettingsDestination.HUB)
    }

    pendingDiscardTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDiscardTarget = null },
            title = { Text(stringResource(R.string.settings_discard_title)) },
            text = { Text(stringResource(R.string.settings_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscardTarget = null
                    unsavedCount = 0
                    destination = target
                }) { Text(stringResource(R.string.settings_discard_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscardTarget = null }) {
                    Text(stringResource(R.string.settings_discard_keep))
                }
            },
        )
    }

    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureSettingsExist() }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            auroraSubScreenTransition(targetState.motionOrder() > initialState.motionOrder(), motionEnabled)
        },
        modifier = Modifier.fillMaxSize(),
        label = "settings-destination",
    ) { dest ->
        when (dest) {
            SettingsDestination.COMPENSATION -> {
                CompensationSettingsScreen(onBack = { destination = SettingsDestination.PAY })
            }
            SettingsDestination.PREMIUM -> {
                PremiumProfilesScreen(onBack = { destination = SettingsDestination.PAY })
            }
            SettingsDestination.TASKS -> {
                com.elmtrackr.app.ui.tasks.TaskManagementScreen(onBack = { destination = SettingsDestination.PAY })
            }
            SettingsDestination.TERMS -> {
                LegalDocumentScreen(
                    title = stringResource(R.string.settings_terms_of_service),
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
                ) { state ->
                    when (state) {
                        is SettingsUiState.Loading -> Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.screenH),
                        ) {
                            SettingsPageHeader()
                            SettingsSkeleton()
                        }
                        is SettingsUiState.Ready -> SettingsFormHost(
                            state = state,
                            destination = dest,
                            authState = authState,
                            onNavigate = { navigateGuarded(it) },
                            onNavigateBack = { navigateGuarded(SettingsDestination.HUB) },
                            onUnsavedCountChange = { unsavedCount = it },
                            onClearPasswordResetFeedback = viewModel::clearPasswordResetFeedback,
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
                        is SettingsUiState.Error -> ErrorState(
                            message = state.message,
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
    onUnsavedCountChange: (Int) -> Unit = {},
    onClearPasswordResetFeedback: () -> Unit = {},
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
    // BiometricAuthPrompt.show takes plain Strings and is called from a
    // non-composable lambda, so these have to be resolved up here.
    val lockEnableTitle = stringResource(R.string.security_prompt_enable_title)
    val lockDisableTitle = stringResource(R.string.security_prompt_disable_title)
    val lockEnableSubtitle = stringResource(R.string.security_prompt_enable_subtitle)
    val lockDisableSubtitle = stringResource(R.string.security_prompt_disable_subtitle)
    var displayName by rememberSaveable { mutableStateOf(state.profile?.fullName ?: "") }
    // rememberSaveable without settings keys: each destination gets its own
    // composition, so fields initialize on entry; keying on settings values
    // let a background sync emission wipe text the user was typing, while
    // plain remember lost unsaved edits on rotation.
    var dailyOtText by rememberSaveable {
        mutableStateOf(minutesToHours(state.settings.dailyOvertimeThresholdMinutes))
    }
    var weeklyOtText by rememberSaveable {
        mutableStateOf(minutesToHours(state.settings.weeklyOvertimeThresholdMinutes))
    }
    var hourlyRateText by rememberSaveable { mutableStateOf(state.settings.hourlyRate?.toString() ?: "") }
    var timezone by rememberSaveable { mutableStateOf(state.settings.timezone) }
    var clockStyle by rememberSaveable { mutableStateOf(supportedClockStyleOf(state.settings.clockStyle)) }
    var currency by rememberSaveable { mutableStateOf(state.settings.currency) }
    var travelRefunds by rememberSaveable { mutableStateOf(state.settings.featuresTravelRefunds) }
    var paidProjects by rememberSaveable { mutableStateOf(state.settings.featuresPaidProjects) }
    var insights by rememberSaveable { mutableStateOf(state.settings.featuresInsights) }
    var clockStyles by rememberSaveable { mutableStateOf(state.settings.featuresClockStyles) }
    var overtimeReminders by rememberSaveable {
        mutableStateOf(state.settings.featuresOvertimeReminders)
    }
    var weekendDays by rememberSaveable { mutableStateOf(state.settings.weekendDays) }

    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(state.saveFeedback) {
        val feedback = state.saveFeedback ?: return@LaunchedEffect
        if (!feedback.isError) {
            AuroraHaptics.success(haptic)
        }
        snackbarHostState.showSnackbar(
            message = feedback.message.resolve(context),
            duration = if (feedback.isError) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        onDismissSaveFeedback()
    }
    LaunchedEffect(state.passwordResetFeedback) {
        state.passwordResetFeedback ?: return@LaunchedEffect
        // Let the confirmation read, then restore the hint line instead of
        // leaving stale "email sent" text for the rest of the session.
        kotlinx.coroutines.delay(6_000)
        onClearPasswordResetFeedback()
    }
    LaunchedEffect(state.accountActionFeedback) {
        val feedback = state.accountActionFeedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = feedback.resolve(context), duration = SnackbarDuration.Long)
        onDismissAccountFeedback()
        val signsOut = feedback == UiText.Res(R.string.settings_feedback_account_deleted) ||
            feedback == UiText.Res(R.string.settings_feedback_local_cleared)
        if (signsOut) {
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
                paidProjects = paidProjects,
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
            paidProjects != state.settings.featuresPaidProjects,
            insights != state.settings.featuresInsights,
            overtimeReminders != state.settings.featuresOvertimeReminders,
        ).count { it }
        else -> 0
    }
    LaunchedEffect(unsavedCount) { onUnsavedCountChange(unsavedCount) }

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
                paidProjects = paidProjects,
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
                onOpenPremiumProfiles = { onNavigate(SettingsDestination.PREMIUM) },
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
                onBrowseAllFaces = { onNavigate(SettingsDestination.CLOCK_FACES) },
            )
            SettingsDestination.CLOCK_FACES -> ClockFaceGalleryScreen(
                selected = clockStyle,
                onSelect = { clockStyle = it },
                onBack = onNavigateBack,
            )
            SettingsDestination.FEATURES -> {
                val reminderRulesViewModel: ReminderRulesViewModel = hiltViewModel()
                val reminderRules by reminderRulesViewModel.rules.collectAsState()
                FeaturesDetailScreen(
                    travelRefunds = travelRefunds,
                    onTravelRefundsChange = { travelRefunds = it },
                    paidProjects = paidProjects,
                    onPaidProjectsChange = { paidProjects = it },
                    insights = insights,
                    onInsightsChange = { insights = it },
                    overtimeReminders = overtimeReminders,
                    onOvertimeRemindersChange = { overtimeReminders = it },
                    reminderRules = reminderRules,
                    onAddReminderRule = reminderRulesViewModel::addRule,
                    onUpdateReminderRule = reminderRulesViewModel::updateRule,
                    onRemoveReminderRule = reminderRulesViewModel::removeRule,
                    onBack = onNavigateBack,
                )
            }
            SettingsDestination.HELP -> HelpDetailScreen(
                state = state,
                authState = authState,
                onBack = onNavigateBack,
                onReplayOnboarding = onReplayOnboarding,
                onOpenTerms = { onNavigate(SettingsDestination.TERMS) },
                onOpenSyncDetails = { onNavigate(SettingsDestination.SYNC_DETAILS) },
                onSyncNow = onSyncNow,
                // Developer-only eligibility inspector; read-only against
                // review-prompt state, absent from release builds.
                reviewPromptDebugContent = if (BuildConfig.DEBUG) {
                    { ReviewPromptDebugCard() }
                } else {
                    null
                },
            )
            SettingsDestination.SECURITY -> SecurityDetailScreen(
                appLockEnabled = state.appLockEnabled,
                biometricAvailability = biometricAvailability,
                onBack = onNavigateBack,
                onAppLockChange = { enabled ->
                    BiometricAuthPrompt.show(
                        activity = activity,
                        title = if (enabled) lockEnableTitle else lockDisableTitle,
                        subtitle = if (enabled) lockEnableSubtitle else lockDisableSubtitle,
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
