package com.elmtrackr.app.ui.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.ElmDashedButton
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraPressScale
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraOvertimeBackground
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import com.elmtrackr.app.ui.theme.auroraWeekendBackground
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val weekdayShortFmt = DateTimeFormatter.ofPattern("EEE")

private val ShiftActiveGreen = Color(0xFF22C55E)
private val ShiftActiveGreenBg = Color(0xFFE8F8EF)
private val ShiftActiveGreenBgDark = Color(0xFF1A3D2A)

@Composable
internal fun ShiftsPageHeader(onAddShift: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.shifts_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        ElmGradientButton(onClick = onAddShift, compact = true) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.shifts_add), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun ShiftsMonthPicker(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthNavRow(month = month, onPrevious = onPrevious, onNext = onNext, spread = false)
    }
}

@Composable
private fun MonthNavRow(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    spread: Boolean,
) {
    val canGoNext = month < YearMonth.now()
    Row(
        modifier = if (spread) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(40.dp)
                .background(auroraSurfaceSub(), RoundedCornerShape(CornerRadius.Small)),
        ) {
            Icon(Icons.Filled.ChevronLeft, stringResource(R.string.shifts_previous_month), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.mirrorInRtl())
        }
        Text(
            "${month.month.getDisplayName(TextStyle.FULL, appLocale())} ${month.year}",
            modifier = Modifier.padding(horizontal = Spacing.md),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier
                .size(40.dp)
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
internal fun ShiftsHeroSummaryCard(
    shifts: List<Shift>,
    activeShift: Shift?,
    settings: UserSettings?,
    month: YearMonth,
    profiles: List<CompensationProfile> = emptyList(),
    onPreviousMonth: (() -> Unit)? = null,
    onNextMonth: (() -> Unit)? = null,
) {
    val completed = remember(shifts) { shifts.filter { it.isCompleted } }
    val summary = remember(completed, settings, month, profiles) {
        val completedMinutes = completed.sumOf { ShiftDurationCalculator.netMinutes(it) ?: 0 }
        val report = settings?.let {
            MonthlyReportBuilder.buildMonthlyReport(month.year, month.monthValue, completed, it)
        }
        val regularMin = report?.regularMinutes ?: 0
        val overtimeMin = report?.overtimeMinutes ?: 0
        val weekendMin = report?.weekendMinutes ?: 0
        val pay = settings?.let { s ->
            val hasRate = (s.hourlyRate ?: 0.0) > 0.0 ||
                profiles.any { (it.baseHourlyRate ?: 0.0) > 0.0 }
            if (hasRate) PayrollCalculator.sumMonthlyPay(completed, s, profiles).totalGross else null
        }
        HeroSummaryData(
            completedMinutes = completedMinutes,
            regularMin = regularMin,
            overtimeMin = overtimeMin,
            weekendMin = weekendMin,
            pay = pay,
        )
    }

    val currency = settings?.currency ?: CurrencyCode.ILS
    val shape = RoundedCornerShape(CornerRadius.Large)
    val categoryTotal = (summary.regularMin + summary.overtimeMin + summary.weekendMin).coerceAtLeast(1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(0.06f), spotColor = Color.Black.copy(0.08f)),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            if (onPreviousMonth != null && onNextMonth != null) {
                MonthNavRow(month = month, onPrevious = onPreviousMonth, onNext = onNextMonth, spread = true)
                Spacer(Modifier.height(Spacing.md))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        stringResource(R.string.shifts_hours_tracked_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    HeroHoursTracked(
                        completedMinutes = summary.completedMinutes,
                        activeShift = activeShift,
                    )
                }
                summary.pay?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.shifts_est_gross_header),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            MoneyFormatter.format(it, currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            if (summary.completedMinutes > 0 || activeShift != null) {
                HeroHoursBreakdown(
                    activeShift = activeShift,
                    completedMinutes = summary.completedMinutes,
                    regularMin = summary.regularMin,
                    overtimeMin = summary.overtimeMin,
                    weekendMin = summary.weekendMin,
                    categoryTotal = categoryTotal,
                )
            }
        }
    }
}

@Composable
private fun HeroHoursTracked(completedMinutes: Int, activeShift: Shift?) {
    if (activeShift == null) {
        Text(
            stringResource(R.string.shifts_hours_value, formatHoursDecimal(completedMinutes)),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        return
    }
    var activeMinutes by remember(activeShift.startTime) { mutableLongStateOf(0L) }
    LaunchedEffect(activeShift.startTime) {
        while (true) {
            activeMinutes = ((Instant.now().toEpochMilli() - activeShift.startTime.toEpochMilli()) / 60_000)
                .coerceAtLeast(0)
            delay(1_000)
        }
    }
    Text(
        stringResource(R.string.shifts_hours_value, formatHoursDecimal(completedMinutes + activeMinutes.toInt())),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun HeroHoursBreakdown(
    activeShift: Shift?,
    completedMinutes: Int,
    regularMin: Int,
    overtimeMin: Int,
    weekendMin: Int,
    categoryTotal: Int,
) {
    if (completedMinutes <= 0 && activeShift == null) return
    Spacer(Modifier.height(Spacing.md))
    SegmentedHoursBar(
        regularFraction = regularMin.toFloat() / categoryTotal,
        overtimeFraction = overtimeMin.toFloat() / categoryTotal,
        weekendFraction = weekendMin.toFloat() / categoryTotal,
    )
    Spacer(Modifier.height(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        HoursLegendDot(color = AuroraIndigo, label = stringResource(R.string.shifts_legend_regular))
        HoursLegendDot(color = AuroraPeach, label = stringResource(R.string.shifts_legend_overtime))
        // Same series color as the dashboard and reports legends.
        HoursLegendDot(color = AuroraPlum, label = stringResource(R.string.shifts_legend_weekend))
    }
}

private data class HeroSummaryData(
    val completedMinutes: Int,
    val regularMin: Int,
    val overtimeMin: Int,
    val weekendMin: Int,
    val pay: Double?,
)

/** Kept for screenshot regression — delegates to [ShiftsHeroSummaryCard]. */
@Composable
internal fun MonthShiftSummary(shifts: List<Shift>, settings: UserSettings?) {
    ShiftsHeroSummaryCard(
        shifts = shifts,
        activeShift = null,
        settings = settings,
        month = YearMonth.now(),
    )
}

@Composable
private fun SegmentedHoursBar(
    regularFraction: Float,
    overtimeFraction: Float,
    weekendFraction: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50)),
    ) {
        if (regularFraction > 0f) {
            Box(
                Modifier
                    .weight(regularFraction.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(AuroraIndigo),
            )
        }
        if (overtimeFraction > 0f) {
            Box(
                Modifier
                    .weight(overtimeFraction.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(AuroraPeach),
            )
        }
        if (weekendFraction > 0f) {
            Box(
                Modifier
                    .weight(weekendFraction.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(AuroraPlum),
            )
        }
    }
}

@Composable
private fun HoursLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ShiftsWeekSectionHeader(
    section: ShiftWeekSection,
    settings: UserSettings?,
) {
    val currency = settings?.currency ?: CurrencyCode.ILS
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = auroraSurfaceSub(),
        shape = RoundedCornerShape(topStart = CornerRadius.Medium, topEnd = CornerRadius.Medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                section.label ?: stringResource(R.string.shifts_this_week),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.shifts_hours_value, formatHoursDecimal(section.totalMinutes)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                section.pay?.let {
                    Text(
                        MoneyFormatter.format(it, currency),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ShiftRow(
    shift: Shift,
    settings: UserSettings?,
    profiles: List<CompensationProfile> = emptyList(),
    allShiftsForPay: List<Shift> = emptyList(),
    showRefunds: Boolean,
    grouped: Boolean = false,
    entranceIndex: Int = 0,
    display: ShiftRowDisplayModel? = null,
    onClick: () -> Unit,
) {
    val rowZone = settings?.let { com.elmtrackr.app.domain.time.WorkTimezone.zoneFor(it) }
        ?: ZoneId.systemDefault()
    if (shift.isActive) {
        ActiveShiftRow(shift = shift, zone = rowZone, onClick = onClick, animateEntrance = !grouped)
        return
    }

    val rowDisplay = display ?: buildShiftRowDisplay(shift, settings, profiles, allShiftsForPay, zone = rowZone, locale = appLocale())

    val rowModifier = if (grouped) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).auroraEnter(entranceIndex)
    }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    @Suppress("UNUSED_PARAMETER") val _refunds = showRefunds

    Surface(
        onClick = {
            AuroraHaptics.navigationTap(haptic)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = rowModifier.auroraPressScale(interactionSource),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .background(auroraSurfaceSub(), RoundedCornerShape(CornerRadius.Small))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    rowDisplay.weekday,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    rowDisplay.dayNumber,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm),
            ) {
                Text(
                    stringResource(R.string.shifts_hours_value_spaced, formatHoursDecimal(rowDisplay.netMinutes)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (rowDisplay.weekend || rowDisplay.hasOt || shift.isSpecialDay || shift.premiumProfileId != null) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (rowDisplay.weekend || shift.isSpecialDay || shift.premiumProfileId != null) {
                            ShiftTypeBadge(
                                label = if (shift.premiumProfileId != null || shift.isSpecialDay) stringResource(R.string.shifts_badge_premium) else stringResource(R.string.shifts_badge_weekend),
                                background = auroraWeekendBackground(),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (rowDisplay.hasOt) {
                            ShiftTypeBadge(
                                label = stringResource(R.string.shifts_badge_ot),
                                background = auroraOvertimeBackground(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.shifts_time_range, rowDisplay.startText, rowDisplay.endText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (shift.breakMinutes > 0) {
                    Text(
                        stringResource(R.string.shifts_break_line, shift.breakMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                rowDisplay.payGross?.let {
                    Text(
                        MoneyFormatter.format(it, settings?.currency ?: CurrencyCode.ILS),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp).mirrorInRtl(),
                )
            }
        }
    }
}

@Composable
private fun ActiveShiftRow(
    shift: Shift,
    zone: ZoneId,
    onClick: () -> Unit,
    animateEntrance: Boolean,
) {
    val startText = shift.startTime.atZone(zone).format(timeFmt)
    val dayNumber = shift.startTime.atZone(zone).dayOfMonth.toString()
    val bgColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        ShiftActiveGreenBgDark
    } else {
        ShiftActiveGreenBg
    }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = {
            AuroraHaptics.navigationTap(haptic)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (animateEntrance) Modifier.auroraEnter(0) else Modifier)
            .auroraPressScale(interactionSource),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(ShiftActiveGreen),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .background(ShiftActiveGreen.copy(alpha = 0.14f), RoundedCornerShape(CornerRadius.Small))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.shifts_badge_now),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ShiftActiveGreen,
                    )
                    Text(
                        dayNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = ShiftActiveGreen,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(ShiftActiveGreen, CircleShape),
                        )
                        Text(
                            stringResource(R.string.shifts_on_shift),
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        ShiftTypeBadge(
                            label = stringResource(R.string.shifts_badge_live),
                            background = ShiftActiveGreen.copy(alpha = 0.16f),
                            color = ShiftActiveGreen,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.shifts_since, startText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                ActiveDurationCompact(shift.startTime)
            }
        }
    }
}

@Composable
private fun ActiveDurationCompact(start: Instant) {
    var seconds by remember(start) { mutableLongStateOf(0L) }
    LaunchedEffect(start) {
        while (true) {
            seconds = ((Instant.now().toEpochMilli() - start.toEpochMilli()) / 1000L).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    Text(
        formatLiveDuration(seconds),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = ShiftActiveGreen,
    )
}

@Composable
private fun ShiftTypeBadge(
    label: String,
    background: Color,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

internal fun formatHoursDecimal(minutes: Int): String = "%.1f".format(Locale.US, minutes / 60.0)

private fun formatLiveDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
    else "%02d:%02d".format(minutes, secs)
}

/** @deprecated Use [ShiftsMonthPicker] — kept for compatibility. */
@Composable
internal fun ShiftsMonthCard(
    month: YearMonth,
    shifts: List<Shift>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    ShiftsMonthPicker(month = month, onPrevious = onPrevious, onNext = onNext)
}

@Composable
internal fun ShiftsAddPastShiftButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElmDashedButton(
        label = stringResource(R.string.shifts_add_past_shift),
        onClick = onClick,
        modifier = modifier,
    )
}
