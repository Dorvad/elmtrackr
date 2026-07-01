@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.auroraSurfaceSub

internal val THEME_OPTIONS = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
internal val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

private val SUPPORTED_CLOCK_STYLES = ClockStyle.entries

internal fun minutesToHours(minutes: Int): String {
    val h = minutes / 60.0
    return if (h == h.toLong().toDouble()) h.toLong().toString() else "%.2f".format(h)
}

internal fun supportedClockStyleOf(style: ClockStyle): ClockStyle = style

internal fun appearanceSummary(theme: String, clockStyle: ClockStyle, clockStylesEnabled: Boolean): String {
    val themeLabel = THEME_OPTIONS.firstOrNull { it.first == theme }?.second ?: theme
    return if (clockStylesEnabled) {
        "$themeLabel · ${clockStyle.name.lowercase().replaceFirstChar(Char::uppercase)} face"
    } else {
        themeLabel
    }
}

internal fun payrollSummary(
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

internal fun featuresSummary(
    travelRefunds: Boolean,
    insights: Boolean,
    clockStyles: Boolean,
    overtimeReminders: Boolean,
): String {
    val enabled = listOfNotNull(
        "Travel Refunds".takeIf { travelRefunds },
        "Insights".takeIf { insights },
        "Clock Styles".takeIf { clockStyles },
        "Overtime Reminders".takeIf { overtimeReminders },
    )
    return when (enabled.size) {
        0 -> "No optional features enabled"
        in 1..2 -> enabled.joinToString(" · ")
        else -> "${enabled.size} features enabled"
    }
}

@Composable
internal fun ThemeSegmentedControl(selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(
            "Theme",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            THEME_OPTIONS.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
internal fun ThemeDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = THEME_OPTIONS.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            THEME_OPTIONS.forEach { (value, display) ->
                DropdownMenuItem(text = { Text(display) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
internal fun CurrencyDropdown(selected: CurrencyCode, onSelect: (CurrencyCode) -> Unit) {
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
internal fun ClockStyleDropdown(selected: ClockStyle, onSelect: (ClockStyle) -> Unit) {
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
                    Box(modifier = Modifier.weight(1f)) {
                        Card(
                            onClick = { onSelect(style) },
                            modifier = Modifier.fillMaxWidth().then(
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
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(18.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
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
            Image(
                painter = painterResource(R.drawable.fellowship_bg_shire),
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
                    fontFamily = FontFamily.Serif,
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
        animationSpec = infiniteRepeatable(
            tween(if (selected) 900 else 1800),
            RepeatMode.Reverse,
        ),
        label = "watch-pulse",
    )
    val animatedPulse = if (auroraMotionEnabled()) pulse else 1f
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
                    drawCircle(Color.White.copy(alpha = if (i % 3 == 0) animatedPulse else .35f), 1.5f, Offset((i * 31 % 97) / 100f * size.width, (i * 47 % 89) / 100f * size.height))
                }
                ClockStyle.RETRO -> drawRoundRect(accent.copy(alpha = .55f), style = Stroke(2f))
                ClockStyle.PULSE -> repeat(3) { i ->
                    drawCircle(accent.copy(alpha = animatedPulse / (i + 2)), radius * (.55f + i * .28f), center, style = Stroke(2f))
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
                        drawCircle(accent.copy(alpha = .4f + animatedPulse * .3f), 1.5f, Offset(center.x, mid + (i - 0.5f) * 4.dp.toPx()))
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
                    val orbitRadius = size.minDimension * .34f
                    drawCircle(accent.copy(alpha = .2f), orbitRadius, center, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))))
                    val angle = Math.toRadians(45.0)
                    val sat = Offset(center.x + kotlin.math.cos(angle).toFloat() * orbitRadius, center.y + kotlin.math.sin(angle).toFloat() * orbitRadius)
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

internal fun watchFaceDescription(style: ClockStyle): String = when (style) {
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

@Composable
internal fun HoursField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { msg -> { Text(msg) } },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Medium),
    )
}

@Composable
internal fun WeekendDaysSelector(selected: List<Int>, onChange: (List<Int>) -> Unit) {
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
internal fun WeekendDayChip(
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
