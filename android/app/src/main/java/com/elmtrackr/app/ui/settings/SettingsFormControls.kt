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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.design.ElmSegmentedPillRow
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.language.AppLanguage
import com.elmtrackr.app.ui.design.ElmDropdownField
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout
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
        ClockStyle.SPROUT -> R.string.clock_style_sprout
        ClockStyle.METRO -> R.string.clock_style_metro
        ClockStyle.VINYL -> R.string.clock_style_vinyl
        ClockStyle.LUNA -> R.string.clock_style_luna
        ClockStyle.SUMMIT -> R.string.clock_style_summit
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

/**
 * Formats stored minutes for the editable hours fields.
 *
 * Locale.ROOT because the value round-trips through [String.toDoubleOrNull], which only
 * accepts a `.` separator. Formatting with the device locale wrote "8,60" on a
 * comma-locale device, which then parsed to null and was rejected as "must be positive" —
 * the app refusing to save the value it had just produced.
 */
internal fun minutesToHours(minutes: Int): String {
    val h = minutes / 60.0
    return if (h == h.toLong().toDouble()) {
        h.toLong().toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.2f", h)
    }
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
    paidProjects: Boolean,
    insights: Boolean,
    clockStyles: Boolean,
    overtimeReminders: Boolean,
): String {
    val enabled = listOfNotNull(
        stringResource(R.string.settings_feature_travel_short).takeIf { travelRefunds },
        stringResource(R.string.settings_feature_paid_projects_short).takeIf { paidProjects },
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

/**
 * Light / dark / follow-the-device, as a segmented control.
 *
 * A segmented control rather than the filter chips this used to be: three
 * mutually exclusive options is exactly what [ElmSegmentedPillRow] is for, and
 * chips are Material's multi-select filtering component — a screen reader
 * announced three independent buttons where the user was making one choice.
 * The rest of the app already uses the pill row, which announces
 * "selected, 1 of 3".
 *
 * The hint under it resolves "System", which is otherwise the one option that
 * does not say what it does. Someone who cannot tell whether the app is
 * following the device or is simply set to light has no way to find out from
 * three words.
 */
@Composable
internal fun ThemeSegmentedControl(selected: String, onSelect: (String) -> Unit) {
    val options = themeOptions()
    Column {
        Text(
            stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.s8))
        ElmSegmentedPillRow(
            options = options.map { it.second },
            selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
            onSelect = { onSelect(options[it].first) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (selected == "system") {
            Spacer(Modifier.height(Spacing.s8))
            val resolved = stringResource(
                if (isSystemInDarkTheme()) R.string.settings_theme_dark_lower
                else R.string.settings_theme_light_lower,
            )
            Text(
                stringResource(R.string.settings_theme_follows_device, resolved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The language autonyms, written in their own language on purpose. */
internal fun AppLanguage.autonym(): String? = when (this) {
    AppLanguage.SYSTEM -> null
    AppLanguage.ENGLISH -> "English"
    AppLanguage.HEBREW -> "עברית"
    AppLanguage.ARABIC -> "العربية"
}

/**
 * The name to show for the current choice — the autonym, or for the device
 * setting the autonym of whatever it currently resolves to.
 */
@Composable
internal fun languageSummary(): String {
    val stored = AppLanguage.current()
    val drawn = AppLanguage.forLanguageCode(LocalConfiguration.current.locales[0]?.language)
    val drawnName = drawn.autonym() ?: "English"
    return stored.autonym()
        ?: stringResource(R.string.settings_language_system_resolved, drawnName)
}

/**
 * In-app language picker, as a single-choice list.
 *
 * A list rather than the chip row this used to be. Four options did not fit a
 * phone's width and wrapped; chips are Material's multi-select filtering
 * component, so a screen reader met four independent buttons rather than one
 * choice of four; and a chip has no room to say what "Device language" resolves
 * to, which is the one option whose effect is not obvious from its name.
 *
 * Applying a language recreates the activity, so there is no state to hoist —
 * the selection is read back from the per-app locale APIs on the next
 * composition.
 */
@Composable
internal fun LanguageChoiceList() {
    val context = LocalContext.current
    val stored = AppLanguage.current()
    // Read off the configuration rather than the stored choice, so the device
    // option can name the language actually on screen.
    val drawn = AppLanguage.forLanguageCode(LocalConfiguration.current.locales[0]?.language)
    SettingsChoiceGroup {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_language_system),
            subtitle = drawn.autonym(),
            selected = stored == AppLanguage.SYSTEM,
            onSelect = { AppLanguage.apply(context, AppLanguage.SYSTEM) },
        )
        listOf(AppLanguage.ENGLISH, AppLanguage.HEBREW, AppLanguage.ARABIC).forEach { language ->
            SettingsChoiceRow(
                title = language.autonym().orEmpty(),
                selected = stored == language,
                onSelect = { AppLanguage.apply(context, language) },
                // The device option can resolve to this language too, so the row
                // that is merely *in use* is marked apart from the one that is
                // selected. Without it, choosing "Device language" on a Hebrew
                // phone leaves the Hebrew row looking unrelated to what is on
                // screen.
                trailingLabel = stringResource(R.string.settings_language_current)
                    .takeIf { stored == AppLanguage.SYSTEM && drawn == language },
            )
            FilterChip(
                selected = current == AppLanguage.RUSSIAN,
                onClick = { AppLanguage.apply(context, AppLanguage.RUSSIAN) },
                label = { Text("Русский") },
            )
        }
    }
}

@Composable
internal fun ThemeDropdown(selected: String, onSelect: (String) -> Unit) {
    // Options carry (stored value, display name); the stored value is what the
    // preference holds, so the lookup falls back to it rather than showing blank.
    val options = themeOptions()
    ElmDropdownField(
        label = stringResource(R.string.settings_theme),
        selected = selected,
        options = options.map { it.first },
        onSelect = onSelect,
        displayName = { value -> options.firstOrNull { it.first == value }?.second ?: value },
    )
}

@Composable
internal fun CurrencyDropdown(selected: CurrencyCode, onSelect: (CurrencyCode) -> Unit) {
    ElmDropdownField(
        label = stringResource(R.string.settings_currency),
        selected = selected,
        options = CurrencyCode.entries,
        onSelect = onSelect,
        displayName = { "${it.symbol}  ${it.name} - ${currencyDisplayName(it)}" },
    )
}

@Composable
internal fun WatchFacePreview(
    style: ClockStyle,
    selected: Boolean,
    height: Dp = Layout.facePreviewHeight,
) {
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
    val darkFace = style in listOf(ClockStyle.BOLD, ClockStyle.NIGHT, ClockStyle.RETRO, ClockStyle.VINYL)
    val faceBackground = if (darkFace) Color(0xFF11162A) else MaterialTheme.colorScheme.surface
    val accent = when (style) {
        ClockStyle.NIGHT, ClockStyle.TIDE, ClockStyle.VINYL -> Color(0xFF54D8E1)
        ClockStyle.RETRO -> Color(0xFFFFC857)
        ClockStyle.PRISM, ClockStyle.AURORA -> Color(0xFF9B7CFF)
        ClockStyle.SPROUT -> Color(0xFF2E9E6B)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier.fillMaxWidth().height(height).background(faceBackground, RoundedCornerShape(CornerRadius.Medium)),
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
                ClockStyle.RETRO -> {
                    // Four flip cards with their centre seam; the time overlays them.
                    val cardW = size.width * .17f
                    val cardH = size.height * .66f
                    val gap = size.width * .03f
                    val startX = (size.width - (cardW * 4 + gap * 3)) / 2f
                    val cardY = center.y - cardH / 2f
                    repeat(4) { i ->
                        val x = startX + i * (cardW + gap)
                        drawRoundRect(
                            Color.Black.copy(alpha = .4f),
                            Offset(x, cardY),
                            Size(cardW, cardH),
                            androidx.compose.ui.geometry.CornerRadius(5f),
                        )
                        drawRoundRect(
                            accent.copy(alpha = .3f),
                            Offset(x, cardY),
                            Size(cardW, cardH),
                            androidx.compose.ui.geometry.CornerRadius(5f),
                            style = Stroke(1.5f),
                        )
                        drawLine(Color.Black.copy(alpha = .7f), Offset(x, center.y), Offset(x + cardW, center.y), 2f)
                    }
                }
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
                ClockStyle.SPROUT -> {
                    val groundY = size.height - 6.dp.toPx()
                    drawLine(accent.copy(alpha = .3f), Offset(center.x - radius, groundY), Offset(center.x + radius, groundY), 2f, StrokeCap.Round)
                    val top = Offset(center.x, groundY - size.height * .62f)
                    drawLine(accent, Offset(center.x, groundY), top, 2.5f, StrokeCap.Round)
                    drawOval(
                        accent.copy(alpha = .85f),
                        Offset(center.x - 11.dp.toPx(), groundY - size.height * .25f - 4.dp.toPx()),
                        Size(11.dp.toPx(), 5.dp.toPx()),
                    )
                    drawOval(
                        accent.copy(alpha = .85f),
                        Offset(center.x, groundY - size.height * .42f - 4.dp.toPx()),
                        Size(11.dp.toPx(), 5.dp.toPx()),
                    )
                    drawCircle(Color(0xFFB07CF8).copy(alpha = .45f + animatedPulse * .4f), 6.dp.toPx(), top)
                    drawCircle(Color(0xFFFFC857), 2.5.dp.toPx(), top)
                }
                ClockStyle.METRO -> {
                    // A short stretch of line, three stations, and the train.
                    val y = center.y + radius * .45f
                    val line = Path().apply {
                        moveTo(6.dp.toPx(), y + 8.dp.toPx())
                        quadraticTo(center.x, y + 8.dp.toPx(), center.x + radius * .5f, y - 2.dp.toPx())
                        quadraticTo(size.width - 24.dp.toPx(), y - 10.dp.toPx(), size.width - 8.dp.toPx(), y - 16.dp.toPx())
                    }
                    drawPath(line, accent.copy(alpha = .2f), style = Stroke(3.5f, cap = StrokeCap.Round))
                    val stops = listOf(
                        Offset(14.dp.toPx(), y + 8.dp.toPx()) to true,
                        Offset(center.x, y + 4.dp.toPx()) to true,
                        Offset(size.width - 14.dp.toPx(), y - 14.dp.toPx()) to false,
                    )
                    stops.forEach { (stop, passed) ->
                        if (passed) drawCircle(accent, 3.5f, stop)
                        else drawCircle(accent.copy(alpha = .35f), 3.5f, stop, style = Stroke(2f))
                    }
                    drawRoundRect(
                        accent,
                        Offset(center.x - 7.dp.toPx(), y - 1.dp.toPx()),
                        Size(14.dp.toPx(), 7.dp.toPx()),
                        androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx()),
                    )
                }
                ClockStyle.VINYL -> {
                    val discRadius = size.minDimension * .42f
                    drawCircle(Color(0xFF211D3E), discRadius, center)
                    repeat(4) { i ->
                        drawCircle(
                            Color.White.copy(alpha = .1f),
                            discRadius * (.45f + i * .13f), center, style = Stroke(1.5f),
                        )
                    }
                    drawCircle(accent.copy(alpha = .4f), discRadius * .71f, center, style = Stroke(1.5f))
                    drawCircle(Color(0xFF5B4DF2), discRadius * .32f, center)
                    drawCircle(Color(0xFF11162A), 2.5f, center)
                    val pivot = Offset(size.width - 8.dp.toPx(), 6.dp.toPx())
                    val needle = Offset(center.x + discRadius * .5f, center.y - discRadius * .55f)
                    drawLine(Color.White.copy(alpha = .6f), pivot, needle, 2f, StrokeCap.Round)
                    drawCircle(accent.copy(alpha = .3f + animatedPulse * .4f), 4f, needle)
                }
                ClockStyle.LUNA -> {
                    val moonRadius = size.minDimension * .38f
                    drawCircle(accent.copy(alpha = .12f + animatedPulse * .08f), moonRadius + 6.dp.toPx(), center)
                    drawCircle(Color(0xFFF1F0FB), moonRadius, center)
                    drawCircle(Color(0xFF181530).copy(alpha = .1f), moonRadius, center, style = Stroke(1.5f))
                    // A waxing gibbous: right semicircle plus a bulging terminator.
                    val lit = Path().apply {
                        arcTo(
                            androidx.compose.ui.geometry.Rect(
                                center.x - moonRadius, center.y - moonRadius,
                                center.x + moonRadius, center.y + moonRadius,
                            ),
                            -90f, 180f, false,
                        )
                        arcTo(
                            androidx.compose.ui.geometry.Rect(
                                center.x - moonRadius * .5f, center.y - moonRadius,
                                center.x + moonRadius * .5f, center.y + moonRadius,
                            ),
                            90f, 180f, false,
                        )
                        close()
                    }
                    drawPath(lit, Color(0xFFC4B8FA))
                    val star = Offset(center.x + moonRadius + 8.dp.toPx(), center.y - moonRadius)
                    val arm = 3.dp.toPx() * (.6f + animatedPulse * .4f)
                    drawLine(accent, star - Offset(arm, 0f), star + Offset(arm, 0f), 1.5f, StrokeCap.Round)
                    drawLine(accent, star - Offset(0f, arm), star + Offset(0f, arm), 1.5f, StrokeCap.Round)
                }
                ClockStyle.SUMMIT -> {
                    val baseY = size.height * .8f
                    val peak = Offset(size.width * .58f, size.height * .22f)
                    fun Path.trailUp() {
                        moveTo(size.width * .12f, baseY)
                        quadraticTo(size.width * .3f, size.height * .6f, size.width * .42f, size.height * .52f)
                        quadraticTo(size.width * .5f, size.height * .44f, peak.x, peak.y)
                    }
                    val body = Path().apply {
                        trailUp()
                        quadraticTo(size.width * .68f, size.height * .46f, size.width * .84f, baseY)
                        close()
                    }
                    drawPath(body, accent.copy(alpha = .12f))
                    drawPath(
                        Path().apply { trailUp() },
                        accent.copy(alpha = .45f),
                        style = Stroke(2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f))),
                    )
                    drawCircle(accent, 2.5f, Offset(size.width * .28f, size.height * .61f))
                    drawCircle(accent.copy(alpha = .35f), 2.5f, Offset(size.width * .47f, size.height * .46f))
                    drawLine(accent, peak, Offset(peak.x, peak.y - 9f), 2f, StrokeCap.Round)
                    val pennant = Path().apply {
                        moveTo(peak.x, peak.y - 9f)
                        lineTo(peak.x + 6f, peak.y - 6.5f)
                        lineTo(peak.x, peak.y - 4f)
                        close()
                    }
                    drawPath(pennant, accent)
                    drawCircle(
                        accent.copy(alpha = .4f + animatedPulse * .5f),
                        3f,
                        Offset(size.width * .36f, size.height * .55f),
                    )
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
        ClockStyle.SPROUT -> R.string.settings_face_sprout
        ClockStyle.METRO -> R.string.settings_face_metro
        ClockStyle.VINYL -> R.string.settings_face_vinyl
        ClockStyle.LUNA -> R.string.settings_face_luna
        ClockStyle.SUMMIT -> R.string.settings_face_summit
    },
)

/**
 * Keeps a decimal field parseable.
 *
 * Every value typed here is read back with [String.toDoubleOrNull], which accepts only a
 * `.` separator. Unfiltered input let "50," or "50 " through: the rate then parsed to null
 * and was saved as "no rate", silently zeroing all pay figures with no error shown. A comma
 * is normalised rather than rejected because that is the separator key on many keyboards.
 */
internal fun sanitizeDecimalInput(raw: String, current: String): String {
    val normalized = raw.replace(',', '.')
    if (normalized.isEmpty()) return normalized
    if (!normalized.all { it.isDigit() || it == '.' }) return current
    if (normalized.count { it == '.' } > 1) return current
    return normalized
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
        onValueChange = { onValueChange(sanitizeDecimalInput(it, value)) },
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
            // Multi-select, so Checkbox rather than RadioButton; the minimum
            // size keeps a text-sized chip reachable.
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .minimumInteractiveComponentSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = textColor,
    )
}
