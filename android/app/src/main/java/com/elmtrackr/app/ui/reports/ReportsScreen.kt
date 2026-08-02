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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.DailyInsight
import com.elmtrackr.app.domain.InsightColor
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.OvernightShiftDetector
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.CurrencyCode
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
import com.elmtrackr.app.ui.design.ElmEmptyState
import com.elmtrackr.app.ui.design.ElmStatCard
import com.elmtrackr.app.ui.design.ElmStatVariant
import com.elmtrackr.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraPressScale
import com.elmtrackr.app.ui.refunds.ReceiptPreviewDialog
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraAquaDeep
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.auroraOvertimeBackground
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

private enum class ReportTab { HOURS, REFUNDS, PROJECTS }

// ── Insight color palette ─────────────────────────────────────────────────────

private val InsightColor.gradientBrush: Brush
    get() = when (this) {
        InsightColor.INDIGO  -> Brush.linearGradient(listOf(Color(0xFF5B4DF2), Color(0xFF7C3AED)))
        InsightColor.VIOLET  -> Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)))
        InsightColor.EMERALD -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF0D9488)))
        InsightColor.AMBER   -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEA580C)))
        InsightColor.ROSE    -> Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFDB2777)))
        InsightColor.SKY     -> Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF2563EB)))
    }

private val InsightColor.subTextColor: Color
    get() = when (this) {
        InsightColor.INDIGO  -> Color(0xFFBFB8FF)
        InsightColor.VIOLET  -> Color(0xFFD8B4FE)
        InsightColor.EMERALD -> Color(0xFFA7F3D0)
        InsightColor.AMBER   -> Color(0xFFFDE68A)
        InsightColor.ROSE    -> Color(0xFFFDA4AF)
        InsightColor.SKY     -> Color(0xFFBAE6FD)
    }

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel(),
    onNavigateToShift: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsState()
    val canGoNext by viewModel.canGoNext.collectAsState()
    val ready = uiState as? ReportsUiState.Ready
    var activeTab by rememberSaveable { mutableStateOf(ReportTab.HOURS) }
    val scope = rememberCoroutineScope()
    var receiptPreviewUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val refundsEnabled = ready?.featuresTravelRefunds == true

    LaunchedEffect(refundsEnabled) {
        if (!refundsEnabled && activeTab == ReportTab.REFUNDS) {
            activeTab = ReportTab.HOURS
        }
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
        val embedNavInHoursCard = effectiveTab == ReportTab.HOURS && ready?.report?.shiftCount?.let { it > 0 } == true
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
                                    ReportExporter.shareCsv(
                                        context,
                                        viewModel.buildProjectCsv(projectReport),
                                        viewModel.projectCsvFilename(projectReport),
                                    )
                                    viewModel.onReportExported()
                                },
                            )
                        } ?: ReportsEmptyContent()

                        ReportTab.HOURS -> if (state.report.shiftCount == 0) {
                            ReportsEmptyContent()
                        } else {
                            HoursReport(
                                state = state,
                                onPreviousMonth = viewModel::previousMonth,
                                onNextMonth = viewModel::nextMonth,
                                canGoNext = canGoNext,
                                onExportCsv = {
                                    ReportExporter.shareCsv(
                                        context,
                                        viewModel.buildCsvContent(
                                            state.rawShifts,
                                            state.settings,
                                            state.year,
                                            state.month,
                                        ),
                                        viewModel.csvFilename(state.year, state.month),
                                    )
                                    viewModel.onReportExported()
                                },
                                onExportPdf = {
                                    ReportExporter.shareShiftPdf(context, state)
                                    viewModel.onReportExported()
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
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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

@Composable
private fun PhoneHoursReportTop(
    report: com.elmtrackr.app.domain.model.MonthlyReport,
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
) {
    ReportCard {
        MonthNavRow(year = year, month = month, onPrev = onPreviousMonth, onNext = onNextMonth, canGoNext = canGoNext)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stackActions = maxWidth < 340.dp
            if (stackActions) {
                Column(Modifier.fillMaxWidth()) {
                    DistributionHeader(report)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CSV", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = onExportPdf, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    DistributionHeader(report, Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportCsv) {
                            Icon(Icons.Filled.Share, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CSV", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = onExportPdf) {
                            Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DistributionBar(report.regularMinutes, report.overtimeMinutes, report.weekendMinutes)
        Spacer(Modifier.height(8.dp))
        DistributionLegend(report.regularMinutes, report.overtimeMinutes, report.weekendMinutes)
    }

    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ElmStatCard(stringResource(R.string.dashboard_stat_total), stringResource(R.string.reports_hours_value, formatHoursDecimal(report.totalMinutes)), Modifier.weight(1f), variant = ElmStatVariant.PRIMARY)
        ElmStatCard(stringResource(R.string.dashboard_stat_regular), stringResource(R.string.reports_hours_value, formatHoursDecimal(report.regularMinutes)), Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ElmStatCard(stringResource(R.string.dashboard_stat_overtime), stringResource(R.string.reports_hours_value, formatHoursDecimal(report.overtimeMinutes)), Modifier.weight(1f), variant = ElmStatVariant.OVERTIME)
        ElmStatCard(stringResource(R.string.dashboard_stat_weekend), stringResource(R.string.reports_hours_value, formatHoursDecimal(report.weekendMinutes)), Modifier.weight(1f), variant = ElmStatVariant.WEEKEND)
    }
}

@Composable
private fun TabletHoursReportTop(
    state: ReportsUiState.Ready,
    report: com.elmtrackr.app.domain.model.MonthlyReport,
    currency: String,
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
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
            label = stringResource(R.string.dashboard_stat_total),
            value = stringResource(R.string.reports_hours_value, formatHoursDecimal(report.totalMinutes)),
            sub = stringResource(R.string.reports_all_hours_month),
            variant = ElmStatVariant.PRIMARY,
            modifier = Modifier.weight(1f),
        )
        ElmStatCard(
            label = stringResource(R.string.dashboard_stat_regular),
            value = stringResource(R.string.reports_hours_value, formatHoursDecimal(report.regularMinutes)),
            sub = totalPct?.let { stringResource(R.string.reports_pct_of_total, report.regularMinutes * 100 / it) },
            modifier = Modifier.weight(1f),
        )
        ElmStatCard(
            label = stringResource(R.string.dashboard_stat_overtime),
            value = stringResource(R.string.reports_hours_value, formatHoursDecimal(report.overtimeMinutes)),
            sub = totalPct?.let { stringResource(R.string.reports_pct_of_total, report.overtimeMinutes * 100 / it) },
            variant = ElmStatVariant.OVERTIME,
            modifier = Modifier.weight(1f),
        )
        ElmStatCard(
            label = stringResource(R.string.dashboard_stat_weekend),
            value = stringResource(R.string.reports_hours_value, formatHoursDecimal(report.weekendMinutes)),
            sub = totalPct?.let { stringResource(R.string.reports_pct_of_total, report.weekendMinutes * 100 / it) },
            variant = ElmStatVariant.WEEKEND,
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(16.dp))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        state.paySummary?.takeIf { it.totalGross > 0 }?.let { pay ->
            ReportCard(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.reports_gross_pay_before_tax))
                Text(
                    MoneyFormatter.format(pay.totalGross, currency),
                    style = TextStyle(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        brush = Brush.linearGradient(listOf(AuroraIndigo, AuroraPlum, AuroraAqua)),
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayCell(stringResource(R.string.dashboard_stat_regular), pay.regularGross, currency, AuroraIndigo,
                        MaterialTheme.colorScheme.surfaceVariant, Modifier.weight(1f))
                    PayCell(stringResource(R.string.dashboard_stat_overtime), pay.overtimeGross, currency, AuroraPeachDeep,
                        auroraOvertimeBackground(), Modifier.weight(1f))
                    PayCell(stringResource(R.string.dashboard_pay_holiday), pay.specialGross, currency, AuroraPlum,
                        auroraWeekendBackground(), Modifier.weight(1f))
                }
            }
        } ?: Spacer(Modifier.weight(1f))

        ReportCard(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                DistributionHeader(report, Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportCsv) {
                        Icon(Icons.Filled.Share, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CSV", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = onExportPdf) {
                        Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            DistributionBar(report.regularMinutes, report.overtimeMinutes, report.weekendMinutes)
            Spacer(Modifier.height(8.dp))
            DistributionLegend(report.regularMinutes, report.overtimeMinutes, report.weekendMinutes)
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
                val delta = report.totalMinutes - state.previousMonthMinutes
                val pctChange = if (state.previousMonthMinutes > 0) {
                    (delta * 100 / state.previousMonthMinutes)
                } else 0
                val prevMonthName = YearMonth.of(state.year, state.month).minusMonths(1)
                    .month.getDisplayName(JavaTextStyle.FULL, appLocale())
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF0D9488))),
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
                    ShiftDurationCalculator.formatMinutes(insights.averageShiftMinutes)
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
    val currency = state.paySummary?.currencyCode ?: state.settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name
    val report = state.report
    val isTablet = isTabletLayout()

    Column(Modifier.fillMaxWidth()) {
    if (isTablet) {
        TabletHoursReportTop(
            state = state,
            report = report,
            currency = currency,
            year = state.year,
            month = state.month,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            canGoNext = canGoNext,
            onExportCsv = onExportCsv,
            onExportPdf = onExportPdf,
        )
    } else {
        PhoneHoursReportTop(
            report = report,
            year = state.year,
            month = state.month,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            canGoNext = canGoNext,
            onExportCsv = onExportCsv,
            onExportPdf = onExportPdf,
        )
    }

    if (!isTablet && state.previousMonthMinutes > 0) {
        val delta = report.totalMinutes - state.previousMonthMinutes
        val deltaColor = when {
            delta > 0 -> Color(0xFF10B981)
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
                Text(
                    stringResource(R.string.reports_hours_this_month, formatHoursDecimal(report.totalMinutes)),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.reports_vs_prev, arrow, formatHoursDecimal(abs(delta)), prevMonthName),
                    color = deltaColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    if (isTablet && state.previousMonthMinutes > 0) {
        val delta = report.totalMinutes - state.previousMonthMinutes
        val deltaColor = when {
            delta > 0 -> Color(0xFF10B981)
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
                Text(
                    stringResource(R.string.reports_hours_this_month, formatHoursDecimal(report.totalMinutes)),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.reports_vs_prev, arrow, formatHoursDecimal(abs(delta)), prevMonthName),
                    color = deltaColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    // ── Gross pay (phone only — shown in tablet top row) ─────────────────────
    if (!isTablet) {
    state.paySummary?.takeIf { it.totalGross > 0 }?.let { pay ->
        Spacer(Modifier.height(14.dp))
        ReportCard {
            SectionLabel(stringResource(R.string.reports_gross_pay_before_tax))
            Text(
                MoneyFormatter.format(pay.totalGross, currency),
                style = TextStyle(
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.linearGradient(listOf(AuroraIndigo, AuroraPlum, AuroraAqua)),
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayCell(stringResource(R.string.dashboard_stat_regular), pay.regularGross, currency, AuroraIndigo,
                    MaterialTheme.colorScheme.surfaceVariant, Modifier.weight(1f))
                PayCell(stringResource(R.string.dashboard_stat_overtime), pay.overtimeGross, currency, AuroraPeachDeep,
                    auroraOvertimeBackground(), Modifier.weight(1f))
                PayCell(stringResource(R.string.dashboard_pay_holiday), pay.specialGross, currency, AuroraPlum,
                    auroraWeekendBackground(), Modifier.weight(1f))
            }
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
        val currency2 = state.paySummary?.currencyCode ?: state.settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name
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
                    ShiftDurationCalculator.formatMinutes(insights.averageShiftMinutes)
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
                accentColor = AuroraPeachDeep,
                bgColor = auroraOvertimeBackground(),
                modifier = Modifier.weight(1f),
            )
            InsightStatCard(
                icon = "📏",
                label = stringResource(R.string.reports_longest_shift),
                value = ShiftDurationCalculator.formatMinutes(insights.longestShiftMinutes),
                sub = insights.longestShift?.startTime?.atZone(state.zone)
                    ?.format(DateTimeFormatter.ofPattern("MMM d", appLocale())),
                accentColor = Color(0xFF10B981),
                bgColor = Color(0xFF10B981).copy(alpha = 0.10f),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightStatCard(
                icon = "💰",
                label = stringResource(R.string.reports_avg_pay_shift),
                value = if (insights.avgPayPerShift != null) MoneyFormatter.format(insights.avgPayPerShift, currency2) else "—",
                sub = if (insights.avgPayPerShift != null) stringResource(R.string.reports_per_shift) else stringResource(R.string.reports_set_rate_unlock),
                accentColor = AuroraIndigo,
                bgColor = AuroraIndigo.copy(alpha = 0.10f),
                modifier = Modifier.weight(1f),
            )
            InsightStatCard(
                icon = "🏆",
                label = stringResource(R.string.reports_best_shift),
                value = if (insights.highestEarningAmount != null) MoneyFormatter.format(insights.highestEarningAmount, currency2) else "—",
                sub = insights.highestEarningShift?.startTime?.atZone(state.zone)
                    ?.format(DateTimeFormatter.ofPattern("MMM d", appLocale()))
                    ?: if (insights.highestEarningAmount == null) stringResource(R.string.reports_set_rate_unlock) else null,
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
                TaskBreakdownRow(task, currency)
            }
        }
    }

    // ── OT thresholds footnote (web: inline with hairline divider) ────────────
    state.settings?.let { settings ->
        Spacer(Modifier.height(12.dp))
        OtThresholdFootnote(settings)

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
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(dotWidth.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isActive) AuroraIndigo else MaterialTheme.colorScheme.outlineVariant)
                                .clickable {
                                    dotScope.launch {
                                        scrollState.scrollTo((i * cardStridePx).toInt())
                                    }
                                },
                        )
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
            .background(insight.color.gradientBrush, RoundedCornerShape(CornerRadius.Large))
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
                buildBoldAnnotatedString(insight.text.asString(), insight.color.subTextColor),
                style = MaterialTheme.typography.bodySmall,
                color = insight.color.subTextColor,
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
            Text(
                "${task.icon.orEmpty()} ${task.name}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.reports_hours_value, formatHoursDecimal(task.totalMinutes)),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.reports_task_shifts_avg, task.shiftCount, formatHoursDecimal(task.averageShiftMinutes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            task.totalPay?.let { pay ->
                Text(
                    MoneyFormatter.format(pay, currency),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (task.overtimeMinutes > 0) {
            Text(
                stringResource(R.string.reports_ot_hours, formatHoursDecimal(task.overtimeMinutes)),
                style = MaterialTheme.typography.labelSmall,
                color = AuroraPeachDeep,
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
                        delta > 0 -> Color(0xFF10B981)
                        delta < 0 -> Color(0xFFF43F5E)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val arrow = when {
                        delta > 0 -> "↑"
                        delta < 0 -> "↓"
                        else -> "—"
                    }
                    Text(
                        "$arrow ${ShiftDurationCalculator.formatMinutes(abs(delta))}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (week.totalMinutes > 0) stringResource(R.string.reports_hours_value, formatHoursDecimal(week.totalMinutes)) else "—",
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
        val showSubRow = week.overtimeMinutes > 0 || (week.pay != null && (week.pay ?: 0.0) > 0)
        if (showSubRow) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (week.overtimeMinutes > 0) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(AuroraPeach))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.reports_ot_suffix, ShiftDurationCalculator.formatMinutes(week.overtimeMinutes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroraPeach,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                week.pay?.takeIf { it > 0 }?.let { pay ->
                    if (week.overtimeMinutes > 0) Spacer(Modifier.width(12.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        MoneyFormatter.format(pay, settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name),
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
    val breakdown = MonthlyReportBuilder.buildShiftBreakdown(shift, settings)
    val date = shift.startTime.atZone(zone).toLocalDate()
    val weekend = CompensationResolver.isWeekendShift(shift, settings, profiles)
    val overnight = OvernightShiftDetector.isOvernight(shift, zone)
    val pay = PayrollCalculator.calculateShiftPayInContext(
        shift,
        allShifts.ifEmpty { listOf(shift) },
        settings,
        profiles,
        premiumProfiles,
    )
    val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
    val otThreshold = resolved.rules.dailyStandardMinutes
    val otMins = maxOf(0, (ShiftDurationCalculator.netMinutes(shift) ?: 0) - otThreshold)

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
                    Text(ShiftDurationCalculator.formatMinutes(breakdown.totalMinutes), fontWeight = FontWeight.Bold)
                    if (otMins > 0 && !shift.isSpecialDay && !weekend) {
                        Text(
                            stringResource(R.string.reports_plus_ot, ShiftDurationCalculator.formatMinutes(otMins)),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroraPeach,
                        )
                    }
                    pay?.let {
                        Text(
                            MoneyFormatter.format(it.totalGross, it.currencyCode),
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
    val currency = state.paySummary?.currencyCode ?: state.settings?.displayCurrencyCode() ?: CurrencyCode.ILS.name
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
    val exportRows = submittedExportRows(state.allShifts, claims)
    if (exportRows.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                exportingAll = true
                scope.launch {
                    try {
                        val pdfRows = exportRows.map { (shift, claim) ->
                            val bitmap = claim.receiptPath?.let { viewModel.receiptUrl(it) }?.let { ReportExporter.loadReceipt(it) }
                            RefundPdfRow(shift, claim, bitmap)
                        }
                        ReportExporter.shareRefundPdf(context, pdfRows, "all-months", allMonthsLabel, currency, state.zone)
                        viewModel.onReportExported()
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
    val months = reimbursableShifts.map { YearMonth.from(it.startTime.atZone(state.zone)) }.distinct().sortedDescending()
    months.forEach { month ->
        val monthShifts = reimbursableShifts.filter { YearMonth.from(it.startTime.atZone(state.zone)) == month }
        val monthShiftIds = monthShifts.mapTo(mutableSetOf()) { it.id }
        RefundMonthCard(
            month = month,
            shifts = monthShifts,
            claims = claims.filter { it.shiftId in monthShiftIds },
            onViewReceipt = onViewReceipt,
            onNavigateToShift = onNavigateToShift,
            onExport = { rows, onDone ->
                scope.launch {
                    try {
                        val pdfRows = rows.map { (shift, claim) ->
                            val bitmap = claim.receiptPath?.let { viewModel.receiptUrl(it) }?.let { ReportExporter.loadReceipt(it) }
                            RefundPdfRow(shift, claim, bitmap)
                        }
                        ReportExporter.shareRefundPdf(context, pdfRows, month.year, month.monthValue, currency, state.zone)
                        viewModel.onReportExported()
                    } finally { onDone() }
                }
            },
            currency = currency,
            zone = state.zone,
        )
        Spacer(Modifier.height(12.dp))
    }
}

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
    val salary = settings?.let { s ->
        val gross = PayrollCalculator.sumMonthlyPay(shifts.filter { it.isCompleted }, s, profiles, premiumProfiles).totalGross
        gross.takeIf { it > 0 }
    }
    Card(shape = RoundedCornerShape(CornerRadius.Large), colors = CardDefaults.cardColors(containerColor = AuroraIndigo)) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.reports_total_refunded), style = MaterialTheme.typography.labelSmall, color = Color(0xffd8d3ff), fontWeight = FontWeight.Bold)
            Text(MoneyFormatter.format(total, currency), style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
            val activeMonths = claims.map { YearMonth.from(it.rideAt.atZone(zone)) }.distinct().size
            Text(stringResource(R.string.reports_rides_months, claims.size, activeMonths), color = Color(0xffd8d3ff), style = MaterialTheme.typography.bodySmall)
            if (salary != null && salary > 0) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = .2f))
                Text(stringResource(R.string.reports_total_compensation), style = MaterialTheme.typography.labelSmall, color = Color(0xffd8d3ff))
                Text(MoneyFormatter.format(salary + total, currency), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    ReportCard {
        SectionLabel(stringResource(R.string.reports_by_provider))
        claims.groupBy { it.provider }.entries.sortedByDescending { it.value.sumOf(RefundClaim::amount) }.forEach { (provider, rows) ->
            val amount = rows.sumOf { it.amount }
            Spacer(Modifier.height(8.dp))
            ReportRow(stringResource(R.string.reports_provider_rides, provider.name.lowercase().replaceFirstChar(Char::uppercase), rows.size), MoneyFormatter.format(amount, currency))
            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth((amount / total).toFloat().coerceIn(0f, 1f)).height(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(stringResource(R.string.reports_avg_per_ride), MoneyFormatter.format(total / claims.size, currency), AuroraIndigo, Modifier.weight(1f))
        StatCard(stringResource(R.string.reports_largest_ride), MoneyFormatter.format(claims.maxOf { it.amount }, currency), AuroraPeach, Modifier.weight(1f))
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
            StatPill(stringResource(R.string.dashboard_stat_total), MoneyFormatter.format(claims.sumOf { it.amount }, currency), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        shifts.sortedByDescending { it.startTime }.forEachIndexed { index, shift ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 7.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val shiftClaims = claims.filter { it.shiftId == shift.id }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(shift.startTime.atZone(zone).format(DateTimeFormatter.ofPattern("EEE, d MMM", appLocale())), fontWeight = FontWeight.SemiBold)
                    shiftClaims.forEach { claim ->
                        Text(
                            stringResource(
                                R.string.reports_claim_line,
                                stringResource(if (claim.direction == com.elmtrackr.app.domain.model.RefundDirection.TO_WORK) R.string.reports_dir_to_work else R.string.reports_dir_from_work),
                                claim.provider.name.lowercase(),
                                MoneyFormatter.format(claim.amount, currency),
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
private fun OtThresholdFootnote(settings: UserSettings) {
    val daily = ShiftDurationCalculator.formatMinutes(settings.dailyOvertimeThresholdMinutes)
    val weekly = ShiftDurationCalculator.formatMinutes(settings.weeklyOvertimeThresholdMinutes)
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
    Column(modifier) {
        SectionLabel(stringResource(R.string.reports_hours_distribution))
        Text(
            stringResource(R.string.shifts_hours_value_spaced, formatHoursDecimal(report.totalMinutes)),
            style = MaterialTheme.typography.headlineMedium,
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
private fun DistributionLegend(regular: Int, overtime: Int, weekend: Int) {
    val total = (regular + overtime + weekend).coerceAtLeast(1)
    val items = listOf(
        Triple(stringResource(R.string.dashboard_stat_regular), regular, AuroraIndigo),
        Triple(stringResource(R.string.dashboard_stat_overtime), overtime, AuroraPeach),
        Triple(stringResource(R.string.dashboard_stat_weekend), weekend, AuroraPlum),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, minutes, color) ->
            SegmentLegendRow(
                label = label,
                minutes = minutes,
                color = color,
                percent = ((minutes.toFloat() / total) * 100).toInt(),
            )
        }
    }
}

@Composable
private fun SegmentLegendRow(label: String, minutes: Int, color: Color, percent: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("$percent%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.reports_hours_value, formatHoursDecimal(minutes)),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DistributionBar(regular: Int, overtime: Int, weekend: Int) {
    val total = (regular + overtime + weekend).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (regular > 0) Box(Modifier.weight(regular.toFloat() / total).height(10.dp).background(AuroraIndigo, RoundedCornerShape(6.dp)))
        if (overtime > 0) Box(Modifier.weight(overtime.toFloat() / total).height(10.dp).background(AuroraPeach, RoundedCornerShape(6.dp)))
        if (weekend > 0) Box(Modifier.weight(weekend.toFloat() / total).height(10.dp).background(AuroraPlum, RoundedCornerShape(6.dp)))
    }
}

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
private fun PayCell(label: String, amount: Double, currency: String, textColor: Color, bgColor: Color, modifier: Modifier) {
    Column(
        modifier.background(bgColor, RoundedCornerShape(CornerRadius.Medium)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            MoneyFormatter.format(amount, currency),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
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
private fun SectionLabel(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun ReportRow(label: String, value: String, valueColor: Color = AuroraIndigo) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun submittedExportRows(
    shifts: List<Shift>,
    claims: List<RefundClaim>,
): List<Pair<Shift, RefundClaim>> =
    shifts.filter { it.refundAction == RefundAction.SUBMITTED }
        .flatMap { shift -> claims.filter { it.shiftId == shift.id }.map { shift to it } }

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
private fun formatHoursDecimal(minutes: Int): String = "%.1f".format(Locale.US, minutes / 60.0)
