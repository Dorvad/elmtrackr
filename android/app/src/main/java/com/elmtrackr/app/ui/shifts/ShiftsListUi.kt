package com.elmtrackr.app.ui.shifts

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.design.ElmDashedButton
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.theme.AuroraAqua
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
            "Shifts",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        ElmGradientButton(onClick = onAddShift, compact = true) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun ShiftsMonthPicker(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val canGoNext = month < YearMonth.now()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(40.dp)
                .background(auroraSurfaceSub(), RoundedCornerShape(CornerRadius.Small)),
        ) {
            Icon(Icons.Filled.ChevronLeft, "Previous month", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
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
                "Next month",
                tint = if (canGoNext) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
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
) {
    val completed = shifts.filter { it.isCompleted }
    val activeMinutes = activeShift?.let {
        ((Instant.now().toEpochMilli() - it.startTime.toEpochMilli()) / 60_000).toInt().coerceAtLeast(0)
    } ?: 0
    val completedMinutes = completed.sumOf { ShiftDurationCalculator.netMinutes(it) ?: 0 }
    val totalMinutes = completedMinutes + activeMinutes

    val report = settings?.let {
        MonthlyReportBuilder.buildMonthlyReport(month.year, month.monthValue, completed, it)
    }

    val regularMin = report?.regularMinutes ?: 0
    val overtimeMin = report?.overtimeMinutes ?: 0
    val weekendMin = report?.weekendMinutes ?: 0
    val categoryTotal = (regularMin + overtimeMin + weekendMin).coerceAtLeast(1)

    val pay = settings?.hourlyRate?.takeIf { it > 0 }?.let {
        PayrollCalculator.sumMonthlyPay(completed, settings).totalGross
    }

    val currency = settings?.currency ?: CurrencyCode.ILS
    val shape = RoundedCornerShape(CornerRadius.Large)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(0.06f), spotColor = Color.Black.copy(0.08f)),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "HOURS TRACKED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${formatHoursDecimal(totalMinutes)}h",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                pay?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "EST. GROSS",
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

            if (totalMinutes > 0) {
                Spacer(Modifier.height(Spacing.md))
                SegmentedHoursBar(
                    regularFraction = regularMin.toFloat() / categoryTotal,
                    overtimeFraction = overtimeMin.toFloat() / categoryTotal,
                    weekendFraction = weekendMin.toFloat() / categoryTotal,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    HoursLegendDot(color = AuroraIndigo, label = "Regular")
                    HoursLegendDot(color = AuroraPeach, label = "Overtime")
                    HoursLegendDot(color = AuroraAqua, label = "Weekend")
                }
            }
        }
    }
}

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
                    .background(AuroraAqua),
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
                section.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "${formatHoursDecimal(section.totalMinutes)}h",
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
    showRefunds: Boolean,
    grouped: Boolean = false,
    entranceIndex: Int = 0,
    onClick: () -> Unit,
) {
    if (shift.isActive) {
        ActiveShiftRow(shift = shift, onClick = onClick, entranceIndex = entranceIndex)
        return
    }

    val zone = ZoneId.systemDefault()
    val zdt = shift.startTime.atZone(zone)
    val weekday = zdt.format(weekdayShortFmt).uppercase(Locale.getDefault())
    val dayNumber = zdt.dayOfMonth.toString()
    val startText = zdt.format(timeFmt)
    val endText = shift.endTime?.atZone(zone)?.format(timeFmt) ?: ""
    val netMinutes = ShiftDurationCalculator.netMinutes(shift) ?: 0
    val weekend = settings?.let { CompensationResolver.isWeekendShift(shift, it, profiles) } == true
    val breakdown = settings?.let { MonthlyReportBuilder.buildShiftBreakdown(shift, it) }
    val hasOt = (breakdown?.overtimeMinutes ?: 0) > 0 && !shift.isSpecialDay && !weekend
    val pay = settings?.let { PayrollCalculator.calculateShiftPay(shift, it, profiles) }

    val rowModifier = if (grouped) {
        Modifier.fillMaxWidth().auroraEnter(entranceIndex)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).auroraEnter(entranceIndex)
    }

    @Suppress("UNUSED_PARAMETER") val _refunds = showRefunds

    Surface(
        onClick = onClick,
        modifier = rowModifier,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    weekday,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dayNumber,
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
                    "${formatHoursDecimal(netMinutes)} h",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (weekend || hasOt || shift.isSpecialDay) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (weekend || shift.isSpecialDay) {
                            ShiftTypeBadge(
                                label = if (shift.isSpecialDay) "HOLIDAY" else "WEEKEND",
                                background = auroraWeekendBackground(),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (hasOt) {
                            ShiftTypeBadge(
                                label = "OT",
                                background = auroraOvertimeBackground(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text(
                    "$startText - $endText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (shift.breakMinutes > 0) {
                    Text(
                        "${shift.breakMinutes}m break",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                pay?.let {
                    Text(
                        MoneyFormatter.format(it.totalGross, settings?.currency ?: CurrencyCode.ILS),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ActiveShiftRow(
    shift: Shift,
    onClick: () -> Unit,
    entranceIndex: Int,
) {
    val zone = ZoneId.systemDefault()
    val startText = shift.startTime.atZone(zone).format(timeFmt)
    val bgColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        ShiftActiveGreenBgDark
    } else {
        ShiftActiveGreenBg
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().auroraEnter(entranceIndex),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(ShiftActiveGreen, CircleShape),
                        )
                        Text(
                            "On shift",
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        ShiftTypeBadge(
                            label = "LIVE",
                            background = ShiftActiveGreen,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        "Since $startText",
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
        label = "Add a past shift",
        onClick = onClick,
        modifier = modifier,
    )
}
