@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.design.ElmSegmentedPillRow
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.language.AppLanguage
import com.elmtrackr.app.ui.design.ElmDropdownField
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
        ClockStyle.SPROUT -> R.string.clock_style_sprout
        ClockStyle.METRO -> R.string.clock_style_metro
        ClockStyle.VINYL -> R.string.clock_style_vinyl
        ClockStyle.LUNA -> R.string.clock_style_luna
        ClockStyle.SUMMIT -> R.string.clock_style_summit
        ClockStyle.METER -> R.string.clock_style_meter
        ClockStyle.STACKS -> R.string.clock_style_stacks
        ClockStyle.JAR -> R.string.clock_style_jar
        ClockStyle.TICKER -> R.string.clock_style_ticker
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
    AppLanguage.RUSSIAN -> "Русский"
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
        listOf(
            AppLanguage.ENGLISH,
            AppLanguage.HEBREW,
            AppLanguage.ARABIC,
            AppLanguage.RUSSIAN,
        ).forEach { language ->
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
        ClockStyle.METER -> R.string.settings_face_meter
        ClockStyle.STACKS -> R.string.settings_face_stacks
        ClockStyle.JAR -> R.string.settings_face_jar
        ClockStyle.TICKER -> R.string.settings_face_ticker
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
