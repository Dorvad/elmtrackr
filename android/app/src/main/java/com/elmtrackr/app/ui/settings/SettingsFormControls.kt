@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.language.AppLanguage
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.auroraSurfaceSub

private val SUPPORTED_CLOCK_STYLES = ClockStyle.entries

@Composable
internal fun themeOptions(): List<Pair<String, String>> = listOf(
    "system" to stringResource(R.string.settings_theme_system),
    "light" to stringResource(R.string.settings_theme_light),
    "dark" to stringResource(R.string.settings_theme_dark),
)

@Composable
internal fun dayLabels(): List<String> = stringArrayResource(R.array.weekday_short_labels).toList()

@Composable
internal fun clockStyleDisplayName(style: ClockStyle): String = stringResource(
    when (style) {
        ClockStyle.CLASSIC -> R.string.clock_style_classic
        ClockStyle.MINIMAL -> R.string.clock_style_minimal
        ClockStyle.FOCUS -> R.string.clock_style_focus
        ClockStyle.BOLD -> R.string.clock_style_bold
        ClockStyle.NIGHT -> R.string.clock_style_night
        ClockStyle.RETRO -> R.string.clock_style_retro
        ClockStyle.AURORA -> R.string.clock_style_aurora
        ClockStyle.PULSE -> R.string.clock_style_pulse
        ClockStyle.DIAL -> R.string.clock_style_dial
        ClockStyle.STRAND -> R.string.clock_style_strand
        ClockStyle.PRISM -> R.string.clock_style_prism
        ClockStyle.SAND -> R.string.clock_style_sand
        ClockStyle.BLOCKS -> R.string.clock_style_blocks
        ClockStyle.ORBIT -> R.string.clock_style_orbit
        ClockStyle.TIDE -> R.string.clock_style_tide
    },
)

@Composable
internal fun currencyDisplayName(currency: CurrencyCode): String = stringResource(
    when (currency) {
        CurrencyCode.ILS -> R.string.currency_ils
        CurrencyCode.USD -> R.string.currency_usd
        CurrencyCode.EUR -> R.string.currency_eur
        CurrencyCode.GBP -> R.string.currency_gbp
        CurrencyCode.CAD -> R.string.currency_cad
        CurrencyCode.AUD -> R.string.currency_aud
        CurrencyCode.JPY -> R.string.currency_jpy
        CurrencyCode.CHF -> R.string.currency_chf
    },
)

internal fun minutesToHours(minutes: Int): String {
    val h = minutes / 60.0
    return if (h == h.toLong().toDouble()) h.toLong().toString() else "%.2f".format(h)
}

internal fun supportedClockStyleOf(style: ClockStyle): ClockStyle = style

@Composable
internal fun appearanceSummary(theme: String, clockStyle: ClockStyle, clockStylesEnabled: Boolean): String {
    val themeLabel = themeOptions().firstOrNull { it.first == theme }?.second ?: theme
    return if (clockStylesEnabled) {
        val face = stringResource(
            R.string.settings_summary_face,
            clockStyleDisplayName(clockStyle),
        )
        "$themeLabel · $face"
    } else {
        themeLabel
    }
}

@Composable
internal fun payrollSummary(
    hourlyRateText: String,
    currency: CurrencyCode,
    dailyOtText: String,
    weeklyOtText: String,
    weekendDays: List<Int>,
    timezone: String,
): String {
    val labels = dayLabels()
    val ratePart = hourlyRateText.toDoubleOrNull()
        ?.let { stringResource(R.string.settings_summary_rate_per_hour, "${currency.symbol}$it") }
        ?: stringResource(R.string.settings_summary_no_rate)
    val otPart = stringResource(R.string.settings_summary_ot, dailyOtText, weeklyOtText)
    val weekendPart = weekendDays.sorted().joinToString(", ") { labels[it] }
    return "$ratePart · $otPart · $weekendPart · $timezone"
}

@Composable
internal fun featuresSummary(
    travelRefunds: Boolean,
    insights: Boolean,
    clockStyles: Boolean,
    overtimeReminders: Boolean,
): String {
    val enabled = listOfNotNull(
        stringResource(R.string.settings_feature_travel_short).takeIf { travelRefunds },
        stringResource(R.string.settings_feature_insights_short).takeIf { insights },
        stringResource(R.string.settings_feature_clock_styles_short).takeIf { clockStyles },
        stringResource(R.string.settings_feature_ot_reminders_short).takeIf { overtimeReminders },
    )
    return when (enabled.size) {
        0 -> stringResource(R.string.settings_summary_no_features)
        in 1..2 -> enabled.joinToString(" · ")
        else -> stringResource(R.string.settings_summary_features_count, enabled.size)
    }
}

@Composable
internal fun ThemeSegmentedControl(selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(
            stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            themeOptions().forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

/**
 * In-app language switcher. Applying a language recreates the activity, so
 * there is no state to hoist — the selection is read back from the
 * per-app locale APIs on the next composition.
 */
@Composable
internal fun LanguageSegmentedControl() {
    val context = LocalContext.current
    val current = AppLanguage.current()
    Column {
        Text(
            stringResource(R.string.settings_language),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = current == AppLanguage.SYSTEM,
                onClick = { AppLanguage.apply(context, AppLanguage.SYSTEM) },
                label = { Text(stringResource(R.string.settings_language_system)) },
            )
            // Language names stay in their own language on purpose.
            FilterChip(
                selected = current == AppLanguage.ENGLISH,
                onClick = { AppLanguage.apply(context, AppLanguage.ENGLISH) },
                label = { Text("English") },
            )
            FilterChip(
                selected = current == AppLanguage.HEBREW,
                onClick = { AppLanguage.apply(context, AppLanguage.HEBREW) },
                label = { Text("עברית") },
            )
        }
    }
}

@Composable
internal fun ThemeDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = themeOptions()
    val label = options.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, display) ->
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
            value = "${selected.symbol}  ${selected.name} - ${currencyDisplayName(selected)}",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CurrencyCode.entries.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.symbol}  ${currency.name} - ${currencyDisplayName(currency)}") },
                    onClick = { onSelect(currency); expanded = false },
                )
            }
        }
    }
}

@Composable
internal fun ClockStyleDropdown(selected: ClockStyle, onSelect: (ClockStyle) -> Unit) {
    Column {
        Text(stringResource(R.string.settings_watch_face), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.settings_watch_face_hint),
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
                                    clockStyleDisplayName(style),
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
                                    contentDescription = stringResource(R.string.settings_selected),
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
    val darkFace = style in listOf(ClockStyle.BOLD, ClockStyle.NIGHT, ClockStyle.RETRO)
    val faceBackground = if (darkFace) Color(0xFF11162A) else MaterialTheme.colorScheme.surface
    val accent = when (style) {
        ClockStyle.NIGHT, ClockStyle.TIDE -> Color(0xFF54D8E1)
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
                ClockStyle.CLASSIC -> {
                    drawCircle(accent.copy(alpha = .18f), radius, center, style = Stroke(4f))
                    drawArc(accent, -90f, 260f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(5f, cap = StrokeCap.Round))
                }
                ClockStyle.AURORA -> {
                    drawArc(accent.copy(alpha = .25f), 120f, 180f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(6f, cap = StrokeCap.Round))
                    drawArc(Color(0xFF54D8E1).copy(alpha = .8f), -90f, 200f, false, Offset(center.x - radius * .8f, center.y - radius * .8f), Size(radius * 1.6f, radius * 1.6f), style = Stroke(4f, cap = StrokeCap.Round))
                    drawArc(accent, -60f, 130f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(5f, cap = StrokeCap.Round))
                }
                ClockStyle.MINIMAL -> {
                    drawLine(accent.copy(alpha = .45f), Offset(center.x - radius * .7f, center.y + radius * .55f), Offset(center.x + radius * .7f, center.y + radius * .55f), 2f, StrokeCap.Round)
                    drawCircle(accent, 2.5f, Offset(center.x + radius * .7f, center.y + radius * .55f))
                }
                ClockStyle.FOCUS -> {
                    val y = center.y + radius * .6f
                    drawRoundRect(accent.copy(alpha = .18f), Offset(center.x - radius, y), Size(radius * 2f, 5f), androidx.compose.ui.geometry.CornerRadius(4f))
                    drawRoundRect(accent, Offset(center.x - radius, y), Size(radius * 2f * (.35f + animatedPulse * .3f), 5f), androidx.compose.ui.geometry.CornerRadius(4f))
                }
                ClockStyle.BOLD -> repeat(3) { i ->
                    val x = size.width * (.22f + i * .28f)
                    drawLine(accent.copy(alpha = .25f + i * .1f), Offset(x - 12f, 0f), Offset(x + 12f, size.height), 8f)
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
                ClockStyle.TIDE -> {
                    val vesselRadius = size.minDimension * .42f
                    drawCircle(accent.copy(alpha = .35f), vesselRadius, center, style = Stroke(2f))
                    val vessel = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(center.x - vesselRadius, center.y - vesselRadius, center.x + vesselRadius, center.y + vesselRadius))
                    }
                    val level = center.y + vesselRadius * (.5f - animatedPulse * .12f)
                    clipPath(vessel) {
                        val waveLine = Path().apply {
                            moveTo(center.x - vesselRadius, level)
                            var x = center.x - vesselRadius
                            while (x <= center.x + vesselRadius) {
                                lineTo(x, level + kotlin.math.sin((x - center.x) / vesselRadius * 3f + animatedPulse * 6f) * 3f)
                                x += 4f
                            }
                            lineTo(center.x + vesselRadius, center.y + vesselRadius)
                            lineTo(center.x - vesselRadius, center.y + vesselRadius)
                            close()
                        }
                        drawPath(waveLine, accent.copy(alpha = .5f))
                    }
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

@Composable
internal fun watchFaceDescription(style: ClockStyle): String = stringResource(
    when (style) {
        ClockStyle.CLASSIC -> R.string.settings_face_classic
        ClockStyle.MINIMAL -> R.string.settings_face_minimal
        ClockStyle.FOCUS -> R.string.settings_face_focus
        ClockStyle.BOLD -> R.string.settings_face_bold
        ClockStyle.NIGHT -> R.string.settings_face_night
        ClockStyle.RETRO -> R.string.settings_face_retro
        ClockStyle.AURORA -> R.string.settings_face_aurora
        ClockStyle.PULSE -> R.string.settings_face_pulse
        ClockStyle.DIAL -> R.string.settings_face_dial
        ClockStyle.STRAND -> R.string.settings_face_strand
        ClockStyle.PRISM -> R.string.settings_face_prism
        ClockStyle.SAND -> R.string.settings_face_sand
        ClockStyle.BLOCKS -> R.string.settings_face_blocks
        ClockStyle.ORBIT -> R.string.settings_face_orbit
        ClockStyle.TIDE -> R.string.settings_face_tide
    },
)

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
    val labels = dayLabels()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { day, label ->
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
