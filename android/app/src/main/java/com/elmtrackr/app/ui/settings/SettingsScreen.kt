@file:OptIn(ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraSurfaceSub
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.components.states.LoadingState
import androidx.compose.foundation.layout.widthIn
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import java.time.Instant

private val SUPPORTED_CLOCK_STYLES = ClockStyle.entries

private fun minutesToHours(minutes: Int): String {
    val h = minutes / 60.0
    return if (h == h.toLong().toDouble()) h.toLong().toString() else "%.2f".format(h)
}

private fun supportedClockStyleOf(style: ClockStyle): ClockStyle = style

private val THEME_OPTIONS = listOf("system" to "System default", "light" to "Light", "dark" to "Dark")
private val DAY_LABELS    = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    authState: AuthUiState?      = null,
    onSignOut: () -> Unit        = {},
    onReplayOnboarding: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureSettingsExist() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val state = uiState) {
                is SettingsUiState.Loading -> Column(
                    Modifier
                        .widthIn(max = 448.dp)
                        .fillMaxWidth()
                        .padding(Spacing.md),
                ) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Spacing.md))
                    SettingsSkeleton()
                }
                is SettingsUiState.Ready -> SettingsContent(
                state         = state,
                authState     = authState,
                onSave        = viewModel::saveSettings,
                onSignOut     = onSignOut,
                onFeatureFlag = viewModel::updateFeatureFlag,
                onWeekendDays = viewModel::updateWeekendDays,
                onTheme       = viewModel::saveTheme,
                onSync        = viewModel::triggerSync,
                onResetPassword = viewModel::resetPassword,
                onReplayOnboarding = onReplayOnboarding,
            )
            is SettingsUiState.Error  -> Box(
                Modifier
                    .widthIn(max = 448.dp)
                    .fillMaxWidth()
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(message = state.message, onRetry = viewModel::ensureSettingsExist)
            }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Ready,
    authState: AuthUiState?,
    onSave: (String, Double, Double, Double?, String, ClockStyle, CurrencyCode) -> Unit,
    onSignOut: () -> Unit,
    onFeatureFlag: (FeatureFlag, Boolean) -> Unit,
    onWeekendDays: (List<Int>) -> Unit,
    onTheme: (String) -> Unit,
    onSync: () -> Unit,
    onResetPassword: () -> Unit,
    onReplayOnboarding: () -> Unit,
) {
    var displayName   by remember(state.profile?.fullName)                       { mutableStateOf(state.profile?.fullName ?: "") }
    var dailyOtText   by remember(state.settings.dailyOvertimeThresholdMinutes)  { mutableStateOf(minutesToHours(state.settings.dailyOvertimeThresholdMinutes)) }
    var weeklyOtText  by remember(state.settings.weeklyOvertimeThresholdMinutes) { mutableStateOf(minutesToHours(state.settings.weeklyOvertimeThresholdMinutes)) }
    var hourlyRateText by remember(state.settings.hourlyRate)                    { mutableStateOf(state.settings.hourlyRate?.toString() ?: "") }
    var timezone      by remember(state.settings.timezone)                       { mutableStateOf(state.settings.timezone) }
    var clockStyle    by remember(state.settings.clockStyle)                     { mutableStateOf(supportedClockStyleOf(state.settings.clockStyle)) }
    var currency      by remember(state.settings.currency)                       { mutableStateOf(state.settings.currency) }

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 448.dp)
            .fillMaxWidth()
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { Spacer(Modifier.height(Spacing.lg)) }
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item { Spacer(Modifier.height(Spacing.md)) }
        item {
            SettingsSectionCard("Profile") {
                OutlinedTextField(
                    value         = displayName,
                    onValueChange = { displayName = it },
                    label         = { Text("Display name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                state.profile?.let { profile ->
                    Spacer(Modifier.height(8.dp))
                    Text(profile.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SettingsSectionCard("Appearance") {
                ThemeDropdown(selected = state.selectedTheme, onSelect = onTheme)
                if (state.settings.featuresClockStyles) {
                    Spacer(Modifier.height(12.dp))
                    ClockStyleDropdown(selected = clockStyle, onSelect = { clockStyle = it })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                    Text("Review feature setup")
                }
            }
        }

        item {
            SettingsSectionCard("Overtime Thresholds") {
                HoursField(
                    label         = "Daily overtime (hours)",
                    value         = dailyOtText,
                    onValueChange = { dailyOtText = it },
                    error         = state.validationErrors["dailyOt"],
                )
                Spacer(Modifier.height(8.dp))
                HoursField(
                    label         = "Weekly overtime (hours)",
                    value         = weeklyOtText,
                    onValueChange = { weeklyOtText = it },
                    error         = state.validationErrors["weeklyOt"],
                )
            }
        }

        item {
            SettingsSectionCard("Weekend Days") {
                WeekendDaysSelector(selected = state.settings.weekendDays, onChange = onWeekendDays)
            }
        }

        item {
            SettingsSectionCard("Payroll") {
                HoursField(
                    label         = "Hourly rate",
                    value         = hourlyRateText,
                    onValueChange = { hourlyRateText = it },
                    error         = state.validationErrors["hourlyRate"],
                )
                Spacer(Modifier.height(12.dp))
                CurrencyDropdown(selected = currency, onSelect = { currency = it })
            }
        }

        item {
            SettingsSectionCard("Location") {
                CountryTimezoneDropdown(timezone = timezone, onSelect = { timezone = it })
            }
        }

        item {
            SettingsSectionCard("Features") {
                ToggleRow(
                    title         = "Travel Refunds",
                    description   = "Track and manage travel refund claims",
                    checked       = state.settings.featuresTravelRefunds,
                    onCheckedChange = { onFeatureFlag(FeatureFlag.TRAVEL_REFUNDS, it) },
                )
                ToggleRow(
                    title         = "Tip Calculator",
                    description   = "Coming soon — tip tracking is not available yet",
                    checked       = false,
                    onCheckedChange = {},
                    enabled       = false,
                )
                ToggleRow(
                    title         = "Insights",
                    description   = "View trends and patterns in your work history",
                    checked       = state.settings.featuresInsights,
                    onCheckedChange = { onFeatureFlag(FeatureFlag.INSIGHTS, it) },
                )
                ToggleRow(
                    title         = "Clock Styles",
                    description   = "Choose from different clock display styles",
                    checked       = state.settings.featuresClockStyles,
                    onCheckedChange = { onFeatureFlag(FeatureFlag.CLOCK_STYLES, it) },
                )
            }
        }

        item {
            ElmGradientButton(
                onClick  = {
                    onSave(
                        displayName,
                        dailyOtText.toDoubleOrNull() ?: 0.0,
                        weeklyOtText.toDoubleOrNull() ?: 0.0,
                        hourlyRateText.toDoubleOrNull(),
                        timezone,
                        clockStyle,
                        currency,
                    )
                },
                enabled  = !state.isSaving,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Text(if (state.isSaving) "Saving..." else "Save Settings", fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            SettingsSectionCard("Sync") {
                InfoRow("Pending changes", state.pendingCount.toString())
                InfoRow("Last sync",       state.lastSyncStatus ?: "Never")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = onSync,
                    enabled  = !state.isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isSyncing) "Syncing..." else "Sync Now")
                }
            }
        }

        if (authState != null) {
            item {
                SettingsSectionCard("Account") {
                    AccountSection(
                        authState       = authState,
                        onResetPassword = onResetPassword,
                        onSignOut       = onSignOut,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// â”€â”€ Section card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElmCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            ElmSectionHeader(title, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// â”€â”€ Theme / clock dropdowns â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ThemeDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label    = THEME_OPTIONS.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = label,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Theme") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            THEME_OPTIONS.forEach { (value, display) ->
                DropdownMenuItem(text = { Text(display) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun CurrencyDropdown(selected: CurrencyCode, onSelect: (CurrencyCode) -> Unit) {
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
private fun ClockStyleDropdown(selected: ClockStyle, onSelect: (ClockStyle) -> Unit) {
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
                    Card(
                        onClick = { onSelect(style) },
                        modifier = Modifier.weight(1f).then(
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
        animationSpec = infiniteRepeatable(tween(if (selected) 900 else 1800), RepeatMode.Reverse),
        label = "watch-pulse",
    )
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
                    drawCircle(Color.White.copy(alpha = if (i % 3 == 0) pulse else .35f), 1.5f, Offset((i * 31 % 97) / 100f * size.width, (i * 47 % 89) / 100f * size.height))
                }
                ClockStyle.RETRO -> drawRoundRect(accent.copy(alpha = .55f), style = Stroke(2f))
                ClockStyle.PULSE -> repeat(3) { i ->
                    drawCircle(accent.copy(alpha = pulse / (i + 2)), radius * (.55f + i * .28f), center, style = Stroke(2f))
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

private fun watchFaceDescription(style: ClockStyle): String = when (style) {
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
}

@Composable
private fun CountryTimezoneDropdown(timezone: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val country = COUNTRY_TIMEZONES.firstOrNull { it.second == timezone }?.first
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = country ?: timezone,
            onValueChange = {},
            readOnly = true,
            label = { Text("Country") },
            supportingText = { Text("Timezone: $timezone") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            COUNTRY_TIMEZONES.forEach { (name, zone) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(zone); expanded = false },
                )
            }
        }
    }
}

// â”€â”€ Hours field â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun HoursField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
) {
    OutlinedTextField(
        value          = value,
        onValueChange  = onValueChange,
        label          = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine     = true,
        isError        = error != null,
        supportingText = error?.let { msg -> { Text(msg) } },
        modifier       = Modifier.fillMaxWidth(),
    )
}

// â”€â”€ Weekend days â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekendDaysSelector(selected: List<Int>, onChange: (List<Int>) -> Unit) {
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
private fun WeekendDayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(CornerRadius.Small)
    val background = if (selected) AuroraIndigo else AuroraSurfaceSub
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

// â”€â”€ Toggle row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// â”€â”€ Info row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
    }
}

// â”€â”€ Account section â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun AccountSection(
    authState: AuthUiState,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit,
) {
    var showSignOutConfirm by remember { mutableStateOf(false) }
    if (showSignOutConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?") },
            text = { Text("Your local changes are kept and will sync the next time you sign in.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSignOutConfirm = false; onSignOut() }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") } },
        )
    }
    when (authState) {
        is AuthUiState.NotConfigured -> Text(
            text  = "Running in local-only mode (Supabase not configured).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is AuthUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        is AuthUiState.SignedIn -> {
            Text(authState.profile.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            authState.profile.fullName?.let { name ->
                Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onResetPassword, modifier = Modifier.fillMaxWidth()) {
                Text("Reset password")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = { showSignOutConfirm = true },
                enabled  = !authState.isLoading,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (authState.isLoading) "Signing out..." else "Sign out")
            }
        }
        is AuthUiState.SignedOut       -> Text("Not signed in.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is AuthUiState.PasswordResetSent -> Text("Password reset email sent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is AuthUiState.SignUpConfirmation -> Text("Confirmation sent to ${authState.email}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ElmTrackrTheme {
        SettingsContent(
            state = SettingsUiState.Ready(
                settings = UserSettings(id = "s1", userId = "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
            ),
            authState       = AuthUiState.NotConfigured,
            onSave          = { _, _, _, _, _, _, _ -> },
            onSignOut       = {},
            onFeatureFlag   = { _, _ -> },
            onWeekendDays   = {},
            onTheme         = {},
            onSync          = {},
            onResetPassword = {},
            onReplayOnboarding = {},
        )
    }
}

