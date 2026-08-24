package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.common.durationText
import com.elmtrackr.app.notification.NotificationPermissionCoordinator
import com.elmtrackr.app.ui.common.AppTimePickerDialog
import com.elmtrackr.app.ui.common.LocalWorkZone
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.dashboard.DashboardPromo
import com.elmtrackr.app.domain.dashboard.DashboardPromoInputs
import com.elmtrackr.app.domain.dashboard.activeDashboardPromo
import androidx.compose.foundation.clickable
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.design.ElmDistributionBar
import com.elmtrackr.app.ui.design.ElmDistributionLegend
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.domain.HoursFormatter
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.setup.SetupStep
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.ui.settings.SettingsLaunchRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elmtrackr.app.ui.components.motion.HOUR_MILLIS
import com.elmtrackr.app.ui.components.motion.MINUTE_MILLIS
import com.elmtrackr.app.ui.components.motion.rememberCurrentInstant
import com.elmtrackr.app.ui.components.motion.rememberElapsedUnits
import com.elmtrackr.app.ui.components.motion.ShiftElapsedDisplay
import com.elmtrackr.app.ui.components.motion.activeShiftPulse
import com.elmtrackr.app.ui.components.motion.FirstClockInCelebrationDialog
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.AuroraScreen
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.layout.isTabletLayout
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmCardPadded
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.ElmStatCard
import com.elmtrackr.app.ui.design.ElmStatVariant
import com.elmtrackr.app.ui.design.AuroraStateCrossfade
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraSubScreenTransition
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.auroraSecondaryText
import com.elmtrackr.app.ui.theme.auroraWeekendBackground
import com.elmtrackr.app.ui.theme.auroraWeekendInk
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraWhite
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateFormatter  = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val timeFormatter  = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val headerGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.5f to AuroraPlum, 1f to AuroraAqua),
)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToReports: () -> Unit = {},
    // Separate from onNavigateToReports because the two land in different places:
    // the month summary opens the hours report, the refund reminder opens the tab
    // holding the claims it is counting.
    onReviewRefunds: () -> Unit = onNavigateToReports,
    onNavigateToSettings: (SettingsLaunchRequest) -> Unit = {},
    onNavigateToProjects: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val showCelebration by viewModel.showFirstClockInCelebration.collectAsState()
    val projectShiftSummary by viewModel.projectShiftSummary.collectAsState()
    val setupChecklist by viewModel.setupChecklist.collectAsState()
    // Plain remember: restoring the overlay after a tab switch stranded users
    // inside task management when they came back to the dashboard.
    var showTasks by remember { mutableStateOf(false) }
    val motionEnabled = auroraMotionEnabled()

    BackHandler(enabled = showTasks) { showTasks = false }

    // A freshly pinned widget only becomes visible to AppWidgetManager once the
    // launcher confirms it, so re-check whenever the dashboard comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshWidgetPinState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AnimatedContent(
        targetState = showTasks,
        transitionSpec = {
            auroraSubScreenTransition(targetState, motionEnabled)
        },
        modifier = Modifier.fillMaxSize(),
        label = "dashboard-tasks",
    ) { tasksOpen ->
        if (tasksOpen) {
            com.elmtrackr.app.ui.tasks.TaskManagementScreen(onBack = { showTasks = false })
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
            ) {
                AuroraStateCrossfade(
                    targetState = uiState,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { state ->
                        when (state) {
                            is DashboardUiState.Loading -> "loading"
                            is DashboardUiState.Ready -> "ready"
                            is DashboardUiState.Error -> "error"
                        }
                    },
                ) { state ->
                    when (state) {
                        is DashboardUiState.Loading -> DashboardSkeleton()
                        is DashboardUiState.Ready -> DashboardReady(
                            state = state,
                            onClockIn = viewModel::clockIn,
                            onClockOut = viewModel::clockOut,
                            onEditStartTime = viewModel::editActiveShiftStartTime,
                            onNavigateToReports = onNavigateToReports,
                            onReviewRefunds = onReviewRefunds,
                            onSelectTask = viewModel::selectTask,
                            onSelectWorkProfile = viewModel::selectWorkProfile,
                            onManageTasks = { showTasks = true },
                            onSelectCompensationSource = viewModel::selectCompensationSource,
                            onSelectClockInProject = viewModel::selectClockInProject,
                            onProjectNoteChange = viewModel::setProjectClockInNote,
                            projectShiftSummary = projectShiftSummary,
                            onDismissProjectShiftSummary = viewModel::dismissProjectShiftSummary,
                            onViewProject = {
                                viewModel.dismissProjectShiftSummary()
                                onNavigateToProjects()
                            },
                            showFirstClockInCelebration = showCelebration,
                            onDismissFirstClockInCelebration = viewModel::dismissFirstClockInCelebration,
                            onEnablePaidProjects = viewModel::enablePaidProjectsFromWizard,
                            onDismissPaidProjectsWizard = viewModel::dismissPaidProjectsWizard,
                            onDismissRefundReminder = viewModel::dismissRefundReminder,
                            setupChecklist = setupChecklist,
                            onSetupNavigate = { step ->
                                viewModel.markSetupStepVisited(step)
                                onNavigateToSettings(
                                    when (step) {
                                        SetupStep.CLOCK_STYLE -> SettingsLaunchRequest.APPEARANCE
                                        SetupStep.COMPENSATION -> SettingsLaunchRequest.COMPENSATION
                                        SetupStep.PROFILE_NAME -> SettingsLaunchRequest.PROFILE
                                        SetupStep.FEATURES -> SettingsLaunchRequest.FEATURES
                                        SetupStep.APP_LOCK -> SettingsLaunchRequest.SECURITY
                                        else -> SettingsLaunchRequest.PREMIUM
                                    },
                                )
                            },
                            onRequestPinWidget = viewModel::requestPinWidget,
                            onDismissSetupChecklist = viewModel::dismissSetupChecklist,
                            onSetupChecklistCelebrated = viewModel::markSetupChecklistCelebrated,
                        )
                        is DashboardUiState.Error -> ErrorState(
                            message = state.message,
                            onRetry = viewModel::retry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardReady(
    state: DashboardUiState.Ready,
    onClockIn: () -> Unit,
    onClockOut: (String) -> Unit,
    onEditStartTime: (shiftId: String, newStartTime: Instant) -> Unit,
    onNavigateToReports: () -> Unit,
    onReviewRefunds: () -> Unit = onNavigateToReports,
    onSelectTask: (String) -> Unit,
    onSelectWorkProfile: (String) -> Unit,
    onManageTasks: () -> Unit,
    onSelectCompensationSource: (CompensationSource) -> Unit = {},
    onSelectClockInProject: (String) -> Unit = {},
    onProjectNoteChange: (String) -> Unit = {},
    projectShiftSummary: DashboardViewModel.ProjectShiftSummary? = null,
    onDismissProjectShiftSummary: () -> Unit = {},
    onViewProject: () -> Unit = {},
    showFirstClockInCelebration: Boolean,
    onDismissFirstClockInCelebration: () -> Unit,
    onEnablePaidProjects: () -> Unit = {},
    onDismissPaidProjectsWizard: () -> Unit = {},
    onDismissRefundReminder: () -> Unit = {},
    setupChecklist: SetupChecklistUiState? = null,
    onSetupNavigate: (SetupStep) -> Unit = {},
    onRequestPinWidget: () -> Unit = {},
    onDismissSetupChecklist: () -> Unit = {},
    onSetupChecklistCelebrated: () -> Unit = {},
) {
    val activeShift = state.activeShift
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    // Shift dates/times below must render in the work zone, like Reports and the shift
    // list. Provided once here so nested cards cannot fall back to the device zone.
    val workZone = state.settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.systemDefault()
    val scope = rememberCoroutineScope()
    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }
    var pendingClockIn by rememberSaveable { mutableStateOf(false) }

    fun performClockIn() {
        AuroraHaptics.clockIn(haptic)
        onClockIn()
    }

    fun requestNotificationsThenClockIn() {
        val host = activity as? MainActivity
        if (host == null || NotificationPermissionCoordinator.hasPermission(host)) {
            performClockIn()
            return
        }
        scope.launch {
            if (NotificationPermissionCoordinator.shouldShowEducationalPrompt(host)) {
                showNotificationRationale = true
                pendingClockIn = true
            } else {
                host.requestNotificationPermission { performClockIn() }
            }
        }
    }

    val handleClockIn = { requestNotificationsThenClockIn() }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }

    if (showNotificationRationale && activity is MainActivity) {
        AlertDialog(
            onDismissRequest = {
                showNotificationRationale = false
                pendingClockIn = false
            },
            title = { Text(stringResource(R.string.dashboard_notif_rationale_title)) },
            text = {
                Text(
                    stringResource(R.string.dashboard_notif_rationale_text),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        scope.launch { NotificationPermissionCoordinator.markPromptShown(activity) }
                        activity.requestNotificationPermission {
                            if (pendingClockIn) performClockIn()
                            pendingClockIn = false
                        }
                    },
                ) { Text(stringResource(R.string.dashboard_notif_rationale_continue)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        if (pendingClockIn) performClockIn()
                        pendingClockIn = false
                    },
                ) { Text(stringResource(R.string.dashboard_notif_rationale_not_now)) }
            },
        )
    }

    val handleClockOut: () -> Unit = {
        val shift = activeShift
        if (shift != null) {
            AuroraHaptics.clockOut(haptic)
            onClockOut(shift.id)
        }
    }

    if (showEditDialog && activeShift != null) {
        EditStartTimeDialog(
            currentStartTime = activeShift.startTime,
            zone = state.settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.systemDefault(),
            onConfirm = { newTime ->
                onEditStartTime(activeShift.id, newTime)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showFirstClockInCelebration) {
        FirstClockInCelebrationDialog(onDismiss = onDismissFirstClockInCelebration)
    }

    // The one-time v1.2 announcement for existing users pops over the main
    // screen — the surface every user reaches — rather than hiding in Settings.
    if (state.showPaidProjectsWizard) {
        PaidProjectsUpdateWizard(
            onEnable = onEnablePaidProjects,
            onDismiss = onDismissPaidProjectsWizard,
        )
    }

    // "Clock faces" is a feature toggle. When it is off, the home-screen clock
    // falls back to the default face regardless of the saved style; the saved
    // choice is preserved and reapplied when the feature is turned back on.
    val clockStyle = if (state.settings?.featuresClockStyles == false) {
        SupportedClockStyle.CLASSIC
    } else {
        state.settings?.clockStyle?.toSupportedOrDefault() ?: SupportedClockStyle.CLASSIC
    }

    val isTablet = isTabletLayout()

    CompositionLocalProvider(LocalWorkZone provides workZone) {
    AuroraScreen {
            DashboardHeader(displayName = state.displayName)

            // One nudge at a time. These four used to be gated independently,
            // so in the worst case all of them stacked above the month summary
            // in identical cards, competing with the clock. The ranking lives in
            // DashboardPromoSlot with its own tests.
            // The work zone, read once. Two separate device-zone reads on a screen
            // that already provides LocalWorkZone could straddle midnight between
            // them, and could disagree with the refund count beside it.
            val today = LocalDate.now(workZone)
            val promo = activeDashboardPromo(
                DashboardPromoInputs(
                    unresolvedRefundCount = state.unresolvedRefundCount,
                    isRefundWindow = today.dayOfMonth >= today.lengthOfMonth() - 4,
                    // Persisted per month by the ViewModel, not held here: as
                    // rememberSaveable this returned on the next launch, so
                    // "not now" lasted until the user left the screen.
                    refundReminderDismissed = state.refundReminderDismissed,
                    hasNoShifts = state.recentShifts.isEmpty() && activeShift == null,
                    setupChecklistAvailable = setupChecklist != null,
                    projectSummaryAvailable = projectShiftSummary != null,
                    updateWizardVisible = state.showPaidProjectsWizard,
                ),
            )

            if (promo == DashboardPromo.FIRST_RUN_WELCOME) {
                FirstRunWelcomeCard(onClockIn = handleClockIn)
            }

            if (promo == DashboardPromo.REFUND_REMINDER) {
                RefundReminderBanner(
                    count = state.unresolvedRefundCount,
                    onDismiss = onDismissRefundReminder,
                    onReviewRefunds = onReviewRefunds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraEnter(index = 1),
                )
            }

            val dailyOtMinutes = state.settings?.dailyOvertimeThresholdMinutes ?: (8 * 60)
            val currencyCode = state.paySummary?.currencyCode
                ?: state.settings?.displayCurrencyCode()
                ?: "ILS"

            // Hourly tasks and paid projects are separate features. The mode
            // toggle decides which picker is on screen: task chips only while
            // "Hourly work" is selected, project chips only while "Project time"
            // is — never both at once.
            val projectModeSelected = state.canSelectProjectTime &&
                state.selectedCompensationSource.isProjectTime

            if (activeShift == null && state.canSelectProjectTime) {
                ProjectTimeSelector(
                    options = state.projectOptions,
                    selectedSource = state.selectedCompensationSource,
                    selectedProjectId = state.selectedProjectId,
                    note = state.projectClockInNote,
                    onSelectSource = onSelectCompensationSource,
                    onSelectProject = onSelectClockInProject,
                    onNoteChange = onProjectNoteChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .auroraEnter(index = 1),
                )
            }

            // Which job, then what at that job — one narrowing question, so the
            // profile bar sits directly above the task bar. Renders nothing while
            // there is only one profile.
            if (activeShift == null && !projectModeSelected) {
                WorkProfileSelectorBar(
                    profiles = state.profiles,
                    selectedProfileId = state.selectedWorkProfileId,
                    onSelectProfile = onSelectWorkProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .auroraEnter(index = 1),
                )
            }

            if (activeShift == null && state.activeTasks.isNotEmpty() && !projectModeSelected) {
                com.elmtrackr.app.ui.tasks.TaskSelectorBar(
                    tasks = state.activeTasks,
                    selectedTaskId = state.selectedTaskId,
                    suggestedTaskId = state.suggestedTaskId,
                    showSuggestedNow = state.showSuggestedNow,
                    suggestionExplanation = state.suggestionExplanation,
                    onSelectTask = onSelectTask,
                    onManageTasks = onManageTasks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .auroraEnter(index = 1),
                )
            }

            // Non-blocking: sits above the clock and is dismissed with a tap, so
            // it never stands between the user and their next punch.
            projectShiftSummary?.takeIf { promo == DashboardPromo.PROJECT_SUMMARY }?.let { summary ->
                ProjectShiftSummaryCard(
                    summary = summary,
                    onDismiss = onDismissProjectShiftSummary,
                    onViewProject = onViewProject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .auroraEnter(index = 1),
                )
            }

            if (activeShift?.isProjectTime == true) {
                val activeProject = state.activeProject
                if (activeProject != null) {
                    ActiveProjectShiftCard(
                        project = activeProject,
                        activeShift = activeShift,
                        projectShifts = state.activeProjectShifts,
                        // Switched off mid-shift: the shift is kept as project
                        // time and can still be clocked out.
                        featureDisabled = state.settings?.featuresPaidProjects != true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .auroraEnter(index = 1),
                    )
                } else {
                    // The project row could not be read — deleted, or the feature
                    // was switched off. Either way the shift stays project time.
                    DisabledProjectShiftNotice(
                        projectName = activeShift.projectNameSnapshot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .auroraEnter(index = 1),
                    )
                }
            }

            if (activeShift != null && !activeShift.taskNameSnapshot.isNullOrBlank()) {
                val taskColor = state.activeTasks.firstOrNull { it.id == activeShift.taskId }?.color
                com.elmtrackr.app.ui.tasks.ActiveShiftTaskBadge(
                    icon = activeShift.taskIconSnapshot,
                    name = activeShift.taskNameSnapshot,
                    rate = activeShift.taskHourlyRateSnapshot,
                    colorHex = taskColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .auroraEnter(index = 1),
                )
            }

            val clockCard = @Composable {
                DashboardClockSection(
                    clockStyle = clockStyle,
                    activeShift = activeShift,
                    dailyOtMinutes = dailyOtMinutes,
                    todayBaseMinutes = state.todayCompletedMinutes,
                    activeStartedToday = run {
                        val workZone = state.settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.systemDefault()
                        // A running project shift is excluded for the same reason
                        // as completed ones: its hours must not drive the ring past
                        // the overtime threshold.
                        activeShift?.isEmployeePaid == true &&
                            activeShift.startTime.atZone(workZone).toLocalDate() == LocalDate.now(workZone)
                    },
                    onClockIn = handleClockIn,
                    onClockOut = handleClockOut,
                    onEditStartTime = { showEditDialog = true },
                )
            }

            // The paid-projects section sits directly under the clock, as in the
            // Aurora reference. Added alongside the existing hourly content,
            // never replacing it, and only when Paid Projects is on.
            val projectCard: (@Composable () -> Unit)? = state.projectSummary?.let { projectSummary ->
                @Composable {
                    ProjectDashboardCard(
                        summary = projectSummary,
                        onOpenProjects = onViewProject,
                        modifier = Modifier
                            .fillMaxWidth()
                            .auroraEnter(index = 2),
                    )
                }
            }

            val setupCard: (@Composable () -> Unit)? = setupChecklist
                ?.takeIf { promo == DashboardPromo.SETUP_CHECKLIST }
                ?.let { checklist ->
                @Composable {
                    SetupChecklistCard(
                        state = checklist,
                        onStepAction = { step ->
                            when (step) {
                                SetupStep.FIRST_SHIFT -> handleClockIn()
                                SetupStep.CLOCK_STYLE,
                                SetupStep.COMPENSATION,
                                SetupStep.PROFILE_NAME,
                                SetupStep.FEATURES,
                                SetupStep.APP_LOCK,
                                SetupStep.PREMIUM -> onSetupNavigate(step)
                                SetupStep.TASKS -> onManageTasks()
                                SetupStep.WIDGET -> onRequestPinWidget()
                            }
                        },
                        onDismiss = onDismissSetupChecklist,
                        onCelebrationDismissed = onSetupChecklistCelebrated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .auroraEnter(index = 2),
                    )
                }
            }

            if (isTablet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(0.42f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        clockCard()
                        projectCard?.invoke()
                        setupCard?.invoke()
                    }
                    Column(
                        modifier = Modifier.weight(0.58f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MonthSummaryCard(
                            report = state.monthlyReport,
                            paySummary = state.paySummary,
                            currencyCode = currencyCode,
                            onOpenReport = onNavigateToReports,
                            modifier = Modifier.auroraEnter(index = 2),
                        )
                        RecentShiftsSection(
                            recentShifts = state.recentShifts,
                            modifier = Modifier.auroraEnter(index = 4),
                        )
                    }
                }
            } else {
                clockCard()

                projectCard?.invoke()

                setupCard?.invoke()

                MonthSummaryCard(
                    report       = state.monthlyReport,
                    paySummary   = state.paySummary,
                    currencyCode = currencyCode,
                    onOpenReport = onNavigateToReports,
                    modifier     = Modifier.auroraEnter(index = 2),
                )

                RecentShiftsSection(
                    recentShifts = state.recentShifts,
                    modifier = Modifier.auroraEnter(index = 3),
                )
            }
    }
    }
}

// â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun DashboardHeader(
    displayName: String?,
) {
    // Ticked hourly so the greeting follows the day. A direct read left a
    // dashboard opened before noon saying "Good morning" until something else
    // happened to recompose it.
    val hour = rememberCurrentInstant(HOUR_MILLIS).atZone(LocalWorkZone.current).hour
    val greetingBase = stringResource(
        when (hour) {
            in 0..11 -> R.string.dashboard_greeting_morning
            in 12..17 -> R.string.dashboard_greeting_afternoon
            else     -> R.string.dashboard_greeting_evening
        },
    )
    val firstName = displayName?.trim()?.split(" ")?.firstOrNull()
    val greeting  = if (firstName != null)
        stringResource(R.string.dashboard_greeting_with_name, greetingBase.uppercase(), firstName.uppercase())
    else
        greetingBase.uppercase()

    Row(
        modifier              = Modifier.fillMaxWidth().auroraEnter(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = greeting,
                style      = MaterialTheme.typography.labelSmall,
                color      = auroraSecondaryText(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(7.dp),
                            ambientColor = AuroraIndigo.copy(alpha = 0.15f),
                            spotColor = AuroraIndigo.copy(alpha = 0.7f),
                        )
                        .background(headerGradient, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint               = AuroraWhite,
                        modifier           = Modifier.size(14.dp),
                    )
                }
                Text(
                    text       = stringResource(R.string.dashboard_wordmark),
                    style      = MaterialTheme.typography.headlineMedium,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RefundReminderBanner(
    count: Int,
    onDismiss: () -> Unit,
    onReviewRefunds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElmCard(
        modifier = modifier,
        cornerRadius = CornerRadius.Large,
        containerColor = auroraWeekendBackground(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (count == 1) R.string.dashboard_refund_pending_one else R.string.dashboard_refund_pending_other,
                        count,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = auroraWeekendInk(),
                )
                Text(
                    stringResource(R.string.dashboard_refund_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = auroraSecondaryText(),
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextButton(
                    onClick = onReviewRefunds,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.dashboard_refund_banner_review),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = auroraWeekendInk(),
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.dashboard_refund_banner_dismiss), tint = AuroraPlum, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FirstRunWelcomeCard(onClockIn: () -> Unit) {
    ElmCard(
        modifier = Modifier.fillMaxWidth().auroraEnter(index = 1),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dashboard_welcome_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.dashboard_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            ElmGradientButton(
                onClick = onClockIn,
                accessibilityLabel = stringResource(R.string.dashboard_clock_in_accessibility),
            ) {
                Text(stringResource(R.string.dashboard_welcome_button), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Classic clock card ────────────────────────────────────────────────────────

@Composable
private fun DashboardClockSection(
    clockStyle: SupportedClockStyle,
    activeShift: Shift?,
    dailyOtMinutes: Int,
    todayBaseMinutes: Int = 0,
    activeStartedToday: Boolean = true,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val elapsedSeconds = rememberElapsedUnits(activeShift?.startTime)

    Box(modifier = Modifier.fillMaxWidth().auroraEnter(index = 1)) {
        AnimatedContent(
            targetState = clockStyle,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(350), initialScale = .96f)) togetherWith
                    (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = .98f))
            },
            label = "watch-face-change",
        ) { renderStyle ->
            when (renderStyle) {
                SupportedClockStyle.CLASSIC -> ClassicClockCard(
                    activeShift = activeShift,
                    elapsedSeconds = elapsedSeconds,
                    dailyOtMinutes = dailyOtMinutes,
                    todayBaseMinutes = todayBaseMinutes,
                    activeStartedToday = activeStartedToday,
                    onClockIn = onClockIn,
                    onClockOut = onClockOut,
                    onEditStartTime = onEditStartTime,
                )
                SupportedClockStyle.MINIMAL -> MinimalClockCard(
                    activeShift = activeShift,
                    elapsedSeconds = elapsedSeconds,
                    onClockIn = onClockIn,
                    onClockOut = onClockOut,
                    onEditStartTime = onEditStartTime,
                )
                SupportedClockStyle.AURORA -> AuroraClockCard(
                    activeShift = activeShift,
                    elapsedSeconds = elapsedSeconds,
                    onClockIn = onClockIn,
                    onClockOut = onClockOut,
                    onEditStartTime = onEditStartTime,
                )
                SupportedClockStyle.FOCUS,
                SupportedClockStyle.BOLD,
                SupportedClockStyle.NIGHT,
                SupportedClockStyle.RETRO,
                SupportedClockStyle.PULSE,
                SupportedClockStyle.DIAL,
                SupportedClockStyle.STRAND,
                SupportedClockStyle.PRISM,
                SupportedClockStyle.SAND,
                SupportedClockStyle.BLOCKS,
                SupportedClockStyle.ORBIT,
                SupportedClockStyle.TIDE,
                SupportedClockStyle.SPROUT,
                SupportedClockStyle.METRO,
                SupportedClockStyle.VINYL,
                SupportedClockStyle.LUNA,
                SupportedClockStyle.SUMMIT -> ExpressiveClockCard(
                    style = renderStyle,
                    activeShift = activeShift,
                    elapsedSeconds = elapsedSeconds,
                    dailyOtMinutes = dailyOtMinutes,
                    todayBaseMinutes = todayBaseMinutes,
                    activeStartedToday = activeStartedToday,
                    onClockIn = onClockIn,
                    onClockOut = onClockOut,
                    onEditStartTime = onEditStartTime,
                )
            }
        }
    }
}

@Composable
private fun ClassicClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    dailyOtMinutes: Int,
    todayBaseMinutes: Int = 0,
    activeStartedToday: Boolean = true,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val elapsedMinutes = elapsedSeconds / 60f
    // Whole-day progress (completed shifts today + current shift), matching the
    // widget and Wear surfaces; an overnight shift started yesterday counts 0.
    val dayMinutes = todayBaseMinutes + if (activeStartedToday) elapsedMinutes else 0f
    val progress = if (dailyOtMinutes > 0) (dayMinutes / dailyOtMinutes).coerceIn(0f, 1f) else 0f
    val isOvertime = activeShift != null && dayMinutes > dailyOtMinutes
    val clockOutContentDescription = stringResource(R.string.dashboard_clock_out_accessibility)

    val progressColor = if (isOvertime) AuroraPeach else AuroraIndigo
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    ElmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (activeShift != null) {
                // Progress ring with elapsed time in the centre
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp)) {
                    Canvas(modifier = Modifier.size(148.dp)) {
                        val strokeWidth = 10.dp.toPx()
                        val inset = strokeWidth / 2f
                        val diameter = size.minDimension - strokeWidth
                        // track
                        drawArc(
                            color      = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter  = false,
                            style      = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft    = Offset(inset, inset),
                            size       = Size(diameter, diameter),
                        )
                        // progress arc
                        if (progress > 0f) {
                            drawArc(
                                color      = progressColor,
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter  = false,
                                style      = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                topLeft    = Offset(inset, inset),
                                size       = Size(diameter, diameter),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShiftElapsedDisplay(
                            elapsedSeconds = elapsedSeconds,
                            running = true,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isOvertime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        if (isOvertime) {
                            Text(
                                text  = stringResource(R.string.dashboard_overtime_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraPeach,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = stringResource(R.string.dashboard_since_time, formatInstantTime(activeShift.startTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onEditStartTime, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector     = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.dashboard_edit_start_time),
                            modifier        = Modifier.size(18.dp),
                            tint            = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockOut,
                    shape   = RoundedCornerShape(CornerRadius.Medium),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .activeShiftPulse(true)
                        .semantics { contentDescription = clockOutContentDescription },
                ) {
                    Text(stringResource(R.string.dashboard_clock_out), fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = stringResource(R.string.dashboard_ready_to_clock_in),
                    style      = MaterialTheme.typography.titleMedium,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                ElmGradientButton(
                    onClick = onClockIn,
                    accessibilityLabel = stringResource(R.string.dashboard_clock_in_accessibility),
                ) {
                    Text(stringResource(R.string.dashboard_clock_in), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// â”€â”€ Minimal clock card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun MinimalClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val clockInContentDescription = stringResource(R.string.dashboard_clock_in_accessibility)
    val clockOutContentDescription = stringResource(R.string.dashboard_clock_out_accessibility)
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShiftElapsedDisplay(
            elapsedSeconds = elapsedSeconds,
            running = activeShift != null,
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Thin),
            color = if (activeShift != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (activeShift != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = formatInstantTime(activeShift.startTime),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onEditStartTime, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector        = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.dashboard_edit_start_time),
                        modifier           = Modifier.size(18.dp),
                        tint               = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = if (activeShift != null) onClockOut else onClockIn,
            shape    = CircleShape,
            colors   = if (activeShift != null)
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            else
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .size(120.dp)
                .activeShiftPulse(activeShift != null)
                .semantics {
                    contentDescription = if (activeShift != null) {
                        clockOutContentDescription
                    } else {
                        clockInContentDescription
                    }
                },
        ) {
            Text(
                text       = stringResource(if (activeShift != null) R.string.dashboard_clock_out_short else R.string.dashboard_clock_in_short),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// â”€â”€ Aurora clock card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun AuroraClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val clockInContentDescription = stringResource(R.string.dashboard_clock_in_accessibility)
    val clockOutContentDescription = stringResource(R.string.dashboard_clock_out_accessibility)
    val brush = Brush.linearGradient(
        colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = brush, shape = RoundedCornerShape(CornerRadius.Large))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (activeShift != null) {
                ShiftElapsedDisplay(
                    elapsedSeconds = elapsedSeconds,
                    running = true,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = stringResource(R.string.dashboard_since_time, formatInstantTime(activeShift.startTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    IconButton(onClick = onEditStartTime, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector        = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.dashboard_edit_start_time),
                            modifier           = Modifier.size(18.dp),
                            tint               = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockOut,
                    shape   = RoundedCornerShape(CornerRadius.Medium),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.22f),
                        contentColor   = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .activeShiftPulse(true)
                        .semantics { contentDescription = clockOutContentDescription },
                ) {
                    Text(stringResource(R.string.dashboard_clock_out), fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text       = stringResource(R.string.dashboard_ready),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                    color      = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClockIn,
                    shape   = RoundedCornerShape(CornerRadius.Medium),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.22f),
                        contentColor   = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = clockInContentDescription },
                ) {
                    Text(stringResource(R.string.dashboard_clock_in), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// â”€â”€ Month summary â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ExpressiveClockCard(
    style: SupportedClockStyle,
    activeShift: Shift?,
    elapsedSeconds: Long,
    dailyOtMinutes: Int,
    todayBaseMinutes: Int = 0,
    activeStartedToday: Boolean = true,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val running = activeShift != null
    val clockInContentDescription = stringResource(R.string.dashboard_clock_in_accessibility)
    val clockOutContentDescription = stringResource(R.string.dashboard_clock_out_accessibility)
    val daySeconds = todayBaseMinutes * 60L + if (activeStartedToday) elapsedSeconds else 0L
    val progress = if (running && dailyOtMinutes > 0) {
        (daySeconds / (dailyOtMinutes * 60f)).coerceIn(0f, 1f)
    } else 0f
    val overtime = running && daySeconds > dailyOtMinutes * 60L
    // The v1.2 faces (Metro, Vinyl, Luna) stay glanceable while clocked out,
    // so they read whole-day progress rather than the running-gated value.
    val dayProgress = if (dailyOtMinutes > 0) {
        (daySeconds / (dailyOtMinutes * 60f)).coerceIn(0f, 1f)
    } else 0f
    val dayOvertime = dailyOtMinutes > 0 && daySeconds > dailyOtMinutes * 60L
    // Two further hours fill the whole overtime extension, per the reference.
    val overtimeExtension = if (dayOvertime) {
        ((daySeconds - dailyOtMinutes * 60L) / 7200f).coerceIn(0f, 1f)
    } else 0f
    val vinylSpin = vinylSpinDegrees(active = style == SupportedClockStyle.VINYL && running)
    // Needle-drop: the tonearm sweeps to position instead of teleporting when
    // the day total jumps (clock-in after a break, edited start time).
    val vinylProgress by animateFloatAsState(
        targetValue = dayProgress,
        animationSpec = tween(900, easing = AuroraEaseOut),
        label = "vinyl-needle-drop",
    )
    ExpressiveClockPulse(running = running) { pulse ->
    val background = when (style) {
        SupportedClockStyle.BOLD -> Color(0xff222038)
        SupportedClockStyle.NIGHT -> Color(0xff080b25)
        SupportedClockStyle.RETRO -> Color(0xff2b2418)
        SupportedClockStyle.VINYL -> Color(0xff181530)
        else -> MaterialTheme.colorScheme.surface
    }
    val dark = style in listOf(
        SupportedClockStyle.BOLD, SupportedClockStyle.NIGHT, SupportedClockStyle.RETRO,
        SupportedClockStyle.VINYL,
    )
    val foreground = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val faceTrack = MaterialTheme.colorScheme.surfaceVariant
    val accent = when {
        overtime -> AuroraPeach
        style == SupportedClockStyle.RETRO -> Color(0xffffc857)
        style == SupportedClockStyle.NIGHT -> AuroraAqua
        style == SupportedClockStyle.TIDE -> AuroraAqua
        style == SupportedClockStyle.VINYL -> AuroraAqua
        style == SupportedClockStyle.SPROUT -> SproutLeafDeep
        else -> AuroraIndigo
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (style) {
                    SupportedClockStyle.FOCUS -> stringResource(if (running) R.string.dashboard_clock_focus_session else R.string.dashboard_clock_ready_to_focus)
                    SupportedClockStyle.BOLD -> stringResource(if (running) R.string.dashboard_clock_on_the_clock else R.string.dashboard_clock_make_it_count)
                    SupportedClockStyle.NIGHT -> stringResource(if (running) R.string.dashboard_clock_night_shift else R.string.dashboard_clock_standby)
                    SupportedClockStyle.RETRO -> stringResource(if (running) R.string.dashboard_clock_shift_active else R.string.dashboard_clock_system_ready)
                    SupportedClockStyle.PULSE -> stringResource(if (running) R.string.dashboard_clock_shift_active else R.string.dashboard_clock_ready_caps)
                    SupportedClockStyle.DIAL, SupportedClockStyle.PRISM -> stringResource(if (running) R.string.dashboard_clock_elapsed else R.string.dashboard_clock_ready_caps)
                    SupportedClockStyle.STRAND -> stringResource(if (running) R.string.dashboard_clock_workday else R.string.dashboard_clock_ready_caps)
                    SupportedClockStyle.SAND -> stringResource(if (running) R.string.dashboard_clock_time_flowing else R.string.dashboard_clock_ready_caps)
                    SupportedClockStyle.BLOCKS -> stringResource(if (running) R.string.dashboard_clock_workday else R.string.dashboard_clock_ready_caps)
                    SupportedClockStyle.ORBIT -> stringResource(if (running) R.string.dashboard_clock_in_orbit else R.string.dashboard_clock_ready_caps)
                    SupportedClockStyle.TIDE -> stringResource(if (running) R.string.dashboard_clock_tide_rising else R.string.dashboard_clock_ready_caps)
                    SupportedClockStyle.SPROUT -> stringResource(if (running) R.string.dashboard_clock_growing else R.string.dashboard_clock_ready_to_grow)
                    SupportedClockStyle.METRO -> stringResource(if (running) R.string.dashboard_clock_in_transit else R.string.dashboard_clock_on_platform)
                    SupportedClockStyle.VINYL -> stringResource(if (running) R.string.dashboard_clock_now_playing else R.string.dashboard_clock_drop_needle)
                    SupportedClockStyle.LUNA -> stringResource(if (running) R.string.dashboard_clock_waxing else R.string.dashboard_clock_new_moon)
                    SupportedClockStyle.SUMMIT -> stringResource(if (running) R.string.dashboard_clock_climbing else R.string.dashboard_clock_base_camp)
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (running) accent else foreground.copy(alpha = .55f),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    when (style) {
                        SupportedClockStyle.FOCUS -> {
                            val y = size.height - 18.dp.toPx()
                            drawRoundRect(faceTrack, Offset(0f, y), Size(size.width, 8.dp.toPx()), GeometryCornerRadius(8.dp.toPx()))
                            drawRoundRect(accent, Offset(0f, y), Size(size.width * progress, 8.dp.toPx()), GeometryCornerRadius(8.dp.toPx()))
                        }
                        SupportedClockStyle.BOLD -> repeat(4) { index ->
                            val x = size.width * (.12f + index * .26f)
                            drawLine(accent.copy(alpha = .12f + index * .04f), Offset(x - 45f, 0f), Offset(x + 45f, size.height), 18f)
                        }
                        SupportedClockStyle.NIGHT -> {
                            repeat(28) { index ->
                                val x = ((index * 73) % 101) / 100f * size.width
                                val y = ((index * 47) % 97) / 100f * size.height
                                drawCircle(Color.White.copy(alpha = if (index % 3 == 0) .25f + pulse() * .65f else .3f), 1.2.dp.toPx() + index % 2, Offset(x, y))
                            }
                            if (running) drawCircle(accent.copy(alpha = .08f + pulse() * .08f), 72.dp.toPx(), center)
                        }
                        SupportedClockStyle.RETRO -> {
                            // Just the amber grid: the split-flap board drawn in
                            // the overlay below is the face.
                            val gap = 13.dp.toPx()
                            var x = 0f
                            while (x < size.width) { drawLine(accent.copy(alpha = .08f), Offset(x, 0f), Offset(x, size.height), 1f); x += gap }
                            var y = 0f
                            while (y < size.height) { drawLine(accent.copy(alpha = .08f), Offset(0f, y), Offset(size.width, y), 1f); y += gap }
                        }
                        SupportedClockStyle.PULSE -> repeat(3) { index ->
                            val phase = (pulse() + index / 3f) % 1f
                            drawCircle(accent.copy(alpha = (1f - phase) * .32f), (32 + phase * 58).dp.toPx(), center, style = Stroke(2.dp.toPx()))
                        }
                        SupportedClockStyle.DIAL -> {
                            val radius = 70.dp.toPx()
                            repeat(60) { index ->
                                val major = index % 5 == 0
                                rotate(index * 6f, center) {
                                    drawLine(if (major) foreground.copy(alpha = .65f) else foreground.copy(alpha = .2f), Offset(center.x, center.y - radius), Offset(center.x, center.y - radius + if (major) 10.dp.toPx() else 5.dp.toPx()), if (major) 2.dp.toPx() else 1.dp.toPx())
                                }
                            }
                            rotate(progress * 360f, center) { drawLine(accent, center, Offset(center.x, center.y - radius + 14.dp.toPx()), 3.dp.toPx(), StrokeCap.Round) }
                            drawCircle(accent, 7.dp.toPx(), center)
                        }
                        SupportedClockStyle.STRAND -> {
                            val count = 20
                            val lit = (progress * count).toInt()
                            repeat(count) { index ->
                                val x = (index + .5f) * size.width / count
                                val color = if (index < lit) accent else foreground.copy(alpha = .16f + if (index == lit) pulse() * .2f else 0f)
                                drawLine(color, Offset(x, 10.dp.toPx()), Offset(x, size.height - 10.dp.toPx()), if (index == lit) 3.dp.toPx() else 1.5.dp.toPx(), StrokeCap.Round)
                            }
                        }
                        SupportedClockStyle.PRISM -> {
                            val top = Offset(center.x, 8.dp.toPx())
                            val left = Offset(20.dp.toPx(), size.height - 10.dp.toPx())
                            val right = Offset(size.width - 20.dp.toPx(), size.height - 10.dp.toPx())
                            val triangle = Path().apply { moveTo(top.x, top.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }
                            drawPath(triangle, accent.copy(alpha = .45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            if (running) {
                                val fillY = left.y - (left.y - top.y) * progress
                                val half = (right.x - left.x) * (1f - progress) / 2f
                                val fill = Path().apply { moveTo(center.x - half, fillY); lineTo(left.x, left.y); lineTo(right.x, right.y); lineTo(center.x + half, fillY); close() }
                                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = .2f), AuroraAqua.copy(alpha = .55f))))
                            }
                            drawCircle(accent.copy(alpha = .55f + pulse() * .45f), 4.dp.toPx(), top)
                            drawCircle(AuroraPlum.copy(alpha = .55f + pulse() * .45f), 4.dp.toPx(), left)
                            drawCircle(AuroraAqua.copy(alpha = .55f + pulse() * .45f), 4.dp.toPx(), right)
                        }
                        SupportedClockStyle.SAND -> {
                            val top = 10.dp.toPx()
                            val bottom = size.height - 10.dp.toPx()
                            val mid = size.height / 2f
                            val bulbW = size.width * 0.44f
                            val neck = size.width * 0.12f
                            val glass = Path().apply {
                                moveTo(center.x - bulbW / 2f, top)
                                quadraticTo(center.x, top - 8.dp.toPx(), center.x + bulbW / 2f, top)
                                lineTo(center.x + neck / 2f, mid - 2.dp.toPx())
                                lineTo(center.x + bulbW / 2f, bottom)
                                quadraticTo(center.x, bottom + 8.dp.toPx(), center.x - bulbW / 2f, bottom)
                                lineTo(center.x - neck / 2f, mid + 2.dp.toPx())
                                close()
                            }
                            drawPath(glass, faceTrack.copy(alpha = 0.55f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            if (running) {
                                val topFillHeight = (mid - top - 10.dp.toPx()) * (1f - progress)
                                if (topFillHeight > 2.dp.toPx()) {
                                    val topSand = Path().apply {
                                        moveTo(center.x - bulbW / 2f + 10.dp.toPx(), top + 6.dp.toPx())
                                        lineTo(center.x + bulbW / 2f - 10.dp.toPx(), top + 6.dp.toPx())
                                        lineTo(center.x + neck / 2f - 2.dp.toPx(), top + 6.dp.toPx() + topFillHeight)
                                        lineTo(center.x - neck / 2f + 2.dp.toPx(), top + 6.dp.toPx() + topFillHeight)
                                        close()
                                    }
                                    drawPath(topSand, Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f))))
                                }
                                val bottomFillHeight = (bottom - mid - 10.dp.toPx()) * progress
                                if (bottomFillHeight > 2.dp.toPx()) {
                                    val bottomSand = Path().apply {
                                        moveTo(center.x - neck / 2f + 2.dp.toPx(), bottom - 6.dp.toPx() - bottomFillHeight)
                                        lineTo(center.x + neck / 2f - 2.dp.toPx(), bottom - 6.dp.toPx() - bottomFillHeight)
                                        lineTo(center.x + bulbW / 2f - 10.dp.toPx(), bottom - 6.dp.toPx())
                                        lineTo(center.x - bulbW / 2f + 10.dp.toPx(), bottom - 6.dp.toPx())
                                        close()
                                    }
                                    drawPath(bottomSand, Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), accent)))
                                }
                                repeat(3) { index ->
                                    val phase = (pulse() + index / 3f) % 1f
                                    drawCircle(
                                        accent.copy(alpha = 0.25f + phase * 0.45f),
                                        2.dp.toPx(),
                                        Offset(center.x + (index - 1) * 4.dp.toPx(), mid + (phase - 0.5f) * 10.dp.toPx()),
                                    )
                                }
                            }
                        }
                        SupportedClockStyle.BLOCKS -> {
                            val blockCount = 8
                            val gap = 5.dp.toPx()
                            val blockW = (size.width - gap * (blockCount - 1)) / blockCount
                            val blockH = 30.dp.toPx()
                            val baseY = size.height - blockH - 6.dp.toPx()
                            val filled = (progress * blockCount).toInt()
                            val partial = progress * blockCount - filled
                            repeat(blockCount) { index ->
                                val x = index * (blockW + gap)
                                val isFilled = index < filled
                                val isCurrent = index == filled && running
                                val color = when {
                                    isFilled -> accent
                                    isCurrent -> accent.copy(alpha = 0.35f + pulse() * 0.35f)
                                    else -> foreground.copy(alpha = 0.12f)
                                }
                                val height = if (isCurrent) blockH * (0.5f + partial * 0.5f) else blockH
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(x, baseY + blockH - height),
                                    size = Size(blockW, height),
                                    cornerRadius = GeometryCornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                )
                            }
                        }
                        SupportedClockStyle.ORBIT -> {
                            val radius = 58.dp.toPx()
                            drawCircle(
                                faceTrack.copy(alpha = 0.65f),
                                radius,
                                center,
                                style = Stroke(
                                    1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
                                ),
                            )
                            if (running && progress > 0f) {
                                drawArc(
                                    color = accent.copy(alpha = 0.22f),
                                    startAngle = -90f,
                                    sweepAngle = progress * 360f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                            val angle = Math.toRadians((progress * 360.0 - 90.0))
                            val satX = center.x + kotlin.math.cos(angle).toFloat() * radius
                            val satY = center.y + kotlin.math.sin(angle).toFloat() * radius
                            if (running) {
                                drawCircle(accent.copy(alpha = 0.12f + pulse() * 0.12f), 16.dp.toPx(), Offset(satX, satY))
                            }
                            drawCircle(accent, 6.dp.toPx(), Offset(satX, satY))
                            drawCircle(Color.White, 2.dp.toPx(), Offset(satX, satY))
                        }
                        SupportedClockStyle.TIDE -> {
                            val radius = 74.dp.toPx()
                            val vessel = Path().apply {
                                addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                            }
                            drawCircle(faceTrack.copy(alpha = .6f), radius, center, style = Stroke(2.dp.toPx()))
                            // Idle keeps a calm pool at the bottom; while running the
                            // waterline climbs with day-goal progress.
                            val level = center.y + radius - (radius * 2f) * (.12f + progress * .76f)
                            val wavePhase = pulse() * 2f * Math.PI.toFloat()
                            fun wave(phaseShift: Float, amplitude: Float): Path = Path().apply {
                                moveTo(center.x - radius, level)
                                var x = center.x - radius
                                while (x <= center.x + radius) {
                                    val y = level + kotlin.math.sin((x - center.x) / radius * 3.2f + wavePhase + phaseShift) * amplitude
                                    lineTo(x, y)
                                    x += 6f
                                }
                                lineTo(center.x + radius, center.y + radius)
                                lineTo(center.x - radius, center.y + radius)
                                close()
                            }
                            clipPath(vessel) {
                                drawPath(wave(1.7f, 6.dp.toPx()), accent.copy(alpha = .25f))
                                drawPath(
                                    wave(0f, 4.dp.toPx()),
                                    Brush.verticalGradient(
                                        listOf(accent.copy(alpha = .55f), AuroraIndigo.copy(alpha = .8f)),
                                        startY = level,
                                        endY = center.y + radius,
                                    ),
                                )
                                if (running) {
                                    repeat(3) { index ->
                                        val phase = (pulse() + index / 3f) % 1f
                                        val rise = (center.y + radius - level).coerceAtLeast(1f)
                                        drawCircle(
                                            Color.White.copy(alpha = (1f - phase) * .45f),
                                            (2 + index % 2).dp.toPx(),
                                            Offset(
                                                center.x + (index - 1) * 24.dp.toPx(),
                                                center.y + radius - 6.dp.toPx() - phase * (rise - 10.dp.toPx()).coerceAtLeast(0f),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        SupportedClockStyle.SPROUT -> drawSproutFace(
                            growthHours = (daySeconds / 3600f).coerceIn(0f, 8f),
                            pulse = pulse(),
                            running = running,
                            foreground = foreground,
                        )
                        SupportedClockStyle.METRO -> drawMetroFace(
                            progress = dayProgress,
                            overtime = dayOvertime,
                            overtimeProgress = overtimeExtension,
                            pulse = pulse(),
                            running = running,
                            foreground = foreground,
                            surface = background,
                        )
                        SupportedClockStyle.VINYL -> drawVinylFace(
                            progress = vinylProgress,
                            spinDegrees = vinylSpin,
                            pulse = pulse(),
                            running = running,
                            overtime = dayOvertime,
                        )
                        SupportedClockStyle.LUNA -> drawLunaFace(
                            progress = dayProgress,
                            pulse = pulse(),
                            running = running,
                            overtime = dayOvertime,
                        )
                        SupportedClockStyle.SUMMIT -> drawSummitFace(
                            progress = dayProgress,
                            overtime = dayOvertime,
                            overtimeProgress = overtimeExtension,
                            pulse = pulse(),
                            running = running,
                            foreground = foreground,
                            surface = background,
                        )
                        else -> Unit
                    }
                }
                if (style == SupportedClockStyle.RETRO) {
                    // The split-flap board is this face's readout, in both
                    // states the text branches below cover: elapsed time on a
                    // running shift, the wall clock while idle.
                    RetroFlipBoard(
                        text = if (running) {
                            flipElapsedText(elapsedSeconds)
                        } else {
                            rememberCurrentInstant(MINUTE_MILLIS)
                                .atZone(LocalWorkZone.current)
                                .format(timeFormatter)
                        },
                        digitColor = accent,
                        surface = background,
                    )
                } else if (running) {
                    ShiftElapsedDisplay(
                        elapsedSeconds = elapsedSeconds,
                        running = true,
                        style = when (style) {
                            SupportedClockStyle.BOLD -> MaterialTheme.typography.displayLarge
                            SupportedClockStyle.FOCUS -> MaterialTheme.typography.displayMedium
                            else -> MaterialTheme.typography.displaySmall
                        }.let { base ->
                            base.copy(
                                fontWeight = if (style == SupportedClockStyle.FOCUS) FontWeight.Light else FontWeight.Bold,
                            )
                        },
                        color = foreground,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        // rememberCurrentInstant, not LocalTime.now(): with no
                        // shift running nothing else on this screen recomposes,
                        // so a direct read froze at the minute the dashboard was
                        // opened. The work zone, not the device's, for the same
                        // reason every other time on this screen uses it.
                        rememberCurrentInstant(MINUTE_MILLIS)
                            .atZone(LocalWorkZone.current)
                            .format(timeFormatter),
                        style = if (style == SupportedClockStyle.BOLD) MaterialTheme.typography.displayLarge else if (style == SupportedClockStyle.FOCUS) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                        fontWeight = if (style == SupportedClockStyle.FOCUS) FontWeight.Light else FontWeight.Bold,
                        color = foreground,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (activeShift != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dashboard_since_time, formatInstantTime(activeShift.startTime)), style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = .65f))
                    IconButton(onClick = onEditStartTime, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.dashboard_edit_start_time), tint = foreground.copy(alpha = .6f), modifier = Modifier.size(18.dp))
                    }
                }
            } else Text(stringResource(R.string.dashboard_tap_to_start), style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = .6f))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = if (running) onClockOut else onClockIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .activeShiftPulse(running)
                    .semantics {
                        contentDescription = if (running) {
                            clockOutContentDescription
                        } else {
                            clockInContentDescription
                        }
                    },
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) accent.copy(alpha = if (dark) .25f else .12f) else accent,
                    contentColor = if (running) accent else Color.White,
                ),
            ) { Text(stringResource(if (running) R.string.dashboard_clock_out else R.string.dashboard_clock_in), fontWeight = FontWeight.Bold) }
        }
    }
    }
}

private val SproutLeafDeep = Color(0xFF2E9E6B)
private val SproutLeafLight = Color(0xFF43C98A)
private val SproutSoil = Color(0xFF7A5B44)
private val SproutBud = Color(0xFF7C4DD4)
private val SproutPetalA = Color(0xFF8B5CF6)
private val SproutPetalB = Color(0xFFB07CF8)
private val SproutCore = Color(0xFFFFC857)
private val SproutFirefly = Color(0xFFFFE08A)
private val SproutGlow = Color(0xFFFFB27D)

/**
 * Sprout face: the plant is the day. The stem rises with hours worked, one
 * true leaf unfurls per hour (1–6), a bud forms in hour 7, and the flower
 * blooms at eight. Growth tracks today's total, so the plant is worth a
 * glance even while clocked out; sway, fireflies, and the petal shimmer
 * only run during a shift.
 */
private fun DrawScope.drawSproutFace(
    growthHours: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    val cx = size.width / 2f
    val groundY = size.height - 16.dp.toPx()

    fun smooth(raw: Float): Float {
        val t = raw.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    if (running) {
        val auraCenter = Offset(cx, groundY - 60.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SproutLeafDeep.copy(alpha = 0.06f + pulse * 0.03f), Color.Transparent),
                center = auraCenter,
                radius = 80.dp.toPx(),
            ),
            radius = 80.dp.toPx(),
            center = auraCenter,
        )
    }

    drawLine(
        foreground.copy(alpha = 0.12f),
        Offset(size.width * 0.08f, groundY),
        Offset(size.width * 0.92f, groundY),
        2.dp.toPx(),
        StrokeCap.Round,
    )
    drawOval(
        SproutSoil.copy(alpha = 0.6f),
        topLeft = Offset(cx - 27.dp.toPx(), groundY - 5.dp.toPx()),
        size = Size(54.dp.toPx(), 10.dp.toPx()),
    )

    if (growthHours <= 0.05f) {
        // A seed, waiting for the first clock-in of the day.
        drawCircle(SproutLeafDeep, 4.dp.toPx(), Offset(cx, groundY - 4.dp.toPx()))
        return
    }

    val stemH = 18.dp.toPx() + 15.dp.toPx() * growthHours
    val sway = if (running) {
        kotlin.math.sin(pulse * 2f * Math.PI.toFloat()) * 2.5.dp.toPx() * (growthHours / 2f).coerceAtMost(1f)
    } else 0f
    val tipX = cx + sway
    val tipY = groundY - stemH
    val ctrlX = cx - sway * 0.8f
    val ctrlY = groundY - stemH * 0.5f

    fun onStem(f: Float): Offset {
        val inv = 1f - f
        return Offset(
            inv * inv * cx + 2f * inv * f * ctrlX + f * f * tipX,
            inv * inv * groundY + 2f * inv * f * ctrlY + f * f * tipY,
        )
    }

    val stem = Path().apply {
        moveTo(cx, groundY)
        quadraticTo(ctrlX, ctrlY, tipX, tipY)
    }
    drawPath(stem, SproutLeafDeep, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))

    fun drawLeaf(at: Offset, dir: Int, length: Float, alpha: Float) {
        if (length <= 0.5f) return
        val leafTip = Offset(at.x + dir * length * 0.88f, at.y - length * 0.7f)
        val blade = Path().apply {
            moveTo(at.x, at.y)
            quadraticTo(at.x + dir * length * 0.45f, at.y - length * 0.75f, leafTip.x, leafTip.y)
            quadraticTo(at.x + dir * length * 0.62f, at.y + length * 0.06f, at.x, at.y)
            close()
        }
        drawPath(blade, Brush.linearGradient(listOf(SproutLeafLight, SproutLeafDeep), start = at, end = leafTip), alpha = alpha)
        drawLine(SproutLeafDeep.copy(alpha = alpha * 0.35f), at, leafTip, 1.dp.toPx(), StrokeCap.Round)
    }

    // First-hour cotyledon pair, folding away once true leaves arrive.
    val cotyledon = smooth(growthHours) * (1f - smooth(growthHours - 1.2f))
    if (cotyledon > 0.01f && growthHours < 2.2f) {
        val at = onStem(0.97f)
        drawLeaf(at, -1, 9.dp.toPx() * cotyledon, 0.95f)
        drawLeaf(at, 1, 9.dp.toPx() * cotyledon, 0.95f)
    }

    // One true leaf per hour (hours 1-6), alternating sides bottom-up.
    repeat(6) { i ->
        val t = smooth(growthHours - (i + 1))
        if (t <= 0f) return@repeat
        val at = onStem(0.22f + i * 0.115f)
        val dir = if (i % 2 == 0) -1 else 1
        drawLeaf(at, dir, (24f - i * 1.6f).dp.toPx() * t, 0.95f)
    }

    // Hour 7 raises a bud; hour 8 opens it.
    val tBud = smooth((growthHours - 7f) * 2f)
    val tBloom = smooth((growthHours - 7.5f) * 2f)
    val tip = onStem(1f)
    if (tBloom > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SproutGlow.copy(alpha = 0.25f * tBloom + if (running) pulse * 0.06f else 0f),
                    Color.Transparent,
                ),
                center = tip,
                radius = (16f + pulse * 3f).dp.toPx(),
            ),
            radius = 20.dp.toPx(),
            center = tip,
        )
        repeat(6) { k ->
            rotate(k * 60f + if (running) pulse * 6f else 0f, tip) {
                drawOval(
                    color = if (k % 2 == 0) SproutPetalA else SproutPetalB,
                    topLeft = Offset(tip.x - 3.2.dp.toPx() * tBloom, tip.y - 16.dp.toPx() * tBloom),
                    size = Size(6.4.dp.toPx() * tBloom, 16.dp.toPx() * tBloom),
                    alpha = 0.95f,
                )
            }
        }
        drawCircle(SproutCore, 4.2.dp.toPx() * tBloom + 2.dp.toPx(), tip)
    } else if (tBud > 0.01f) {
        drawCircle(SproutBud, 5.dp.toPx() * tBud, Offset(tip.x, tip.y - 2.dp.toPx() * tBud))
        drawLeaf(tip, -1, 7.dp.toPx() * tBud, 0.9f)
        drawLeaf(tip, 1, 7.dp.toPx() * tBud, 0.9f)
    }

    // Fireflies drifting up while on shift — the "alive" cue that pulls you back.
    if (running && growthHours >= 1.5f) {
        repeat(3) { k ->
            val phase = (pulse + k / 3f) % 1f
            val fx = cx + (k - 1) * 26.dp.toPx() + kotlin.math.sin(phase * 6.28f + k) * 6.dp.toPx()
            val fy = groundY - phase * (stemH + 20.dp.toPx())
            drawCircle(SproutFirefly.copy(alpha = (1f - phase) * 0.5f), 1.8.dp.toPx(), Offset(fx, fy))
        }
    }
}

/**
 * Continuous rotation for the Vinyl face — the record turns only while a shift
 * runs and motion is allowed; with reduced motion the disc freezes in place
 * while the tonearm keeps showing progress.
 */
@Composable
private fun vinylSpinDegrees(active: Boolean): Float {
    if (!auroraMotionEnabled() || !active) return 0f
    val transition = rememberInfiniteTransition(label = "vinyl-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5400, easing = LinearEasing), RepeatMode.Restart),
        label = "vinyl-spin-angle",
    )
    return angle
}

/**
 * Supplies the clock faces' 1.8s animation phase.
 *
 * The value is handed over as a reader, not a `Float`, and every caller invokes
 * it inside the `Canvas` draw lambda. That is the whole point: passing the
 * `Float` meant this composable read the animation in its own scope and the
 * animation invalidated `content` — which is the entire clock card, a 16-branch
 * `when`, a dozen `stringResource` lookups, a 176dp `Canvas`, the elapsed
 * display and a button. On a 120Hz panel that is a full recomposition 120 times
 * a second for as long as a shift is running and the dashboard is on screen.
 * Reading it during draw invalidates only the draw phase. Same pattern as
 * `LiveClockTimer`, which reads its animated value inside `graphicsLayer`.
 */
@Composable
private fun ExpressiveClockPulse(
    running: Boolean,
    content: @Composable (pulse: () -> Float) -> Unit,
) {
    if (!auroraMotionEnabled() || !running) {
        content { 0f }
        return
    }
    val transition = rememberInfiniteTransition(label = "expressive-clock-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "clock-motion",
    )
    content { pulse }
}

@Composable
internal fun MonthSummaryCard(
    report: MonthlyReport?,
    paySummary: PayrollCalculator.MonthlyPaySummary?,
    currencyCode: String,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the current month, so the heading follows the calendar over a
    // month boundary instead of naming the month the screen was opened in. The
    // remember still holds: recomposing does not repeat the locale lookup or the
    // display-name allocation unless the month or the locale actually changed.
    val locale = appLocale()
    val month = YearMonth.from(rememberCurrentInstant(MINUTE_MILLIS).atZone(LocalWorkZone.current))
    val monthName = remember(locale, month) {
        month.month.getDisplayName(TextStyle.FULL, locale)
    }
    val shiftCount = report?.shiftCount ?: 0
    val openReportLabel = stringResource(R.string.dashboard_month_summary_open_report)

    ElmCardPadded(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openReportLabel, onClick = onOpenReport),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElmSectionHeader(
                title = stringResource(R.string.dashboard_month_summary_title, monthName),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    if (shiftCount == 1) R.string.dashboard_shift_count_one else R.string.dashboard_shift_count_other,
                    shiftCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(Layout.rowGap))

        // Hours and pay on one line: the two numbers a user opens the app to
        // check. Everything behind them lives one tap away in Reports.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.dashboard_hours_value,
                        HoursFormatter.decimal(report?.totalMinutes ?: 0),
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.dashboard_stat_total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (paySummary != null && paySummary.totalGross > 0.0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MoneyFormatter.format(paySummary.totalGross, currencyCode),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.dashboard_gross_before_tax),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(Layout.rowGap))

        ElmDistributionBar(
            regularMinutes = report?.regularMinutes ?: 0,
            overtimeMinutes = report?.overtimeMinutes ?: 0,
            weekendMinutes = report?.weekendMinutes ?: 0,
        )

        Spacer(Modifier.height(Layout.inlineGap))

        ElmDistributionLegend(
            regularMinutes = report?.regularMinutes ?: 0,
            overtimeMinutes = report?.overtimeMinutes ?: 0,
            weekendMinutes = report?.weekendMinutes ?: 0,
        )

        Spacer(Modifier.height(Layout.rowGap))

        // Its own row, below the legend.
        //
        // It used to sit beside the legend in a SpaceBetween row, which put it in
        // the wrong place twice over: centred against a three-row legend it landed
        // level with the middle row and read as part of "Overtime", and because
        // every legend row is itself full-width SpaceBetween, the row's hours were
        // pushed right up against it — "0.0hView full report" with no gap at all.
        //
        // This is the whole card's action (the card is clickable; the label is what
        // TalkBack announces for it), so it belongs at the end of the card, not
        // inside its contents. End-aligned with a chevron to match the right-hand
        // column the header count, the pay figure and every legend row already form.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = openReportLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.s2))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Spacing.s18).mirrorInRtl(),
            )
        }
    }
}




// â”€â”€ Recent shifts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun RecentShiftsSection(
    recentShifts: List<Shift>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ElmSectionHeader(title = stringResource(R.string.dashboard_recent_shifts))
        Spacer(Modifier.height(10.dp))
        if (recentShifts.isEmpty()) {
            Text(
                text  = stringResource(R.string.dashboard_no_completed_shifts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ElmCard {
                recentShifts.forEachIndexed { index, shift ->
                    RecentShiftRow(
                        shift = shift,
                        showDivider = index < recentShifts.lastIndex,
                        modifier = Modifier.auroraEnter(index = index),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentShiftRow(
    shift: Shift,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val zone         = LocalWorkZone.current
    val dateText     = shift.startTime.atZone(zone).format(dateFormatter)
    val startText    = shift.startTime.atZone(zone).format(timeFormatter)
    val endText      = shift.endTime?.atZone(zone)?.format(timeFormatter) ?: "-"
    val durationText = ShiftDurationCalculator.netMinutes(shift)
        ?.let { durationText(it) } ?: "-"

    val stripeColor = if (shift.isSpecialDay) AuroraPlum else AuroraIndigo.copy(alpha = 0.35f)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(stripeColor))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = dateText,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    if (shift.isSpecialDay) {
                        Text(
                            text  = stringResource(R.string.dashboard_special_day_suffix),
                            style = MaterialTheme.typography.labelSmall,
                            color = auroraWeekendInk(),
                        )
                    }
                }
                Text(
                    text  = stringResource(R.string.dashboard_shift_time_range, startText, endText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text       = durationText,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary,
            )
        }
        }
        if (showDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

// â”€â”€ Edit start time dialog â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun EditStartTimeDialog(
    currentStartTime: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    onConfirm: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    // Work timezone, not device timezone: an entered wall-clock time must land
    // on the same work-day the rest of the app groups this shift into.
    val zonedStart = currentStartTime.atZone(zone)

    fun candidateFor(hour: Int, minute: Int): Instant =
        LocalDateTime.of(zonedStart.toLocalDate(), LocalTime.of(hour, minute))
            .atZone(zone).toInstant()

    AppTimePickerDialog(
        initialHour = zonedStart.hour,
        initialMinute = zonedStart.minute,
        title = stringResource(R.string.dashboard_edit_start_time),
        confirmLabel = stringResource(R.string.dashboard_save),
        cancelLabel = stringResource(R.string.dashboard_cancel),
        onConfirm = { hour, minute -> onConfirm(candidateFor(hour, minute)) },
        onDismiss = onDismiss,
        confirmEnabled = { hour, minute -> !candidateFor(hour, minute).isAfter(Instant.now()) },
        supportingContent = { hour, minute ->
            if (candidateFor(hour, minute).isAfter(Instant.now())) {
                Text(
                    stringResource(R.string.dashboard_start_time_future_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

// â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun formatInstantTime(instant: Instant): String =
    instant.atZone(LocalWorkZone.current).format(timeFormatter)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DashboardScreenPreview() {
    ElmTrackrTheme {
        DashboardError("preview only")
    }
}

/** Renders the dashboard ready state for screenshot / UI tests. */
@Composable
internal fun DashboardReadyPreview(
    state: DashboardUiState.Ready,
    modifier: Modifier = Modifier,
    onNavigateToReports: () -> Unit = {},
) {
    DashboardReady(
        state = state,
        onClockIn = {},
        onClockOut = {},
        onEditStartTime = { _, _ -> },
        onNavigateToReports = onNavigateToReports,
        onSelectTask = {},
        onSelectWorkProfile = {},
        onManageTasks = {},
        showFirstClockInCelebration = false,
        onDismissFirstClockInCelebration = {},
    )
}

@Composable
private fun DashboardError(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(
            stringResource(R.string.dashboard_error_message, message),
            color = MaterialTheme.colorScheme.error,
        )
    }
}



