package com.elmtrackr.app.ui.reports

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.common.formattedEach
import com.elmtrackr.app.ui.common.formattedEachOrZero
import com.elmtrackr.app.ui.common.labelWithCode
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.common.paidTimeNote
import com.elmtrackr.app.ui.common.PayComponents
import com.elmtrackr.app.ui.common.PayComponentList
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectableGroup
import java.time.LocalDate
import com.elmtrackr.app.ui.common.durationText
import com.elmtrackr.app.ui.design.mirrorInRtl
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.DailyInsight
import com.elmtrackr.app.domain.HoursFormatter
import com.elmtrackr.app.domain.leave.AbsenceListEntry
import com.elmtrackr.app.domain.leave.LeaveQuantityFormat
import com.elmtrackr.app.domain.text.BidiText
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.OvernightShiftDetector
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.TaskMonthlyBreakdown
import com.elmtrackr.app.domain.model.WeeklyTotals
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraScreen
import com.elmtrackr.app.ui.design.AuroraStateCrossfade
import com.elmtrackr.app.ui.design.LocalReduceMotion
import com.elmtrackr.app.ui.layout.isTabletLayout
import com.elmtrackr.app.ui.design.ElmDistributionBar
import com.elmtrackr.app.ui.design.ElmDistributionLegend
import com.elmtrackr.app.ui.design.ElmEmptyState
import com.elmtrackr.app.ui.design.ElmStatCard
import com.elmtrackr.app.ui.design.ElmStatVariant
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraPressScale
import com.elmtrackr.app.ui.design.LocalAppSnackbarHostState
import com.elmtrackr.app.ui.design.showAppMessage
import com.elmtrackr.app.ui.refunds.ReceiptPreviewDialog
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraAquaDeep
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraSuccess
import com.elmtrackr.app.ui.theme.AuroraSuccessDeep
import com.elmtrackr.app.ui.theme.gradientBrush
import com.elmtrackr.app.ui.theme.subTextColor
import com.elmtrackr.app.ui.theme.auroraSemantics
import com.elmtrackr.app.ui.theme.auroraOvertimeBackground
import com.elmtrackr.app.ui.theme.auroraOvertimeInk
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import com.elmtrackr.app.ui.theme.auroraWeekendBackground
import com.elmtrackr.app.ui.tasks.parseTaskColor
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

/** Internal rather than private so [ReportsLaunchRequest] can map onto it. */
internal enum class ReportTab { HOURS, REFUNDS, PROJECTS }

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel(),
    onNavigateToShift: (String) -> Unit = {},
    pendingLaunch: ReportsLaunchRequest? = null,
    onPendingLaunchConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsState()
    val canGoNext by viewModel.canGoNext.collectAsState()
    val ready = uiState as? ReportsUiState.Ready
    var activeTab by rememberSaveable { mutableStateOf(ReportTab.HOURS) }
    val scope = rememberCoroutineScope()
    val appSnackbar = LocalAppSnackbarHostState.current
    val exportFailed = stringResource(R.string.reports_export_failed)
    var receiptPreviewUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val refundsEnabled = ready?.featuresTravelRefunds == true

    LaunchedEffect(refundsEnabled) {
        if (!refundsEnabled && activeTab == ReportTab.REFUNDS) {
            activeTab = ReportTab.HOURS
        }
    }

    // Consumed once and cleared, so returning to this tab later restores the
    // user's own last selection rather than replaying the request. The tab is set
    // without checking whether it is available: [effectiveTab] already falls back
    // to Hours, and the flag it depends on has usually not loaded yet at this
    // point — refusing here would drop a valid request on a cold start.
    LaunchedEffect(pendingLaunch) {
        val launch = pendingLaunch ?: return@LaunchedEffect
        activeTab = launch.toTab()
        onPendingLaunchConsumed()
    }

    // Keyed on the month so each viewed report registers once per visit; the
    // ViewModel filters out the current month and empty months.
    LaunchedEffect(ready?.year, ready?.month, ready != null) {
        val state = ready ?: return@LaunchedEffect
        viewModel.onMonthlyReportViewed(
            year = state.year,
            month = state.month,
            completedShiftCount = state.report.shiftCount,
            zone = state.zone,
        )
    }

    val effectiveTab = when {
        activeTab == ReportTab.REFUNDS && !refundsEnabled -> ReportTab.HOURS
        // Paid Projects switched off: the tab disappears and Hours takes over,
        // so the screen is exactly what an hourly-only user has always seen.
        activeTab == ReportTab.PROJECTS && ready?.projectReport == null -> ReportTab.HOURS
        else -> activeTab
    }

    receiptPreviewUrl?.let { url ->
        ReceiptPreviewDialog(receiptUrl = url, onDismiss = { receiptPreviewUrl = null })
    }

    AuroraScreen {
        val isTablet = isTabletLayout()
        Text(
            stringResource(R.string.reports_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.auroraEnter(),
        )
        if (isTablet) {
            Text(
                stringResource(R.string.reports_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.auroraEnter(index = 1),
            )
        }
        ReportTabs(
            selected = effectiveTab,
            refundsEnabled = refundsEnabled,
            projectsEnabled = ready?.projectReport != null,
            onSelect = { activeTab = it },
        )
        if (effectiveTab == ReportTab.HOURS && ready?.canChooseJob == true) {
            JobScopeSelector(
                state = ready,
                onSelect = viewModel::selectScope,
            )
        }
        val hoursReportVisible = effectiveTab == ReportTab.HOURS &&
            ready?.let { it.report.shiftCount > 0 || it.leaveEntries.isNotEmpty() } == true
        val embedNavInHoursCard = hoursReportVisible
        if (effectiveTab == ReportTab.HOURS && !embedNavInHoursCard) {
            MonthNavigator(
                selectedYearMonth.first,
                selectedYearMonth.second,
                viewModel::previousMonth,
                viewModel::nextMonth,
                canGoNext,
            )
        }

        AuroraStateCrossfade(
            targetState = uiState,
            modifier = Modifier.fillMaxWidth(),
            contentKey = { state ->
                when (state) {
                    ReportsUiState.Loading -> "loading"
                    ReportsUiState.Empty -> "empty"
                    is ReportsUiState.Error -> "error"
                    is ReportsUiState.Ready -> "ready-${state.year}-${state.month}"
                }
            },
        ) { state ->
            when (state) {
                ReportsUiState.Loading -> ReportsSkeleton()
                ReportsUiState.Empty -> ReportsEmptyContent()
                is ReportsUiState.Error -> ErrorState(
                    state.message,
                    onRetry = viewModel::retry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                )
                is ReportsUiState.Ready -> Column(Modifier.fillMaxWidth()) {
                    when (effectiveTab) {
                        ReportTab.REFUNDS -> RefundReview(
                            state = state,
                            viewModel = viewModel,
                            onViewReceipt = { path ->
                                scope.launch {
                                    val url = viewModel.receiptUrl(path)
                                    if (url != null) receiptPreviewUrl = url
                                }
                            },
                            onNavigateToShift = onNavigateToShift,
                        )
                        ReportTab.PROJECTS -> state.projectReport?.let { projectReport ->
                            com.elmtrackr.app.ui.projects.ProjectReportSection(
                                report = projectReport,
                                onExportCsv = {
                                    scope.launch {
                                        runExport(appSnackbar, exportFailed, viewModel::onReportExported) {
                                            ReportExporter.shareCsv(
                                                context,
                                                viewModel.buildProjectCsv(projectReport),
                                                viewModel.projectCsvFilename(projectReport),
                                            )
                                        }
                                    }
                                },
                            )
                        } ?: ReportsEmptyContent()

                        ReportTab.HOURS -> if (state.report.shiftCount == 0 && state.leaveEntries.isEmpty()) {
                            val jobName = state.scopedJobName
                            if (jobName != null && state.allJobsShiftCount > 0) {
                                JobEmptyContent(
                                    jobName = jobName,
                                    year = state.year,
                                    month = state.month,
                                    onShowAllJobs = { viewModel.selectScope(com.elmtrackr.app.domain.ReportScope.AllJobs) },
                                )
                            } else {
                                ReportsEmptyContent()
                            }
                        } else {
                            HoursReport(
                                state = state,
                                onPreviousMonth = viewModel::previousMonth,
                                onNextMonth = viewModel::nextMonth,
                                canGoNext = canGoNext,
                                onExportCsv = {
                                    scope.launch {
                                        runExport(appSnackbar, exportFailed, viewModel::onReportExported) {
                                        ReportExporter.shareCsv(
                                            context,
                                            viewModel.buildCsvContent(
                                                shifts = state.rawShifts,
                                                settings = state.settings,
                                                profiles = state.profiles,
                                                // The same pay-week window the screen
                                                // used, so the export's TOTAL row cannot
                                                // report less overtime than the report it
                                                // was exported from.
                                                contextShifts = state.payContextShifts,
                                                year = state.year,
                                                month = state.month,
                                                leaveEntries = state.leaveEntries,
                                                premiumProfiles = state.premiumProfiles,
                                                scopeJobName = state.scopedJobName,
                                            ),
                                            viewModel.csvFilename(state.year, state.month, state.scopedJobName),
                                        )
                                        }
                                    }
                                },
                                onExportPdf = {
                                    scope.launch {
                                        runExport(appSnackbar, exportFailed, viewModel::onReportExported) {
                                            ReportExporter.shareShiftPdf(context, state)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Tabs ──────────────────────────────────────────────────────────────────────

@Composable
private fun ReportTabs(
    selected: ReportTab,
    refundsEnabled: Boolean,
    projectsEnabled: Boolean,
    onSelect: (ReportTab) -> Unit,
) {
    val shape = RoundedCornerShape(CornerRadius.Large)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .auroraEnter()
            .animateContentSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabButton(stringResource(R.string.reports_tab_hours), selected == ReportTab.HOURS, Modifier.weight(1f)) { onSelect(ReportTab.HOURS) }
        if (projectsEnabled) {
            TabButton(
                stringResource(R.string.reports_tab_projects),
                selected == ReportTab.PROJECTS,
                Modifier.weight(1f),
            ) { onSelect(ReportTab.PROJECTS) }
        }
        if (refundsEnabled) {
            TabButton(stringResource(R.string.reports_tab_refunds), selected == ReportTab.REFUNDS, Modifier.weight(1f)) { onSelect(ReportTab.REFUNDS) }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundBrush = if (selected) {
        Brush.linearGradient(listOf(AuroraIndigo, AuroraPlum, AuroraAqua))
    } else {
        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    Box(
        modifier = modifier
            .auroraPressScale(interactionSource)
            .background(backgroundBrush, RoundedCornerShape(CornerRadius.Medium))
            // selectable rather than clickable so the tab announces its role and
            // its selected state; heightIn brings a 10dp-padded pill up to the
            // 48dp minimum target.
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

// ── Month navigator ───────────────────────────────────────────────────────────

@Composable
private fun MonthNavigator(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit, canGoNext: Boolean) {
    val shape = RoundedCornerShape(CornerRadius.Large)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .auroraEnter(index = 1)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            MonthNavRow(year = year, month = month, onPrev = onPrev, onNext = onNext, canGoNext = canGoNext)
        }
    }
}

@Composable
private fun MonthNavRow(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit, canGoNext: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier
                .size(48.dp)
                .background(auroraSurfaceSub(), RoundedCornerShape(CornerRadius.Small)),
        ) {
            Icon(Icons.Filled.ChevronLeft, stringResource(R.string.shifts_previous_month), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.mirrorInRtl())
        }
        Text(
            "${Month.of(month).displayName(appLocale())} $year",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier
                .size(48.dp)
                .background(auroraSurfaceSub(), RoundedCornerShape(CornerRadius.Small)),
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                stringResource(R.string.shifts_next_month),
                tint = if (canGoNext) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                modifier = Modifier.mirrorInRtl(),
            )
        }
    }
}

@Composable
private fun ReportsEmptyContent() = ElmEmptyState(
    icon = Icons.Filled.Analytics,
    title = stringResource(R.string.reports_empty_title),
    subtitle = stringResource(R.string.reports_empty_subtitle),
    modifier = Modifier.fillMaxWidth(),
)

// ── Hours report ──────────────────────────────────────────────────────────────

/**
 * The month at a glance: what was worked, what it is estimated to have earned, and
 * anything that qualifies those two numbers.
 *
 * It used to open on a "Hours distribution" figure that was payable time under a
 * worked-time label, followed by four stat cards repeating the legend beneath it,
 * with the pay several cards further down. The two questions a person opens a
 * monthly report to answer are now its first two numbers.
 */
@Composable
private fun PhoneHoursReportTop(
    state: ReportsUiState.Ready,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
) {
    val report = state.report
    ReportCard {
        MonthNavRow(year = state.year, month = state.month, onPrev = onPreviousMonth, onNext = onNextMonth, canGoNext = canGoNext)
        Spacer(Modifier.height(Spacing.s12))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.s12))
        ReportOverviewFigures(state)
        if (report.shiftCount > 0) {
            Spacer(Modifier.height(Spacing.s14))
            ElmDistributionBar(report.regularMinutes, report.overtimeMinutes, report.weekendMinutes)
            Spacer(Modifier.height(Spacing.s8))
            ElmDistributionLegend(report.regularMinutes, report.overtimeMinutes, report.weekendMinutes)
        }
    }
}

/**
 * Worked hours and estimated gross side by side, then the lines that qualify them:
 * paid time when the rules changed it, leave kept apart from work, and a note while
 * the month is still running. Shared by the phone and tablet layouts.
 */
@Composable
private fun ReportOverviewFigures(state: ReportsUiState.Ready) {
    val report = state.report
    val locale = appLocale()
    val paid = state.paySummary?.paidByCurrency.orEmpty()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            SectionLabel(stringResource(R.string.paid_time_worked))
            Text(
                stringResource(R.string.shifts_hours_value_spaced, HoursFormatter.decimal(report.workedMinutes, locale)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    stringResource(
                        if (report.shiftCount == 1) R.string.dashboard_shift_count_one else R.string.dashboard_shift_count_other,
                        report.shiftCount,
                    ),
                    state.scopedJobName?.let { BidiText.isolate(it) }
                        ?: stringResource(R.string.reports_scope_all_jobs).takeIf { state.canChooseJob },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (paid.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = Spacing.s8)) {
                SectionLabel(stringResource(R.string.reports_gross_pay_before_tax))
                // One figure per currency, each with its own code when there is more
                // than one. There are no exchange rates here to make a sum honest.
                paid.forEach { totals ->
                    Text(
                        if (paid.size == 1) {
                            MoneyFormatter.format(totals.totalGross, totals.currencyCode, locale)
                        } else {
                            com.elmtrackr.app.ui.common.moneyWithCode(totals.totalGross, totals.currencyCode)
                        },
                        style = if (paid.size == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    ReportOverviewNotes(state)
}

/** The lines that qualify the month's two headline numbers. See [ReportOverviewFigures]. */
@Composable
private fun ReportOverviewNotes(state: ReportsUiState.Ready) {
    val report = state.report
    val locale = appLocale()
    val notes = buildList {
        if (report.payableDiffersFromWorked) {
            paidTimeNote(
                stringResource(R.string.shifts_hours_value_spaced, HoursFormatter.decimal(report.totalMinutes, locale)),
                report.paidTimeReasons,
            )?.let(::add)
        }
        state.paidLeave?.takeIf { !it.isEmpty }?.let { leave ->
            add(stringResource(R.string.reports_leave_line, leaveAmountLabel(leave.vacationDays + leave.sickDays, leave.vacationHours + leave.sickHours)))
        }
        if (report.shiftCount == 0 && state.leaveEntries.isNotEmpty()) {
            add(stringResource(R.string.reports_leave_only))
        }
        val today = LocalDate.now(state.zone)
        if (YearMonth.from(today) == YearMonth.of(state.year, state.month)) {
            add(
                stringResource(
                    R.string.reports_in_progress,
                    today.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                ),
            )
        }
    }
    notes.forEach { note ->
        Spacer(Modifier.height(Spacing.s6))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Picks the job the report covers. Offered only with more than one job; "All jobs"
 * comes first and is the default, so nothing changes for a one-job user.
 */
@Composable
private fun JobScopeSelector(
    state: ReportsUiState.Ready,
    onSelect: (com.elmtrackr.app.domain.ReportScope) -> Unit,
) {
    val groupLabel = stringResource(R.string.reports_scope_group)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = groupLabel }
            .selectableGroup()
            .padding(vertical = Spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        com.elmtrackr.app.ui.design.ElmChoiceChip(
            selected = state.scope == com.elmtrackr.app.domain.ReportScope.AllJobs,
            onClick = { onSelect(com.elmtrackr.app.domain.ReportScope.AllJobs) },
        ) {
            Text(stringResource(R.string.reports_scope_all_jobs), style = MaterialTheme.typography.labelLarge)
        }
        state.profiles.forEach { profile ->
            val scope = com.elmtrackr.app.domain.ReportScope.Job(profile.id)
            com.elmtrackr.app.ui.design.ElmChoiceChip(
                selected = state.scope == scope,
                onClick = { onSelect(scope) },
            ) {
                Text(profile.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** A one-job report with nothing in it, while the other jobs have shifts. */
@Composable
private fun JobEmptyContent(jobName: String, year: Int, month: Int, onShowAllJobs: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        Text(
            stringResource(
                R.string.reports_job_empty_title,
                BidiText.isolate(jobName),
                Month.of(month).displayName(appLocale()),
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.reports_job_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onShowAllJobs) {
            Text(stringResource(R.string.reports_job_empty_action), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * How one currency's estimated pay was put together, from the engine's own buckets.
 *
 * Replaces a card that showed three cells under the total — with weekend and holiday
 * money together labelled "Holiday" and night premium nowhere — so its parts did not
 * add up to the figure above them.
 */
@Composable
private fun PayBreakdownCard(
    totals: PayrollCalculator.CurrencyPayTotals,
    showCurrencyCode: Boolean,
    modifier: Modifier = Modifier,
) {
    ReportCard(modifier) {
        val title = stringResource(R.string.reports_breakdown_title)
        SectionLabel(if (showCurrencyCode) labelWithCode(title, totals.currencyCode) else title)
        Spacer(Modifier.height(Spacing.s10))
        PayComponentList(PayComponents.of(totals))
        Spacer(Modifier.height(Spacing.s10))
        Text(
            stringResource(R.string.pay_estimate_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The steps from worked time to paid time, shown only when the profile's rules
 * made them differ. Each row is a figure the engine actually applied.
 */
@Composable
private fun PaidTimeCard(report: com.elmtrackr.app.domain.model.MonthlyReport) {
    val locale = appLocale()
    fun hours(minutes: Int) = HoursFormatter.decimal(minutes, locale)
    ReportCard {
        SectionLabel(stringResource(R.string.reports_paid_time_title))
        Spacer(Modifier.height(Spacing.s8))
        ReportRow(stringResource(R.string.paid_time_worked), stringResource(R.string.shifts_hours_value_spaced, hours(report.workedMinutes)))
        if (report.paidBreakMinutes > 0) {
            ReportRow(stringResource(R.string.paid_time_paid_break), "+" + stringResource(R.string.shifts_hours_value_spaced, hours(report.paidBreakMinutes)))
        }
        if (report.roundingMinutes != 0) {
            val sign = if (report.roundingMinutes > 0) "+" else "−"
            ReportRow(stringResource(R.string.paid_time_rounding), sign + stringResource(R.string.shifts_hours_value_spaced, hours(kotlin.math.abs(report.roundingMinutes))))
        }
        if (report.minimumTopUpMinutes > 0) {
            ReportRow(stringResource(R.string.paid_time_minimum), "+" + stringResource(R.string.shifts_hours_value_spaced, hours(report.minimumTopUpMinutes)))
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.s4), color = MaterialTheme.colorScheme.outlineVariant)
        ReportRow(stringResource(R.string.paid_time_paid), stringResource(R.string.shifts_hours_value_spaced, hours(report.totalMinutes)))
    }
}

/**
 * What an export will contain, said before the tap: the month, the job scope and
 * the currencies. The buttons used to read "CSV" and "PDF" and nothing else.
 */
@Composable
private fun ExportCard(
    state: ReportsUiState.Ready,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
) {
    val locale = appLocale()
    val monthLabel = "${Month.of(state.month).displayName(locale)} ${state.year}"
    val scopeLabel = state.scopedJobName?.let { BidiText.isolate(it) } ?: stringResource(R.string.reports_scope_all_jobs)
    val currencies = state.paySummary?.paidByCurrency.orEmpty().map { it.currencyCode }
        .plus(state.leaveGrossByCurrency.keys)
        .distinct()
    ReportCard {
        SectionLabel(stringResource(R.string.reports_export_title))
        Spacer(Modifier.height(Spacing.s4))
        Text(
            stringResource(R.string.reports_export_subject, monthLabel, scopeLabel),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        when (currencies.size) {
            0 -> Unit
            1 -> Text(
                stringResource(R.string.reports_export_currency_one, currencies.single()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                stringResource(R.string.reports_export_currency_many, currencies.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.s10))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
            verticalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            OutlinedButton(onClick = onExportCsv) {
                Icon(Icons.Filled.Share, null, Modifier.size(Spacing.s14))
                Spacer(Modifier.width(Spacing.s4))
                Text(stringResource(R.string.reports_export_csv), style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = onExportPdf) {
                Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(Spacing.s14))
                Spacer(Modifier.width(Spacing.s4))
                Text(stringResource(R.string.reports_export_pdf), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TabletHoursReportTop(
    state: ReportsUiState.Ready,
    report: com.elmtrackr.app.domain.model.MonthlyReport,
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
) {
    val totalPct = report.totalMinutes.takeIf { it > 0 }
    val navShape = RoundedCornerShape(CornerRadius.Large)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, navShape),
        shape = navShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            MonthNavRow(year = year, month = month, onPrev = onPreviousMonth, onNext = onNextMonth, canGoNext = canGoNext)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ElmStatCard(
            label = stringResource(R.string.paid_time_worked),
            value = stringResource(R.string.reports_hours_value, HoursFormatter.decimal(report.workedMinutes, appLocale())),
            sub = stringResource(R.string.reports_all_hours_month),
            variant = ElmStatVariant.PRIMARY,
            modifier = Modifier.weight(1f),
        )
        ElmStatCard(
            label = stringResource(R.string.dashboard_stat_regular),
            value = stringResource(R.string.reports_hours_value, HoursFormatter.decimal(report.regularMinutes, appLocale())),
            sub = totalPct?.let { stringResource(R.string.reports_pct_of_total, report.regularMinutes * 100 / it) },
            modifier = Modifier.weight(1f),
        )
        ElmStatCard(
            label = stringResource(R.string.dashboard_stat_overtime),
            value = stringResource(R.string.reports_hours_value, HoursFormatter.decimal(report.overtimeMinutes, appLocale())),
            sub = totalPct?.let { stringResource(R.string.reports_pct_of_total, report.overtimeMinutes * 100 / it) },
            variant = ElmStatVariant.OVERTIME,
            modifier = Modifier.weight(1f),
        )
        ElmStatCard(
            label = stringResource(R.string.dashboard_stat_weekend),
            value = stringResource(R.string.reports_hours_value, HoursFormatter.decimal(report.weekendMinutes, appLocale())),
            sub = totalPct?.let { stringResource(R.string.reports_pct_of_total, report.weekendMinutes * 100 / it) },
            variant = ElmStatVariant.WEEKEND,
            modifier = Modifier.weight(1f),
        )
    }

    ReportOverviewNotes(state)

    Spacer(Modifier.height(Spacing.s16))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s16),
        verticalAlignment = Alignment.Top,
    ) {
        val paid = state.paySummary?.paidByCurrency.orEmpty()
        if (paid.isNotEmpty()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                paid.forEach { totals -> PayBreakdownCard(totals, showCurrencyCode = paid.size > 1) }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        ReportCard(Modifier.weight(1f)) {
            DistributionHeader(report)
            Spacer(Modifier.height(Spacing.s14))
            ElmDistributionBar(report.regularMinutes, report.overtimeMinutes, report.weekendMinutes)
            Spacer(Modifier.height(8.dp))
            ElmDistributionLegend(report.regularMinutes, report.overtimeMinutes, report.weekendMinutes)
        }
    }

    state.insights?.let { insights ->
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (state.previousMonthMinutes > 0) {
                val delta = report.workedMinutes - state.previousMonthMinutes
                val pctChange = if (state.previousMonthMinutes > 0) {
                    (delta * 100 / state.previousMonthMinutes)
                } else 0
                val prevMonthName = YearMonth.of(state.year, state.month).minusMonths(1)
                    .month.getDisplayName(JavaTextStyle.FULL, appLocale())
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .background(
                            Brush.linearGradient(listOf(AuroraSuccess, AuroraSuccessDeep)),
                            RoundedCornerShape(CornerRadius.Large),
                        )
                        .padding(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.reports_peak_performer),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            when {
                                pctChange > 0 -> stringResource(R.string.reports_peak_more, pctChange, prevMonthName)
                                pctChange < 0 -> stringResource(R.string.reports_peak_fewer, abs(pctChange), prevMonthName)
                                else -> stringResource(R.string.reports_peak_same, prevMonthName)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1.6f))
            }

            InsightStatCard(
                icon = "⏱",
                label = stringResource(R.string.reports_avg_shift_length),
                value = if (insights.averageShiftMinutes > 0) {
                    durationText(insights.averageShiftMinutes)
                } else "—",
                sub = stringResource(R.string.reports_per_shift),
                accentColor = AuroraIndigo,
                bgColor = AuroraIndigo.copy(alpha = 0.10f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun HoursReport(
    state: ReportsUiState.Ready,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
) {
    val report = state.report
    val isTablet = isTabletLayout()

    Column(Modifier.fillMaxWidth()) {
    if (isTablet) {
        TabletHoursReportTop(
            state = state,
            report = report,
            year = state.year,
            month = state.month,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            canGoNext = canGoNext,
        )
    } else {
        PhoneHoursReportTop(
            state = state,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            canGoNext = canGoNext,
        )
    }

    // ── How the pay was estimated (phone only — tablet shows it in its top row) ──
    if (!isTablet) {
        val paid = state.paySummary?.paidByCurrency.orEmpty()
        paid.forEach { totals ->
            Spacer(Modifier.height(Spacing.s14))
            PayBreakdownCard(totals, showCurrencyCode = paid.size > 1)
        }
    }

    if (report.payableDiffersFromWorked) {
        Spacer(Modifier.height(Spacing.s14))
        PaidTimeCard(report)
    }

    if (state.leaveEntries.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.s14))
        LeaveReportSection(state)
    }

    Spacer(Modifier.height(Spacing.s14))
    ExportCard(state, onExportCsv = onExportCsv, onExportPdf = onExportPdf)

    if (!isTablet && state.previousMonthMinutes > 0) {
        val delta = report.workedMinutes - state.previousMonthMinutes
        val deltaColor = when {
            delta > 0 -> auroraSemantics.successInk
            delta < 0 -> Color(0xFFF43F5E)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val arrow = when {
            delta > 0 -> "↑"
            delta < 0 -> "↓"
            else -> "—"
        }
        val prevMonthName = YearMonth.of(state.year, state.month).minusMonths(1)
            .month.getDisplayName(JavaTextStyle.SHORT, appLocale())
        Spacer(Modifier.height(14.dp))
        ReportCard {
            Text(stringResource(R.string.reports_month_over_month), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Both sides are sentences carrying a localized month name and
                // neither could shrink. Row measures unweighted children in order
                // with the width left over, so the first starves the second; the
                // weight makes the comparison measure first and keeps the header
                // yielding. Not reproducible in the JVM harness — see the note on
                // SettingsInfoRow.
                Text(
                    stringResource(R.string.reports_hours_this_month, HoursFormatter.decimal(report.workedMinutes, appLocale())),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    stringResource(R.string.reports_vs_prev, arrow, HoursFormatter.decimal(abs(delta), appLocale()), prevMonthName),
                    color = deltaColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Spacing.s8),
                )
            }
        }
    }

    if (isTablet && state.previousMonthMinutes > 0) {
        val delta = report.totalMinutes - state.previousMonthMinutes
        val deltaColor = when {
            delta > 0 -> auroraSemantics.successInk
            delta < 0 -> Color(0xFFF43F5E)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val arrow = when {
            delta > 0 -> "↑"
            delta < 0 -> "↓"
            else -> "—"
        }
        val prevMonthName = YearMonth.of(state.year, state.month).minusMonths(1)
            .month.getDisplayName(JavaTextStyle.SHORT, appLocale())
        Spacer(Modifier.height(14.dp))
        ReportCard {
            Text(stringResource(R.string.reports_month_over_month), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Both sides are sentences carrying a localized month name and
                // neither could shrink. Row measures unweighted children in order
                // with the width left over, so the first starves the second; the
                // weight makes the comparison measure first and keeps the header
                // yielding. Not reproducible in the JVM harness — see the note on
                // SettingsInfoRow.
                Text(
                    stringResource(R.string.reports_hours_this_month, HoursFormatter.decimal(report.workedMinutes, appLocale())),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    stringResource(R.string.reports_vs_prev, arrow, HoursFormatter.decimal(abs(delta), appLocale()), prevMonthName),
                    color = deltaColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Spacing.s8),
                )
            }
        }
    }

    // ── Daily Insights carousel (web: features_insights) ──────────────────────
    if (state.settings?.featuresInsights == false) {
        Spacer(Modifier.height(18.dp))
        ReportCard {
            Text(
                stringResource(R.string.reports_insights_off_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.reports_insights_off_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else if (state.dailyInsights.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        InsightOfTheDay(state.dailyInsights)
    }

    // ── Shift Insights / QuickStats (web: 6-card grid) ────────────────────────
    if (!isTablet) {
    state.insights?.let { insights ->
        // The insights' own currency: the average and the best shift are only computed
        // when the month was paid in one currency, and this is that one.
        val currency2 = insights.payCurrencyCode ?: state.settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name
        val payUnavailableSub = if (insights.payInMultipleCurrencies) {
            stringResource(R.string.reports_pay_multiple_currencies)
        } else {
            stringResource(R.string.reports_set_rate_unlock)
        }
        Spacer(Modifier.height(18.dp))
        ElmSectionHeader(stringResource(R.string.reports_shift_insights))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightStatCard(
                icon = "🌅",
                label = stringResource(R.string.reports_weekend_shifts),
                value = insights.weekendShiftCount.toString(),
                sub = stringResource(if (insights.weekendShiftCount == 1) R.string.reports_shift_one else R.string.reports_shift_other),
                accentColor = AuroraPlum,
                bgColor = auroraWeekendBackground(),
                modifier = Modifier.weight(1f),
            )
            InsightStatCard(
                icon = "⏱",
                label = stringResource(R.string.reports_avg_shift_length),
                value = if (insights.averageShiftMinutes > 0) {
                    durationText(insights.averageShiftMinutes)
                } else "—",
                sub = stringResource(R.string.reports_per_shift),
                accentColor = AuroraIndigo,
                bgColor = AuroraIndigo.copy(alpha = 0.10f),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightStatCard(
                icon = "🔥",
                label = stringResource(R.string.reports_overtime_shifts),
                value = insights.overtimeShiftCount.toString(),
                sub = stringResource(if (insights.overtimeShiftCount == 1) R.string.reports_shift_one else R.string.reports_shift_other),
                // accentColor is this card's value text as well as its border, so it
                // takes the ink; the border reads fine at either.
                accentColor = auroraOvertimeInk(),
                bgColor = auroraOvertimeBackground(),
                modifier = Modifier.weight(1f),
            )
            InsightStatCard(
                icon = "📏",
                label = stringResource(R.string.reports_longest_shift),
                value = durationText(insights.longestShiftMinutes),
                sub = insights.longestShift?.startTime?.atZone(state.zone)
                    ?.format(DateTimeFormatter.ofPattern("MMM d", appLocale())),
                accentColor = auroraSemantics.successInk,
                bgColor = auroraSemantics.success.copy(alpha = 0.10f),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightStatCard(
                icon = "💰",
                label = stringResource(R.string.reports_avg_pay_shift),
                value = if (insights.avgPayPerShift != null) MoneyFormatter.format(insights.avgPayPerShift, currency2, appLocale()) else "—",
                sub = if (insights.avgPayPerShift != null) stringResource(R.string.reports_per_shift) else payUnavailableSub,
                accentColor = AuroraIndigo,
                bgColor = AuroraIndigo.copy(alpha = 0.10f),
                modifier = Modifier.weight(1f),
            )
            InsightStatCard(
                icon = "🏆",
                label = stringResource(R.string.reports_best_shift),
                value = if (insights.highestEarningAmount != null) MoneyFormatter.format(insights.highestEarningAmount, currency2, appLocale()) else "—",
                sub = insights.highestEarningShift?.startTime?.atZone(state.zone)
                    ?.format(DateTimeFormatter.ofPattern("MMM d", appLocale()))
                    ?: if (insights.highestEarningAmount == null) payUnavailableSub else null,
                accentColor = AuroraPlum,
                bgColor = auroraWeekendBackground(),
                modifier = Modifier.weight(1f),
            )
        }
    }
    }

    // ── Weekly breakdown ──────────────────────────────────────────────────────
    if (state.weeklyTotals.isNotEmpty()) {
        val hasPrevData = state.weeklyTotals.any { it.prevMonthMinutes > 0 }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ElmSectionHeader(stringResource(R.string.reports_weekly_breakdown))
            if (hasPrevData) {
                val prevMonthName = YearMonth.of(state.year, state.month).minusMonths(1)
                    .let { Month.of(it.monthValue).displayName(appLocale()) }
                Text(
                    stringResource(R.string.reports_vs_month, prevMonthName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ReportCard {
            val maxMinutes = state.weeklyTotals.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(1) ?: 1
            state.weeklyTotals.forEachIndexed { index, week ->
                if (index > 0) HorizontalDivider(
                    Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                WeekRow(week, maxMinutes, state.settings, rowIndex = index)
            }
        }
    }

    if (state.taskBreakdown.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        ElmSectionHeader(stringResource(R.string.reports_by_task))
        Spacer(Modifier.height(8.dp))
        ReportCard {
            state.taskBreakdown.forEachIndexed { index, task ->
                if (index > 0) HorizontalDivider(
                    Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                TaskBreakdownRow(task, state.settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name)
            }
        }
    }

    // ── OT thresholds footnote (web: inline with hairline divider) ────────────
    state.settings?.takeIf { state.rawShifts.isNotEmpty() }?.let { settings ->
        Spacer(Modifier.height(12.dp))
        OtThresholdFootnote(settings, state.profiles)

        // ── Shift breakdown ───────────────────────────────────────────────────
        Spacer(Modifier.height(18.dp))
        ElmSectionHeader(stringResource(R.string.reports_shift_breakdown))
        Spacer(Modifier.height(8.dp))
        val shape = RoundedCornerShape(CornerRadius.Large)
        Card(
            Modifier
                .fillMaxWidth()
                .auroraEnter()
                .shadow(8.dp, shape, clip = false,
                    ambientColor = AuroraIndigo.copy(alpha = 0.04f),
                    spotColor = AuroraIndigo.copy(alpha = 0.28f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            state.rawShifts.sortedByDescending { it.startTime }.forEachIndexed { index, shift ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ShiftReportRow(shift, settings, state.profiles, state.rawShifts, state.premiumProfiles, state.zone)
            }
        }
    }
    } // Column
}

// ── Insights carousel ─────────────────────────────────────────────────────────

@Composable
private fun InsightOfTheDay(insights: List<DailyInsight>) {
    // Use a plain Row + horizontalScroll instead of LazyRow.
    // LazyRow inside a verticalScroll Column can report height = 0 to the parent
    // layout, causing the sections below to collapse upward and overlap the carousel.
    // A regular Row always reports the correct measured height.
    val scrollState = rememberScrollState()
    val dotScope = rememberCoroutineScope()
    val gapDp = 12.dp
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth * 0.88f
        val cardStridePx = with(density) { (cardWidth + gapDp).toPx() }

        // Derive active index from scroll position (snap to nearest card)
        val activeIdx = remember(scrollState.value, cardStridePx) {
            if (cardStridePx > 0f) {
                ((scrollState.value + cardStridePx / 2) / cardStridePx)
                    .toInt()
                    .coerceIn(0, insights.lastIndex)
            } else 0
        }

        Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 200.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ElmSectionHeader(stringResource(R.string.reports_insights_of_day), modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.reports_swipe_daily),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(gapDp),
            ) {
                insights.forEachIndexed { i, insight ->
                    InsightCard(
                        insight = insight,
                        index = i,
                        total = insights.size,
                        cardWidth = cardWidth,
                        isActive = i == activeIdx,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            // Pagination dots
            if (insights.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    insights.forEachIndexed { i, _ ->
                        val isActive = i == activeIdx
                        val dotWidth by animateFloatAsState(if (isActive) 20f else 6f, label = "dot-$i")
                        // The 6dp dot is the visual; the tap target around it is
                        // the 48dp minimum. Previously the dot itself was the
                        // target, which is smaller than a fingertip.
                        Box(
                            modifier = Modifier
                                .selectable(
                                    selected = isActive,
                                    role = Role.Tab,
                                    onClick = {
                                        dotScope.launch {
                                            scrollState.scrollTo((i * cardStridePx).toInt())
                                        }
                                    },
                                )
                                .minimumInteractiveComponentSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .height(6.dp)
                                    .width(dotWidth.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isActive) AuroraIndigo else MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: DailyInsight, index: Int, total: Int, cardWidth: Dp, isActive: Boolean) {
    Box(
        modifier = Modifier
            .width(cardWidth)
            .background(insight.color.gradientBrush(), RoundedCornerShape(CornerRadius.Large))
            .padding(20.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.reports_index_of, index + 1, total),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Text(
                    if (isActive) stringResource(R.string.reports_today_dot) else "○",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = if (isActive) 0.4f else 0.25f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(insight.icon, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text(
                insight.title.asString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildBoldAnnotatedString(insight.text.asString(), insight.color.subTextColor()),
                style = MaterialTheme.typography.bodySmall,
                color = insight.color.subTextColor(),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Parses **bold** markers into AnnotatedString with white bold spans. */
private fun buildBoldAnnotatedString(text: String, baseColor: Color) = buildAnnotatedString {
    // String.split() in Java/Kotlin does NOT include captured groups in the result —
    // only the text between matches. We use findAll() instead so bold segments
    // are never silently dropped from the rendered card text.
    val boldPattern = Regex("""\*\*([^*]+)\*\*""")
    var lastEnd = 0
    for (match in boldPattern.findAll(text)) {
        // Plain text before this bold segment
        if (match.range.first > lastEnd) {
            append(text.substring(lastEnd, match.range.first))
        }
        // Bold text — groupValues[1] is the content without the ** delimiters
        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
            append(match.groupValues[1])
        }
        lastEnd = match.range.last + 1
    }
    // Remaining plain text after the last bold segment
    if (lastEnd < text.length) {
        append(text.substring(lastEnd))
    }
}

// ── Insight stat card (6-cell QuickStats) ────────────────────────────────────

@Composable
private fun InsightStatCard(
    icon: String,
    label: String,
    value: String,
    sub: String?,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(CornerRadius.Medium)
    Box(
        modifier
            .auroraEnter()
            .shadow(4.dp, shape, clip = false,
                ambientColor = accentColor.copy(alpha = 0.03f),
                spotColor = accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.18f), shape)
            .background(bgColor, shape)
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Task breakdown row ─────────────────────────────────────────────────────────

@Composable
private fun TaskBreakdownRow(task: TaskMonthlyBreakdown, currency: String) {
    val tint = parseTaskColor(task.color)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tint != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tint),
                )
                Spacer(Modifier.width(8.dp))
            }
            // Task names are user-entered and unbounded, and this was the only
            // title in this file without maxLines. Left to wrap, the hours value —
            // centred against the row — floats to the middle of the wrapped block,
            // which is the defect the dashboard's report link had. The start padding
            // guarantees a gap the arrangement alone does not.
            Text(
                "${task.icon.orEmpty()} ${task.name}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.reports_hours_value, HoursFormatter.decimal(task.totalMinutes, appLocale())),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.s8),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // weight(fill = false) so the shift-count line yields instead of
            // starving the pay figure of width. Neither side could shrink before.
            Text(
                stringResource(R.string.reports_task_shifts_avg, task.shiftCount, HoursFormatter.decimal(task.averageShiftMinutes, appLocale())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            task.totalPay?.let { pay ->
                Text(
                    pay.formattedEachOrZero(currency),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.s8),
                )
            }
        }
        if (task.overtimeMinutes > 0) {
            Text(
                stringResource(R.string.reports_ot_hours, HoursFormatter.decimal(task.overtimeMinutes, appLocale())),
                style = MaterialTheme.typography.labelSmall,
                color = auroraOvertimeInk(),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ── Weekly breakdown row ──────────────────────────────────────────────────────

@Composable
private fun WeekRow(week: WeeklyTotals, maxMinutes: Int, settings: UserSettings?, rowIndex: Int) {
    val pct = if (maxMinutes > 0) (week.totalMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f) else 0f
    val reduceMotion = LocalReduceMotion.current
    val animatedPct by animateFloatAsState(
        targetValue = pct,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            tween(durationMillis = 700, delayMillis = rowIndex * 40, easing = AuroraEaseOut)
        },
        label = "bar-${week.label}",
    )
    val showDelta = week.prevMonthMinutes > 0
    val delta = week.totalMinutes - week.prevMonthMinutes

    Column {
        // Header row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.reports_week_label, week.label.filter { it.isDigit() }),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.reports_week_days, week.dayRange),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showDelta) {
                    val deltaColor = when {
                        delta > 0 -> auroraSemantics.successInk
                        delta < 0 -> Color(0xFFF43F5E)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val arrow = when {
                        delta > 0 -> "↑"
                        delta < 0 -> "↓"
                        else -> "—"
                    }
                    Text(
                        "$arrow ${durationText(abs(delta))}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (week.totalMinutes > 0) stringResource(R.string.reports_hours_value, HoursFormatter.decimal(week.totalMinutes, appLocale())) else "—",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // Progress bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedPct)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(AuroraIndigo, AuroraAqua))),
            )
        }

        // OT indicator + pay sub-row
        val showSubRow = week.overtimeMinutes > 0 || week.pay?.isNotEmpty == true
        if (showSubRow) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (week.overtimeMinutes > 0) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(AuroraPeach))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.reports_ot_suffix, durationText(week.overtimeMinutes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroraPeach,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                week.pay?.takeIf { it.isNotEmpty }?.let { pay ->
                    if (week.overtimeMinutes > 0) Spacer(Modifier.width(12.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        // In each shift's own currency, never one figure for two.
                        pay.formattedEach(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AuroraIndigo,
                    )
                }
            }
        }
    }
}

// ── Shift report row with left stripe ────────────────────────────────────────

/** The per-row figures [ShiftReportRow] derives from the pay engine, remembered as one unit. */
private data class ShiftReportRowPay(
    val breakdown: com.elmtrackr.app.domain.model.ShiftBreakdown,
    val weekend: Boolean,
    val overnight: Boolean,
    val pay: com.elmtrackr.app.domain.model.ShiftPayBreakdown?,
    val otMinutes: Int,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftReportRow(
    shift: Shift,
    settings: UserSettings,
    profiles: List<CompensationProfile> = emptyList(),
    allShifts: List<Shift> = emptyList(),
    premiumProfiles: List<PremiumProfile> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    // Remembered because `calculateShiftPayInContext` is not a cheap read: it walks
    // this shift's pay week, and in the Israeli engine it re-classifies every prior
    // shift of that week minute by minute. Computed inline it ran again on every
    // recomposition of every visible row, on the main thread — the ViewModel moved the
    // month's payroll to a computation dispatcher for exactly this cost, and these
    // per-row calls sat outside that.
    val rowPay = remember(shift, settings, profiles, allShifts, premiumProfiles, zone) {
        // `allShifts` here is the pay-week window the screen was built with, the
        // same one the row's pay is priced against below — so the row's hours
        // and its money describe the same week.
        val breakdown = MonthlyReportBuilder.buildShiftBreakdown(
            shift, settings, profiles, premiumProfiles, allShifts,
        )
        ShiftReportRowPay(
            breakdown = breakdown,
            weekend = CompensationResolver.isWeekendShift(shift, settings, profiles),
            overnight = OvernightShiftDetector.isOvernight(shift, zone),
            pay = PayrollCalculator.calculateShiftPayInContext(
                shift,
                allShifts.ifEmpty { listOf(shift) },
                settings,
                profiles,
                premiumProfiles,
            ),
            // The classifier's overtime — the minutes the pay engine actually paid at
            // an overtime rate. This used to be clock time less breaks minus the raw
            // daily standard: a third overtime rule, blind to the shorter standard on
            // a night shift or before rest, to weekly overtime, and to rounding.
            otMinutes = breakdown.overtimeMinutes,
        )
    }
    val breakdown = rowPay.breakdown
    val date = shift.startTime.atZone(zone).toLocalDate()
    val weekend = rowPay.weekend
    val overnight = rowPay.overnight
    val pay = rowPay.pay
    val otMins = rowPay.otMinutes

    // Stripe color logic — matches web app
    val stripeColor = when {
        shift.isSpecialDay || weekend -> AuroraPlum
        overnight -> AuroraIndigo
        else -> AuroraIndigo.copy(alpha = 0.35f)
    }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        // Left stripe — fillMaxHeight() + IntrinsicSize.Max ensures it spans the full row
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(stripeColor),
        )

        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        shift.startTime.atZone(zone).format(DateTimeFormatter.ofPattern("EEE, MMM d", appLocale())),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val breakStr = if (shift.breakMinutes > 0) stringResource(R.string.reports_break_suffix, shift.breakMinutes) else ""
                    Text(
                        shift.startTime.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")) +
                            " — " +
                            (shift.endTime?.atZone(zone)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "") +
                            breakStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val tags = listOfNotNull(
                        if (shift.isSpecialDay) stringResource(R.string.dashboard_pay_holiday) else null,
                        if (weekend && !shift.isSpecialDay) stringResource(R.string.reports_chip_weekend) else null,
                        if (overnight) stringResource(R.string.reports_chip_overnight) else null,
                    )
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tags.forEach { tag ->
                                ShiftTagPill(tag, shift.isSpecialDay || weekend)
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    // Worked time first; paid time beneath it only where the rules
                    // changed it, so rounded time is never shown as time worked.
                    Text(durationText(breakdown.workedMinutes), fontWeight = FontWeight.Bold)
                    if (breakdown.totalMinutes != breakdown.workedMinutes) {
                        Text(
                            stringResource(R.string.shifts_time_fact, stringResource(R.string.paid_time_paid), durationText(breakdown.totalMinutes)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (otMins > 0) {
                        Text(
                            stringResource(R.string.reports_plus_ot, durationText(otMins)),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroraPeach,
                        )
                    }
                    pay?.let {
                        Text(
                            MoneyFormatter.format(it.totalGross, it.currencyCode, appLocale()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AuroraIndigo,
                        )
                    }
                }
            }
        }
    }
}

// ── Refund review ─────────────────────────────────────────────────────────────

@Composable
private fun RefundReview(
    state: ReportsUiState.Ready,
    viewModel: ReportsViewModel,
    onViewReceipt: (String) -> Unit,
    onNavigateToShift: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appSnackbar = LocalAppSnackbarHostState.current
    val exportFailed = stringResource(R.string.reports_export_failed)
    val activeShiftIds = remember(state.allShifts) { state.allShifts.map { it.id }.toSet() }
    val claims = remember(state.refundClaims, activeShiftIds) {
        state.refundClaims.filter { it.shiftId in activeShiftIds }
    }
    val reimbursableShifts = remember(state.allShifts, claims) {
        val claimShiftIds = claims.map { it.shiftId }.toSet()
        state.allShifts.filter { shift ->
            shift.isCompleted && (shift.refundAction != null || shift.id in claimShiftIds)
        }
    }
    // Refund claims carry no currency of their own: they are entered in the account's
    // currency. The month's wage currency is a different fact and, in a month worked in
    // two currencies, has no single answer.
    val currency = state.settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name
    val allMonthsLabel = stringResource(R.string.reports_all_months)
    var exportingAll by rememberSaveable { mutableStateOf(false) }
    if (reimbursableShifts.isEmpty()) {
        ElmEmptyState(
            Icons.Filled.PictureAsPdf,
            stringResource(R.string.reports_no_reimb_title),
            stringResource(R.string.reports_no_reimb_body),
            Modifier.fillMaxWidth(),
        )
        return
    }
    if (claims.isNotEmpty()) RefundAnalytics(claims, state.allShifts, state.settings, state.profiles, state.premiumProfiles, state.zone)
    // Remembered: this walks every shift ever recorded against every claim, and
    // it only changes when one of those lists does.
    val exportRows = remember(state.allShifts, claims) {
        submittedExportRows(state.allShifts, claims)
    }
    if (exportRows.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                exportingAll = true
                scope.launch {
                    try {
                        val pdfRows = exportRows.map { (shift, claim) -> RefundPdfRow(shift, claim) }
                        ReportExporter.shareRefundPdf(
                            context = context,
                            rows = pdfRows,
                            filenameSuffix = "all-months",
                            periodLabel = allMonthsLabel,
                            currency = currency,
                            zone = state.zone,
                        ) { row ->
                            row.claim.receiptPath
                                ?.let { viewModel.receiptUrl(it) }
                                ?.let { ReportExporter.loadReceipt(context, it) }
                        }
                        viewModel.onReportExported()
                    } catch (e: Exception) {
                        // finally already clears the spinner; without this the user saw
                        // it stop with no file and no reason.
                        com.elmtrackr.app.monitoring.CrashReporting.report(e)
                        appSnackbar.showAppMessage(exportFailed)
                    } finally { exportingAll = false }
                }
            },
            enabled = !exportingAll,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (exportingAll) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(if (exportingAll) stringResource(R.string.reports_preparing_reimb_pdf) else stringResource(R.string.reports_export_all_receipts))
        }
    }
    val remindLater = reimbursableShifts.count { it.refundAction == RefundAction.REMIND_LATER }
    if (remindLater > 0) {
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(CornerRadius.Medium),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(stringResource(if (remindLater == 1) R.string.reports_reminder_one else R.string.reports_reminder_other, remindLater), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(stringResource(R.string.reports_open_shift_submit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
    Spacer(Modifier.height(18.dp)); ElmSectionHeader(stringResource(R.string.reports_reimb_history)); Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.reports_showing_months, monthsLabelCount(reimbursableShifts, state.zone)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    // One grouping pass instead of a nested scan, and remembered so it does not
    // re-run on every recomposition of this screen. The previous form called
    // atZone once per shift per month — O(months × shifts) ZonedDateTime
    // allocations for a list that had not changed.
    val shiftsByMonth = remember(reimbursableShifts, state.zone) {
        reimbursableShifts
            .groupBy { YearMonth.from(it.startTime.atZone(state.zone)) }
            .toSortedMap(compareByDescending { it })
    }
    val claimsByMonth = remember(shiftsByMonth, claims) {
        val monthOfShift = shiftsByMonth.entries
            .flatMap { (month, shifts) -> shifts.map { it.id to month } }
            .toMap()
        claims.groupBy { monthOfShift[it.shiftId] }
    }
    // Only a window of months is composed. This list is every reimbursable shift
    // the user has ever recorded, and it lives inside a verticalScroll — which
    // composes and measures every child whether or not it is on screen. After a
    // few years that is thousands of rows built before the first frame, and
    // rebuilt on every recomposition of this screen.
    //
    // A window rather than a LazyColumn because the refunds tab is one section of
    // a scrolling page, and a lazy list nested in a vertical scroller has no
    // bounded height to work with.
    var visibleMonths by rememberSaveable { mutableIntStateOf(INITIAL_REFUND_MONTHS) }
    val monthsToShow = shiftsByMonth.entries.take(visibleMonths)
    val hiddenMonths = shiftsByMonth.size - monthsToShow.size

    monthsToShow.forEach { (month, monthShifts) ->
        RefundMonthCard(
            month = month,
            shifts = monthShifts,
            claims = claimsByMonth[month].orEmpty(),
            onViewReceipt = onViewReceipt,
            onNavigateToShift = onNavigateToShift,
            onExport = { rows, onDone ->
                scope.launch {
                    try {
                        val pdfRows = rows.map { (shift, claim) -> RefundPdfRow(shift, claim) }
                        ReportExporter.shareRefundPdf(
                            context = context,
                            rows = pdfRows,
                            year = month.year,
                            month = month.monthValue,
                            currency = currency,
                            zone = state.zone,
                        ) { row ->
                            row.claim.receiptPath
                                ?.let { viewModel.receiptUrl(it) }
                                ?.let { ReportExporter.loadReceipt(context, it) }
                        }
                        viewModel.onReportExported()
                    } catch (e: Exception) {
                        com.elmtrackr.app.monitoring.CrashReporting.report(e)
                        appSnackbar.showAppMessage(exportFailed)
                    } finally { onDone() }
                }
            },
            currency = currency,
            zone = state.zone,
        )
        Spacer(Modifier.height(12.dp))
    }

    if (hiddenMonths > 0) {
        TextButton(
            onClick = { visibleMonths += REFUND_MONTHS_STEP },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Layout.minTouchTarget),
        ) {
            Text(stringResource(R.string.reports_show_earlier_months, hiddenMonths))
        }
    }
}

/** Months of refund history composed before the user asks for more. */
private const val INITIAL_REFUND_MONTHS = 3
private const val REFUND_MONTHS_STEP = 6

@Composable
private fun RefundAnalytics(
    claims: List<RefundClaim>,
    shifts: List<Shift>,
    settings: UserSettings?,
    profiles: List<CompensationProfile> = emptyList(),
    premiumProfiles: List<PremiumProfile> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val total = claims.sumOf { it.amount }
    val currency = settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name
    // Same reason as ShiftReportRow: sumMonthlyPay walks every completed shift of the
    // month through the pay engine, and this ran on each recomposition of the card.
    val salary = remember(shifts, settings, profiles, premiumProfiles, currency) {
        settings?.let { s ->
            val pay = PayrollCalculator
                .sumMonthlyPay(shifts.filter { it.isCompleted }, s, profiles, premiumProfiles)
                .grossPay
            // "Salary plus refunds" is one figure, so only when every wage was paid in the
            // currency the refunds are in. Otherwise the two cannot be added and the line
            // is left out rather than printed under the refunds' currency.
            pay.singleOrNull()?.takeIf { (code, _) -> code == currency }?.second
        }
    }
    Card(shape = RoundedCornerShape(CornerRadius.Large), colors = CardDefaults.cardColors(containerColor = AuroraIndigo)) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.reports_total_refunded), style = MaterialTheme.typography.labelSmall, color = Color(0xffd8d3ff), fontWeight = FontWeight.Bold)
            Text(MoneyFormatter.format(total, currency, appLocale()), style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
            val activeMonths = claims.map { YearMonth.from(it.rideAt.atZone(zone)) }.distinct().size
            Text(stringResource(R.string.reports_rides_months, claims.size, activeMonths), color = Color(0xffd8d3ff), style = MaterialTheme.typography.bodySmall)
            if (salary != null && salary > 0) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = .2f))
                Text(stringResource(R.string.reports_total_compensation), style = MaterialTheme.typography.labelSmall, color = Color(0xffd8d3ff))
                Text(MoneyFormatter.format(salary + total, currency, appLocale()), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    ReportCard {
        SectionLabel(stringResource(R.string.reports_by_provider))
        claims.groupBy { it.provider }.entries.sortedByDescending { it.value.sumOf(RefundClaim::amount) }.forEach { (provider, rows) ->
            val amount = rows.sumOf { it.amount }
            Spacer(Modifier.height(8.dp))
            ReportRow(stringResource(R.string.reports_provider_rides, provider.name.lowercase().replaceFirstChar(Char::uppercase), rows.size), MoneyFormatter.format(amount, currency, appLocale()))
            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth((amount / total).toFloat().coerceIn(0f, 1f)).height(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(stringResource(R.string.reports_avg_per_ride), MoneyFormatter.format(total / claims.size, currency, appLocale()), AuroraIndigo, Modifier.weight(1f))
        StatCard(stringResource(R.string.reports_largest_ride), MoneyFormatter.format(claims.maxOf { it.amount }, currency, appLocale()), AuroraPeach, Modifier.weight(1f))
    }
}

@Composable
private fun RefundMonthCard(
    month: YearMonth,
    shifts: List<Shift>,
    claims: List<RefundClaim>,
    onViewReceipt: (String) -> Unit,
    onNavigateToShift: (String) -> Unit,
    onExport: (List<Pair<Shift, RefundClaim>>, () -> Unit) -> Unit,
    currency: String,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val submitted = shifts.count { it.refundAction == RefundAction.SUBMITTED }
    val pending = shifts.count { it.refundAction == null }
    val exportRows = submittedExportRows(shifts, claims)
    var isExporting by rememberSaveable(month.toString()) { mutableStateOf(false) }
    ReportCard {
        Text("${month.month.displayName(appLocale())} ${month.year}", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(stringResource(R.string.reports_submitted), submitted.toString(), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
            StatPill(stringResource(R.string.reports_pending), pending.toString(), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(1f))
            StatPill(stringResource(R.string.dashboard_stat_total), MoneyFormatter.format(claims.sumOf { it.amount }, currency, appLocale()), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        // Sorted and indexed once per card rather than per row: the sort ran on
        // every recomposition and the claim lookup was a scan for each shift.
        val orderedShifts = remember(shifts) { shifts.sortedByDescending { it.startTime } }
        val claimsByShift = remember(claims) { claims.groupBy { it.shiftId } }
        orderedShifts.forEachIndexed { index, shift ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 7.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val shiftClaims = claimsByShift[shift.id].orEmpty()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(shift.startTime.atZone(zone).format(DateTimeFormatter.ofPattern("EEE, d MMM", appLocale())), fontWeight = FontWeight.SemiBold)
                    shiftClaims.forEach { claim ->
                        Text(
                            stringResource(
                                R.string.reports_claim_line,
                                stringResource(if (claim.direction == com.elmtrackr.app.domain.model.RefundDirection.TO_WORK) R.string.reports_dir_to_work else R.string.reports_dir_from_work),
                                claim.provider.name.lowercase(),
                                MoneyFormatter.format(claim.amount, currency, appLocale()),
                            ) + if (claim.receiptPath != null) stringResource(R.string.reports_receipt_suffix) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        claim.receiptPath?.let { path ->
                            TextButton(onClick = { onViewReceipt(path) }) { Text(stringResource(R.string.refunds_view_receipt)) }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        refundStatus(shift.refundAction),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (shift.refundAction == RefundAction.SUBMITTED) AuroraAquaDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onNavigateToShift(shift.id) }) {
                        Text(stringResource(R.string.reports_view), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (exportRows.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { isExporting = true; onExport(exportRows) { isExporting = false } },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isExporting) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (isExporting) stringResource(R.string.reports_preparing_pdf) else stringResource(R.string.reports_export_pdf_month, month.month.displayName(appLocale())))
            }
        }
    }
}

// ── Shared primitives ─────────────────────────────────────────────────────────

@Composable
private fun OtThresholdFootnote(
    settings: UserSettings,
    profiles: List<CompensationProfile> = emptyList(),
) {
    // Resolved, not read off the settings fields. Those default to 480/2400 while
    // every figure on this screen resolves its thresholds through a profile — 516
    // and 2520 on the Israeli preset — so the footnote used to state a threshold
    // none of the numbers above it used.
    val rules = remember(settings, profiles) {
        CompensationResolver.defaultRules(settings, profiles)
    }
    val daily = durationText(rules.dailyStandardMinutes)
    val weekly = durationText(rules.weeklyStandardMinutes)
    ReportCard {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 340.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OtThresholdLine(stringResource(R.string.reports_daily_ot_after), daily)
                    OtThresholdLine(stringResource(R.string.reports_weekly_ot_after), weekly)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OtThresholdLine(stringResource(R.string.reports_daily_ot_after), daily)
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.width(1.dp).height(12.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(Modifier.width(16.dp))
                    OtThresholdLine(stringResource(R.string.reports_weekly_ot_after), weekly)
                }
            }
        }
    }
}

@Composable
private fun OtThresholdLine(prefix: String, value: String) {
    Text(
        buildAnnotatedString {
            append("$prefix ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                append(value)
            }
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ShiftTagPill(label: String, weekendStyle: Boolean) {
    val bg = if (weekendStyle) auroraWeekendBackground() else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (weekendStyle) AuroraPlum else AuroraIndigo
    Text(
        label,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = fg,
    )
}

@Composable
private fun DistributionHeader(report: com.elmtrackr.app.domain.model.MonthlyReport, modifier: Modifier = Modifier) {
    // Paid hours by category: what the bar beneath it divides. Worked hours are the
    // headline figure elsewhere, so this one is labelled as paid time.
    Column(modifier) {
        SectionLabel(stringResource(R.string.reports_hours_distribution))
        Text(
            stringResource(R.string.shifts_hours_value_spaced, HoursFormatter.decimal(report.totalMinutes, appLocale())) +
                " · " + stringResource(R.string.paid_time_paid),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(if (report.shiftCount == 1) R.string.dashboard_shift_count_one else R.string.dashboard_shift_count_other, report.shiftCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(CornerRadius.Medium)
    Card(
        modifier
            .auroraEnter()
            .shadow(6.dp, shape, clip = false,
                ambientColor = AuroraIndigo.copy(alpha = 0.04f),
                spotColor = AuroraIndigo.copy(alpha = 0.22f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            SectionLabel(label)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, background: Color, color: Color, modifier: Modifier) {
    Column(
        modifier.background(background, RoundedCornerShape(CornerRadius.Medium)).padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReportCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(CornerRadius.Large)
    Card(
        modifier
            .fillMaxWidth()
            .auroraEnter()
            .animateContentSize()
            .shadow(8.dp, shape, clip = false,
                ambientColor = AuroraIndigo.copy(alpha = 0.04f),
                spotColor = AuroraIndigo.copy(alpha = 0.28f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

@Composable
private fun LeaveReportSection(state: ReportsUiState.Ready) {
    val leave = state.paidLeave
    ElmSectionHeader(stringResource(R.string.leave_reports_section))
    Spacer(Modifier.height(Spacing.s8))
    ReportCard {
        if (leave != null && !leave.isEmpty) {
            if (leave.hasVacation) {
                ReportRow(
                    stringResource(R.string.leave_reports_vacation),
                    leaveAmountLabel(leave.vacationDays, leave.vacationHours),
                )
            }
            if (leave.hasSick) {
                ReportRow(
                    stringResource(R.string.leave_reports_sick),
                    leaveAmountLabel(leave.sickDays, leave.sickHours),
                )
            }
            // One total per currency. Two workplaces paid in different currencies
            // have no honest combined figure, so a second currency gets its own
            // row, labelled with its code, rather than being added to the first.
            val totals = state.leaveGrossByCurrency
            val totalLabel = stringResource(R.string.leave_reports_estimated_total)
            totals.forEach { (code, gross) ->
                ReportRow(
                    if (totals.size == 1) {
                        totalLabel
                    } else {
                        stringResource(R.string.project_money_label_with_code, totalLabel, BidiText.isolate(code))
                    },
                    MoneyFormatter.format(gross, code, appLocale()),
                )
            }
            if (state.leaveEntries.isNotEmpty()) {
                HorizontalDivider(
                    Modifier.padding(vertical = Spacing.s10),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        state.leaveEntries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    Modifier.padding(vertical = Spacing.s10),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            LeaveReportRow(entry)
        }
    }
}

@Composable
private fun LeaveReportRow(entry: AbsenceListEntry) {
    val date = entry.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", appLocale()))
    val type = stringResource(
        if (entry.type == com.elmtrackr.app.domain.model.AbsenceType.VACATION) {
            R.string.leave_reports_vacation
        } else {
            R.string.leave_reports_sick
        },
    )
    val quantity = when (entry.allocation.unit) {
        LeaveBalanceUnit.DAYS -> if (entry.allocation.entitlementUnits == 1.0) {
            stringResource(R.string.leave_form_length_single)
        } else {
            stringResource(
                R.string.leave_reports_days_value,
                LeaveQuantityFormat.number(entry.allocation.entitlementUnits, appLocale()),
            )
        }
        LeaveBalanceUnit.HOURS -> stringResource(
            R.string.leave_reports_hours_value,
            LeaveQuantityFormat.number(entry.allocation.entitlementUnits, appLocale()),
        )
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$date · $type",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                quantity,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.s8),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.s2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.workplaceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (entry.allocation.estimatedGrossPay > 0.0) {
                Text(
                    MoneyFormatter.format(entry.allocation.estimatedGrossPay, entry.currencyCode, appLocale()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = Spacing.s8),
                )
            }
        }
        entry.event.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s2),
            )
        }
    }
}

@Composable
private fun leaveAmountLabel(days: Double, hours: Double): String {
    val locale = appLocale()
    val dayText = when {
        days <= 0.0 -> null
        days == 1.0 -> stringResource(R.string.leave_form_length_single)
        else -> stringResource(
            R.string.leave_reports_days_value,
            LeaveQuantityFormat.number(days, locale),
        )
    }
    val hourText = if (hours > 0.0) {
        stringResource(
            R.string.leave_reports_hours_value,
            LeaveQuantityFormat.number(hours, locale),
        )
    } else {
        null
    }
    return listOfNotNull(dayText, hourText).joinToString(" · ")
}

@Composable
private fun SectionLabel(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun ReportRow(label: String, value: String, valueColor: Color = AuroraIndigo) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        // Same measurement order problem as SettingsInfoRow: the label yields so
        // the value is measured first, and the padding keeps a gap regardless.
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = Spacing.s8),
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Indexes the claims once instead of scanning them per shift. The old form was
 * shifts × claims, and both grow with the user's whole history, so the cost of
 * this rose with the square of how long they had been using the app.
 */
private fun submittedExportRows(
    shifts: List<Shift>,
    claims: List<RefundClaim>,
): List<Pair<Shift, RefundClaim>> {
    val claimsByShift = claims.groupBy { it.shiftId }
    return shifts.filter { it.refundAction == RefundAction.SUBMITTED }
        .flatMap { shift -> claimsByShift[shift.id].orEmpty().map { shift to it } }
}

@Composable
private fun monthsLabelCount(shifts: List<Shift>, zone: ZoneId): String {
    val count = shifts.map { YearMonth.from(it.startTime.atZone(zone)) }.distinct().size
    return stringResource(
        if (count == 1) R.string.reports_month_count_one else R.string.reports_month_count_other,
        count,
    )
}

@Composable
private fun refundStatus(action: RefundAction?): String = stringResource(
    when (action) {
        RefundAction.SUBMITTED -> R.string.reports_submitted
        RefundAction.NO_RIDE_TAKEN -> R.string.refunds_no_ride
        RefundAction.REMIND_LATER -> R.string.refunds_remind_later
        null -> R.string.reports_pending
    },
)

private fun Month.displayName(locale: Locale): String =
    getDisplayName(JavaTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase(locale) }

/**
 * Runs an export and says so when it fails.
 *
 * The five export call sites each did `scope.launch { ReportExporter.share…() }` with no
 * `catch`. Those are suspend functions that write a file and hand it to another app, so
 * they throw on a full disk, on revoked storage access, and on a device with nothing
 * registered for the MIME type. The launched coroutine took the exception and the user
 * saw a tap that did nothing — no file, no error, no way to tell which.
 *
 * The snackbar comes from [LocalAppSnackbarHostState], hosted once in `MainScaffold`.
 * Reports had no host of its own, which is why the only alternative used to be
 * escalating to the full-screen error state and losing the report behind it.
 */
private suspend fun runExport(
    snackbarHostState: androidx.compose.material3.SnackbarHostState?,
    failureMessage: String,
    onSuccess: () -> Unit = {},
    export: suspend () -> Unit,
) {
    runCatching { export() }
        .onSuccess { onSuccess() }
        .onFailure {
            com.elmtrackr.app.monitoring.CrashReporting.report(it)
            snackbarHostState.showAppMessage(failureMessage)
        }
}
