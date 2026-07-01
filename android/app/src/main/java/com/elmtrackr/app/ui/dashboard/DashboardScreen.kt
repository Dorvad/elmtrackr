package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.notification.NotificationPermissionCoordinator
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.components.motion.LiveClockTimer
import com.elmtrackr.app.ui.components.motion.activeShiftPulse
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraScreen
import com.elmtrackr.app.ui.layout.isTabletLayout
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmCardPadded
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.ElmStatCard
import com.elmtrackr.app.ui.design.ElmStatVariant
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.auroraSecondaryText
import com.elmtrackr.app.ui.theme.auroraWeekendBackground
import com.elmtrackr.app.ui.theme.auroraWeekendInk
import com.elmtrackr.app.ui.theme.AuroraHair
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraSurface
import com.elmtrackr.app.ui.theme.AuroraSurfaceSub
import com.elmtrackr.app.ui.theme.AuroraWeekendBg
import com.elmtrackr.app.ui.theme.AuroraWhite
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import kotlinx.coroutines.delay
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
) {
    val uiState by viewModel.uiState.collectAsState()
    val showCelebration by viewModel.showFirstClockInCelebration.collectAsState()
    var showTasks by rememberSaveable { mutableStateOf(false) }

    if (showTasks) {
        com.elmtrackr.app.ui.tasks.TaskManagementScreen(onBack = { showTasks = false })
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = Color.Transparent,
    ) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> DashboardSkeleton()
            is DashboardUiState.Ready  -> DashboardReady(
                state           = state,
                onClockIn       = viewModel::clockIn,
                onClockOut      = viewModel::clockOut,
                onEditStartTime = viewModel::editActiveShiftStartTime,
                onNavigateToReports = onNavigateToReports,
                onSelectTask    = viewModel::selectTask,
                onManageTasks   = { showTasks = true },
                showFirstClockInCelebration = showCelebration,
                onDismissFirstClockInCelebration = viewModel::dismissFirstClockInCelebration,
            )
            is DashboardUiState.Error  -> ErrorState(message = state.message, onRetry = viewModel::retry)
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
    onSelectTask: (String) -> Unit,
    onManageTasks: () -> Unit,
    showFirstClockInCelebration: Boolean,
    onDismissFirstClockInCelebration: () -> Unit,
) {
    val activeShift = state.activeShift
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }
    var pendingClockIn by rememberSaveable { mutableStateOf(false) }

    fun performClockIn() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
    var refundBannerDismissed by rememberSaveable { mutableStateOf(false) }

    if (showNotificationRationale && activity is MainActivity) {
        AlertDialog(
            onDismissRequest = {
                showNotificationRationale = false
                pendingClockIn = false
            },
            title = { Text("Stay on top of your shift") },
            text = {
                Text(
                    "ElmTrackr uses notifications to keep your active shift visible " +
                        "and remind you before overtime.",
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
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        if (pendingClockIn) performClockIn()
                        pendingClockIn = false
                    },
                ) { Text("Not now") }
            },
        )
    }

    val handleClockOut: () -> Unit = {
        val shift = activeShift
        if (shift != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClockOut(shift.id)
        }
    }

    var elapsedSeconds by remember(activeShift?.id) { mutableLongStateOf(0L) }
    LaunchedEffect(activeShift?.id) {
        val shift = activeShift ?: return@LaunchedEffect
        while (true) {
            elapsedSeconds = (Instant.now().toEpochMilli() - shift.startTime.toEpochMilli()) / 1000L
            delay(1_000L)
        }
    }

    if (showEditDialog && activeShift != null) {
        EditStartTimeDialog(
            currentStartTime = activeShift.startTime,
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

    val clockStyle = state.settings?.clockStyle?.toSupportedOrDefault()
        ?: SupportedClockStyle.CLASSIC

    val isTablet = isTabletLayout()

    AuroraScreen {
            DashboardHeader(displayName = state.displayName)

            if (state.recentShifts.isEmpty() && activeShift == null) {
                FirstRunWelcomeCard(onClockIn = handleClockIn)
            }

            val showRefundBanner = !refundBannerDismissed &&
                state.unresolvedRefundCount > 0 &&
                LocalDate.now().dayOfMonth >= LocalDate.now().lengthOfMonth() - 4

            if (showRefundBanner) {
                RefundReminderBanner(
                    count = state.unresolvedRefundCount,
                    onDismiss = { refundBannerDismissed = true },
                    onReviewRefunds = onNavigateToReports,
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraEnter(index = 1),
                )
            }

            val dailyOtMinutes = state.settings?.dailyOvertimeThresholdMinutes ?: (8 * 60)
            val currencyCode = state.paySummary?.currencyCode
                ?: state.settings?.currencyCode
                ?: state.settings?.currency?.name
                ?: "ILS"

            if (activeShift == null && state.activeTasks.isNotEmpty()) {
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
                            activeShift       = activeShift,
                            elapsedSeconds    = elapsedSeconds,
                            dailyOtMinutes    = dailyOtMinutes,
                            onClockIn         = handleClockIn,
                            onClockOut        = handleClockOut,
                            onEditStartTime   = { showEditDialog = true },
                        )
                        SupportedClockStyle.MINIMAL -> MinimalClockCard(
                            activeShift     = activeShift,
                            elapsedSeconds  = elapsedSeconds,
                            onClockIn       = handleClockIn,
                            onClockOut      = handleClockOut,
                            onEditStartTime = { showEditDialog = true },
                        )
                        SupportedClockStyle.AURORA -> AuroraClockCard(
                            activeShift     = activeShift,
                            elapsedSeconds  = elapsedSeconds,
                            onClockIn       = handleClockIn,
                            onClockOut      = handleClockOut,
                            onEditStartTime = { showEditDialog = true },
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
                        SupportedClockStyle.ORBIT -> ExpressiveClockCard(
                            style = renderStyle,
                            activeShift = activeShift,
                            elapsedSeconds = elapsedSeconds,
                            dailyOtMinutes = dailyOtMinutes,
                            onClockIn = handleClockIn,
                            onClockOut = handleClockOut,
                            onEditStartTime = { showEditDialog = true },
                        )
                        SupportedClockStyle.FELLOWSHIP -> FellowshipClockCard(
                            activeShift = activeShift,
                            elapsedSeconds = elapsedSeconds,
                            onClockIn = handleClockIn,
                            onClockOut = handleClockOut,
                            onEditStartTime = { showEditDialog = true },
                        )
                    }
                    }
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
                        MonthSummaryDistribution(
                            report = state.monthlyReport,
                            modifier = Modifier.auroraEnter(index = 2),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(0.58f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MonthSummaryStatsGrid(
                            report = state.monthlyReport,
                            modifier = Modifier.auroraEnter(index = 2),
                        )
                        MonthSummaryGrossPay(
                            paySummary = state.paySummary,
                            currencyCode = currencyCode,
                            modifier = Modifier.auroraEnter(index = 3),
                        )
                        RecentShiftsSection(
                            recentShifts = state.recentShifts,
                            modifier = Modifier.auroraEnter(index = 4),
                        )
                    }
                }
            } else {
                clockCard()

                MonthSummarySection(
                    report      = state.monthlyReport,
                    paySummary  = state.paySummary,
                    currencyCode = currencyCode,
                    modifier    = Modifier.auroraEnter(index = 2),
                )

                RecentShiftsSection(
                    recentShifts = state.recentShifts,
                    modifier = Modifier.auroraEnter(index = 3),
                )
            }
    }
}

// â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun DashboardHeader(
    displayName: String?,
) {
    val hour = Instant.now().atZone(ZoneId.systemDefault()).hour
    val greetingBase = when (hour) {
        in 0..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else     -> "Good evening"
    }
    val firstName = displayName?.trim()?.split(" ")?.firstOrNull()
    val greeting  = if (firstName != null)
        "${greetingBase.uppercase()} · ${firstName.uppercase()}"
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
                    text       = "elmtrackr",
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
                    "$count travel refund${if (count == 1) "" else "s"} pending",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = auroraWeekendInk(),
                )
                Text(
                    "Month end is near — don't forget to file your transport claims.",
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
                        "Review refunds →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = auroraWeekendInk(),
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = AuroraPlum, modifier = Modifier.size(16.dp))
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
            Text("You're ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Clock in once. See hours, pay estimate, and overtime instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            ElmGradientButton(
                onClick = onClockIn,
                accessibilityLabel = "Clock in. Start tracking your shift.",
            ) {
                Text("Clock in now", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FirstClockInCelebrationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text("You're tracking!", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Text(
                "Your hours, pay estimate, and overtime are live on the home screen. Keep the shift running — or clock out when you're done.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            ElmGradientButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Got it", fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
// â”€â”€ Classic clock card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ClassicClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    dailyOtMinutes: Int,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val elapsedMinutes = elapsedSeconds / 60f
    val progress = if (dailyOtMinutes > 0) (elapsedMinutes / dailyOtMinutes).coerceIn(0f, 1f) else 0f
    val isOvertime = activeShift != null && elapsedMinutes > dailyOtMinutes

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
                        Text(
                            text       = formatElapsedTime(elapsedSeconds),
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = if (isOvertime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        if (isOvertime) {
                            Text(
                                text  = "overtime",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraPeach,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "Since ${formatInstantTime(activeShift.startTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onEditStartTime, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector     = Icons.Filled.Edit,
                            contentDescription = "Edit start time",
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
                        .semantics { contentDescription = "Clock out. End your current shift." },
                ) {
                    Text("Clock Out", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = "Ready to clock in",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                ElmGradientButton(
                    onClick = onClockIn,
                    accessibilityLabel = "Clock in. Start tracking your shift.",
                ) {
                    Text("Clock In", fontWeight = FontWeight.Bold)
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
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = if (activeShift != null) formatElapsedTime(elapsedSeconds) else "00:00",
            style      = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Thin,
            color      = if (activeShift != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            textAlign  = TextAlign.Center,
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
                        contentDescription = "Edit start time",
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
                        "Clock out. End your current shift."
                    } else {
                        "Clock in. Start tracking your shift."
                    }
                },
        ) {
            Text(
                text       = if (activeShift != null) "OUT" else "IN",
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
                Text(
                    text       = formatElapsedTime(elapsedSeconds),
                    style      = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "Since ${formatInstantTime(activeShift.startTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    IconButton(onClick = onEditStartTime, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector        = Icons.Filled.Edit,
                            contentDescription = "Edit start time",
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
                        .semantics { contentDescription = "Clock out. End your current shift." },
                ) {
                    Text("Clock Out", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text       = "Ready",
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
                        .semantics { contentDescription = "Clock in. Start tracking your shift." },
                ) {
                    Text("Clock In", fontWeight = FontWeight.Bold)
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
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    val running = activeShift != null
    val progress = if (running && dailyOtMinutes > 0) {
        (elapsedSeconds / (dailyOtMinutes * 60f)).coerceIn(0f, 1f)
    } else 0f
    val overtime = running && elapsedSeconds > dailyOtMinutes * 60L
    val transition = rememberInfiniteTransition(label = "${style.name}-clock")
    val pulse by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "clock-motion",
    )
    val background = when (style) {
        SupportedClockStyle.BOLD -> Color(0xff222038)
        SupportedClockStyle.NIGHT -> Color(0xff080b25)
        SupportedClockStyle.RETRO -> Color(0xff2b2418)
        else -> MaterialTheme.colorScheme.surface
    }
    val dark = style in listOf(SupportedClockStyle.BOLD, SupportedClockStyle.NIGHT, SupportedClockStyle.RETRO)
    val foreground = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val faceTrack = MaterialTheme.colorScheme.surfaceVariant
    val accent = when {
        overtime -> AuroraPeach
        style == SupportedClockStyle.RETRO -> Color(0xffffc857)
        style == SupportedClockStyle.NIGHT -> AuroraAqua
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
                    SupportedClockStyle.FOCUS -> if (running) "FOCUS SESSION" else "READY TO FOCUS"
                    SupportedClockStyle.BOLD -> if (running) "ON THE CLOCK" else "MAKE IT COUNT"
                    SupportedClockStyle.NIGHT -> if (running) "NIGHT SHIFT" else "STANDBY"
                    SupportedClockStyle.RETRO -> if (running) "SHIFT ACTIVE" else "SYSTEM READY"
                    SupportedClockStyle.PULSE -> if (running) "SHIFT ACTIVE" else "READY"
                    SupportedClockStyle.DIAL, SupportedClockStyle.PRISM -> if (running) "ELAPSED" else "READY"
                    SupportedClockStyle.STRAND -> if (running) "WORKDAY" else "READY"
                    SupportedClockStyle.SAND -> if (running) "TIME FLOWING" else "READY"
                    SupportedClockStyle.BLOCKS -> if (running) "WORKDAY" else "READY"
                    SupportedClockStyle.ORBIT -> if (running) "IN ORBIT" else "READY"
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
                                drawCircle(Color.White.copy(alpha = if (index % 3 == 0) .25f + pulse * .65f else .3f), 1.2.dp.toPx() + index % 2, Offset(x, y))
                            }
                            if (running) drawCircle(accent.copy(alpha = .08f + pulse * .08f), 72.dp.toPx(), center)
                        }
                        SupportedClockStyle.RETRO -> {
                            val gap = 13.dp.toPx()
                            var x = 0f
                            while (x < size.width) { drawLine(accent.copy(alpha = .08f), Offset(x, 0f), Offset(x, size.height), 1f); x += gap }
                            var y = 0f
                            while (y < size.height) { drawLine(accent.copy(alpha = .08f), Offset(0f, y), Offset(size.width, y), 1f); y += gap }
                            drawRoundRect(accent.copy(alpha = .12f), Offset(size.width * .08f, size.height * .18f), Size(size.width * .84f, size.height * .64f), GeometryCornerRadius(5.dp.toPx()), style = Stroke(2.dp.toPx()))
                        }
                        SupportedClockStyle.PULSE -> repeat(3) { index ->
                            val phase = (pulse + index / 3f) % 1f
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
                                val color = if (index < lit) accent else foreground.copy(alpha = .16f + if (index == lit) pulse * .2f else 0f)
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
                            drawCircle(accent.copy(alpha = .55f + pulse * .45f), 4.dp.toPx(), top)
                            drawCircle(AuroraPlum.copy(alpha = .55f + pulse * .45f), 4.dp.toPx(), left)
                            drawCircle(AuroraAqua.copy(alpha = .55f + pulse * .45f), 4.dp.toPx(), right)
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
                                    val phase = (pulse + index / 3f) % 1f
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
                                    isCurrent -> accent.copy(alpha = 0.35f + pulse * 0.35f)
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
                                drawCircle(accent.copy(alpha = 0.12f + pulse * 0.12f), 16.dp.toPx(), Offset(satX, satY))
                            }
                            drawCircle(accent, 6.dp.toPx(), Offset(satX, satY))
                            drawCircle(Color.White, 2.dp.toPx(), Offset(satX, satY))
                        }
                        else -> Unit
                    }
                }
                Text(
                    if (running) formatElapsedTime(elapsedSeconds) else LocalTime.now().format(timeFormatter),
                    style = if (style == SupportedClockStyle.BOLD) MaterialTheme.typography.displayLarge else if (style == SupportedClockStyle.FOCUS) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = if (style == SupportedClockStyle.FOCUS) FontWeight.Light else FontWeight.Bold,
                    color = foreground,
                    textAlign = TextAlign.Center,
                )
            }
            if (activeShift != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Since ${formatInstantTime(activeShift.startTime)}", style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = .65f))
                    IconButton(onClick = onEditStartTime, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Edit, "Edit start time", tint = foreground.copy(alpha = .6f), modifier = Modifier.size(18.dp))
                    }
                }
            } else Text("Tap to start tracking your shift", style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = .6f))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = if (running) onClockOut else onClockIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .activeShiftPulse(running)
                    .semantics {
                        contentDescription = if (running) {
                            "Clock out. End your current shift."
                        } else {
                            "Clock in. Start tracking your shift."
                        }
                    },
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) accent.copy(alpha = if (dark) .25f else .12f) else accent,
                    contentColor = if (running) accent else Color.White,
                ),
            ) { Text(if (running) "Clock Out" else "Clock In", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MonthSummarySection(
    report: MonthlyReport?,
    paySummary: PayrollCalculator.MonthlyPaySummary?,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSummaryHeader(report)
        MonthSummaryDistributionCard(report)
        MonthSummaryStatsGrid(report)
        MonthSummaryGrossPay(paySummary, currencyCode)
    }
}

@Composable
private fun MonthSummaryHeader(report: MonthlyReport?) {
    val monthName = YearMonth.now().month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElmSectionHeader(title = "$monthName Summary", modifier = Modifier.weight(1f))
        Text(
            text = "${report?.shiftCount ?: 0} shift${if (report?.shiftCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MonthSummaryDistribution(
    report: MonthlyReport?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSummaryHeader(report)
        MonthSummaryDistributionCard(report)
    }
}

@Composable
private fun MonthSummaryDistributionCard(report: MonthlyReport?) {
    val totalMinutes = report?.totalMinutes ?: 0
    val regularMinutes = report?.regularMinutes ?: 0
    val overtimeMinutes = report?.overtimeMinutes ?: 0
    val weekendMinutes = report?.weekendMinutes ?: 0

    ElmCardPadded(modifier = Modifier.auroraEnter(index = 1)) {
            Text(
                text = "HOURS DISTRIBUTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatHoursDecimal(totalMinutes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = " h total",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            DistributionBar(regularMinutes, overtimeMinutes, weekendMinutes)
            Spacer(Modifier.height(10.dp))
            DistributionLegend(regularMinutes, overtimeMinutes, weekendMinutes)
        }
}

@Composable
private fun MonthSummaryStatsGrid(
    report: MonthlyReport?,
    modifier: Modifier = Modifier,
) {
    val totalMinutes = report?.totalMinutes ?: 0
    val regularMinutes = report?.regularMinutes ?: 0
    val overtimeMinutes = report?.overtimeMinutes ?: 0
    val weekendMinutes = report?.weekendMinutes ?: 0
    val isTablet = isTabletLayout()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isTablet) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ElmStatCard(
                    label = "Total",
                    value = "${formatHoursDecimal(totalMinutes)}h",
                    variant = ElmStatVariant.PRIMARY,
                    modifier = Modifier.weight(1f).auroraEnter(index = 1),
                )
                ElmStatCard(
                    label = "Regular",
                    value = "${formatHoursDecimal(regularMinutes)}h",
                    modifier = Modifier.weight(1f).auroraEnter(index = 2),
                )
                ElmStatCard(
                    label = "Overtime",
                    value = "${formatHoursDecimal(overtimeMinutes)}h",
                    variant = ElmStatVariant.OVERTIME,
                    modifier = Modifier.weight(1f).auroraEnter(index = 3),
                )
                ElmStatCard(
                    label = "Weekend",
                    value = "${formatHoursDecimal(weekendMinutes)}h",
                    variant = ElmStatVariant.WEEKEND,
                    modifier = Modifier.weight(1f).auroraEnter(index = 4),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ElmStatCard(
                    label = "Total",
                    value = "${formatHoursDecimal(totalMinutes)}h",
                    variant = ElmStatVariant.PRIMARY,
                    modifier = Modifier.weight(1f).auroraEnter(index = 1),
                )
                ElmStatCard(
                    label = "Regular",
                    value = "${formatHoursDecimal(regularMinutes)}h",
                    modifier = Modifier.weight(1f).auroraEnter(index = 2),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ElmStatCard(
                    label = "Overtime",
                    value = "${formatHoursDecimal(overtimeMinutes)}h",
                    variant = ElmStatVariant.OVERTIME,
                    modifier = Modifier.weight(1f).auroraEnter(index = 3),
                )
                ElmStatCard(
                    label = "Weekend",
                    value = "${formatHoursDecimal(weekendMinutes)}h",
                    variant = ElmStatVariant.WEEKEND,
                    modifier = Modifier.weight(1f).auroraEnter(index = 4),
                )
            }
        }
    }
}

@Composable
private fun MonthSummaryGrossPay(
    paySummary: PayrollCalculator.MonthlyPaySummary?,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    paySummary?.takeIf { it.totalGross > 0.0 }?.let { pay ->
        Box(
            modifier = modifier
                .fillMaxWidth()
                .auroraEnter(index = 3)
                .background(headerGradient, RoundedCornerShape(CornerRadius.Large))
                .border(1.dp, AuroraWhite.copy(alpha = 0.28f), RoundedCornerShape(CornerRadius.Large))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    text = "THIS MONTH - GROSS PAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraWhite.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = MoneyFormatter.format(pay.totalGross, currencyCode),
                        style = MaterialTheme.typography.headlineLarge,
                        color = AuroraWhite,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = " before tax",
                        style = MaterialTheme.typography.bodySmall,
                        color = AuroraWhite.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (pay.overtimeGross > 0.0 || pay.specialGross > 0.0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (pay.regularGross > 0.0) {
                            PaySummaryCell("Regular", pay.regularGross, currencyCode, Modifier.weight(1f))
                        }
                        if (pay.overtimeGross > 0.0) {
                            PaySummaryCell("Overtime", pay.overtimeGross, currencyCode, Modifier.weight(1f))
                        }
                        if (pay.specialGross > 0.0) {
                            PaySummaryCell("Holiday", pay.specialGross, currencyCode, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaySummaryCell(label: String, amount: Double, currencyCode: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AuroraWhite.copy(alpha = 0.15f), RoundedCornerShape(CornerRadius.Medium))
            .padding(10.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AuroraWhite.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = MoneyFormatter.format(amount, currencyCode),
            style = MaterialTheme.typography.bodyMedium,
            color = AuroraWhite,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DistributionBar(regularMinutes: Int, overtimeMinutes: Int, weekendMinutes: Int) {
    val values = listOf(regularMinutes, overtimeMinutes, weekendMinutes)
    val colors = listOf(AuroraIndigo, AuroraPeach, AuroraPlum)
    val total = values.sum()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (total == 0) {
            Spacer(Modifier.weight(1f).fillMaxHeight())
        } else {
            values.forEachIndexed { index, value ->
                if (value > 0) {
                    Box(
                        Modifier
                            .weight(value.toFloat())
                            .fillMaxHeight()
                            .background(colors[index]),
                    )
                }
            }
        }
    }
}

@Composable
private fun DistributionLegend(regularMinutes: Int, overtimeMinutes: Int, weekendMinutes: Int) {
    val items = listOf(
        Triple("Regular", regularMinutes, AuroraIndigo),
        Triple("Overtime", overtimeMinutes, AuroraPeach),
        Triple("Weekend", weekendMinutes, AuroraPlum),
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, minutes, color) ->
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(7.dp).background(color, CircleShape))
                Column {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${formatHoursDecimal(minutes)}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun formatHoursDecimal(minutes: Int): String = "%.1f".format(minutes / 60.0)

// â”€â”€ Recent shifts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun RecentShiftsSection(
    recentShifts: List<Shift>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ElmSectionHeader(title = "Recent Shifts")
        Spacer(Modifier.height(10.dp))
        if (recentShifts.isEmpty()) {
            Text(
                text  = "No completed shifts yet",
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
    val zone         = ZoneId.systemDefault()
    val dateText     = shift.startTime.atZone(zone).format(dateFormatter)
    val startText    = shift.startTime.atZone(zone).format(timeFormatter)
    val endText      = shift.endTime?.atZone(zone)?.format(timeFormatter) ?: "-"
    val durationText = ShiftDurationCalculator.netMinutes(shift)
        ?.let { ShiftDurationCalculator.formatMinutes(it) } ?: "-"

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
                            text  = " Special",
                            style = MaterialTheme.typography.labelSmall,
                            color = auroraWeekendInk(),
                        )
                    }
                }
                Text(
                    text  = "$startText - $endText",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStartTimeDialog(
    currentStartTime: Instant,
    onConfirm: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone        = ZoneId.systemDefault()
    val zonedStart  = currentStartTime.atZone(zone)
    val timePickerState = rememberTimePickerState(
        initialHour   = zonedStart.hour,
        initialMinute = zonedStart.minute,
        is24Hour      = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit start time") },
        text  = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = {
                val newInstant = LocalDateTime.of(
                    zonedStart.toLocalDate(),
                    LocalTime.of(timePickerState.hour, timePickerState.minute),
                ).atZone(zone).toInstant()
                onConfirm(newInstant)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private fun formatElapsedTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

private fun formatInstantTime(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(timeFormatter)

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
        onManageTasks = {},
        showFirstClockInCelebration = false,
        onDismissFirstClockInCelebration = {},
    )
}

@Composable
private fun DashboardError(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text("Error: $message", color = MaterialTheme.colorScheme.error)
    }
}



