@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.compensation.COMPENSATION_DISCLAIMER
import com.elmtrackr.app.domain.compensation.COMPENSATION_PROFILE_HELPER
import com.elmtrackr.app.domain.compensation.COMPENSATION_RULES_GUIDANCE
import com.elmtrackr.app.domain.compensation.StackingPolicyLabels
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.ElmCardPadded
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun CompensationSettingsScreen(
    onBack: () -> Unit,
    viewModel: CompensationSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    AuroraListScreen {
        when (val state = uiState) {
            is CompensationSettingsUiState.Loading -> BoxCentered { CircularProgressIndicator() }
            is CompensationSettingsUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::ensureLoaded,
            )
            is CompensationSettingsUiState.Ready -> CompensationSettingsContent(
                state = state,
                onBack = onBack,
                onSelectProfile = viewModel::selectProfile,
                onCreateProfile = viewModel::createProfile,
                onSave = viewModel::saveProfile,
                onDismissMessage = viewModel::clearSaveMessage,
            )
        }
    }
}

@Composable
private fun BoxCentered(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().widthIn(max = 448.dp),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
internal fun CompensationSettingsContent(
    state: CompensationSettingsUiState.Ready,
    onBack: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (String) -> Unit,
    onSave: (
        String, RegionCode, String, String, Double?, StackingPolicy, CompensationRules,
    ) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    var name by remember(state.profile.id) { mutableStateOf(state.profile.name) }
    var regionCode by remember(state.profile.id) { mutableStateOf(state.profile.regionCode) }
    var currencyCode by remember(state.profile.id) { mutableStateOf(state.profile.currencyCode) }
    var timezone by remember(state.profile.id) { mutableStateOf(state.profile.timezone) }
    var hourlyRateText by remember(state.profile.id) {
        mutableStateOf(state.profile.baseHourlyRate?.toString().orEmpty())
    }
    var stackingPolicy by remember(state.profile.id) { mutableStateOf(state.profile.stackingPolicy) }
    var rules by remember(state.profile.id) { mutableStateOf(state.profile.rules) }

    LaunchedEffect(state.saveMessage) {
        if (state.saveMessage != null) {
            kotlinx.coroutines.delay(2500)
            onDismissMessage()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Compensation Rules",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item {
            // Keep the intro to one line so the actual controls stay above the
            // fold; the full guidance and disclaimer live at the end of the form.
            Text(
                COMPENSATION_PROFILE_HELPER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            ElmCardPadded {
                ElmSectionHeader("Profiles")
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.id == state.profile.id,
                            onClick = { onSelectProfile(profile.id) },
                            label = { Text(profile.name) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            newProfileName = ""
                            showCreateDialog = true
                        },
                        label = { Text("Add profile") },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Each profile has its own pay rules. Pick one to edit, or add another for a second job.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            ElmCardPadded {
                ElmSectionHeader("Profile")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                RegionDropdown(regionCode, state.presets.map { it.regionCode to it.label }) {
                    regionCode = it
                    state.presets.firstOrNull { p -> p.regionCode == it }?.let { preset ->
                        currencyCode = preset.currencyCode
                        timezone = preset.timezone
                        // Apply the preset in full — keeping the previous region's
                        // standards silently produced wrong overtime thresholds.
                        rules = preset.rules
                    }
                }
                state.presets.firstOrNull { p -> p.regionCode == regionCode }?.let { preset ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                StringDropdown("Currency", currencyCode, state.currencyOptions.map { it.first to it.second }) {
                    currencyCode = it
                }
                Spacer(Modifier.height(12.dp))
                StringDropdown("Timezone", timezone, state.timezoneOptions.map { it to it }) {
                    timezone = it
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hourlyRateText,
                    onValueChange = { hourlyRateText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Base hourly rate ($currencyCode)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
                StackingPolicyRow(stackingPolicy) { stackingPolicy = it }
            }
        }

        item {
            ElmCardPadded {
                ElmSectionHeader("Work week")
                Spacer(Modifier.height(12.dp))
                HoursField("Daily standard (hours)", rules.dailyStandardMinutes) {
                    rules = rules.copy(dailyStandardMinutes = (it * 60).roundToInt())
                }
                Spacer(Modifier.height(8.dp))
                HoursField("Weekly standard (hours)", rules.weeklyStandardMinutes) {
                    rules = rules.copy(weeklyStandardMinutes = (it * 60).roundToInt())
                }
                Spacer(Modifier.height(12.dp))
                StringDropdown(
                    "Week starts on",
                    rules.weekStartDay.toString(),
                    DAY_LABELS.mapIndexed { index, label -> index.toString() to label },
                ) { rules = rules.copy(weekStartDay = it.toIntOrNull()?.coerceIn(0, 6) ?: 1) }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Weekly overtime counts hours from this day. Israel and most US workweeks start Sunday.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("Weekend days", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        FilterChip(
                            selected = index in rules.weekendDays,
                            onClick = {
                                val days = rules.weekendDays.toMutableList()
                                if (index in days) days.remove(index) else days.add(index)
                                rules = rules.copy(weekendDays = days.sorted())
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        item {
            ElmCardPadded {
                ElmSectionHeader("Premiums")
                Spacer(Modifier.height(8.dp))
                ToggleRow("Overtime", rules.overtimeEnabled) { rules = rules.copy(overtimeEnabled = it) }
                if (rules.overtimeEnabled) {
                    overtimeLadderSummary(rules)?.let { summary ->
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    ToggleRow("7th consecutive day premium", rules.seventhDayEnabled) { enabled ->
                        rules = rules.copy(
                            seventhDayEnabled = enabled,
                            seventhDayTiers = if (enabled && rules.seventhDayTiers.isEmpty()) {
                                listOf(OvertimeTier(0, 1.5), OvertimeTier(480, 2.0))
                            } else {
                                rules.seventhDayTiers
                            },
                        )
                    }
                    if (rules.seventhDayEnabled) {
                        Text(
                            "Working all seven days of the pay week pays a premium from the first minute of the 7th day (California-style: 150% first 8 h, 200% after).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ToggleRow("Weekend premium", rules.weekendEnabled) { rules = rules.copy(weekendEnabled = it) }
                if (rules.weekendEnabled) {
                    MultiplierField("Weekend multiplier", rules.weekendMultiplier) {
                        rules = rules.copy(weekendMultiplier = it)
                    }
                }
                ToggleRow("Holiday / special day", rules.holidayEnabled) { rules = rules.copy(holidayEnabled = it) }
                if (rules.holidayEnabled) {
                    MultiplierField("Holiday multiplier", rules.holidayMultiplier) {
                        rules = rules.copy(holidayMultiplier = it)
                    }
                }
                ToggleRow("Night shift", rules.nightEnabled) { rules = rules.copy(nightEnabled = it) }
                if (rules.nightEnabled) {
                    MultiplierField("Night multiplier", rules.nightMultiplier) {
                        rules = rules.copy(nightMultiplier = it)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField("Night starts", rules.nightStartTime, Modifier.weight(1f)) {
                            rules = rules.copy(nightStartTime = it)
                        }
                        TimeField("Night ends", rules.nightEndTime, Modifier.weight(1f)) {
                            rules = rules.copy(nightEndTime = it)
                        }
                    }
                }
            }
        }

        item {
            ElmCardPadded {
                ElmSectionHeader("Time & breaks")
                Spacer(Modifier.height(8.dp))
                ToggleRow("Breaks are paid", rules.paidBreaks) { rules = rules.copy(paidBreaks = it) }
                if (!rules.paidBreaks) {
                    OptionalMinutesField(
                        "Auto-deduct break (minutes)",
                        rules.autoDeductBreakMinutes,
                    ) { rules = rules.copy(autoDeductBreakMinutes = it) }
                    Text(
                        "Deducted automatically when a shift has no recorded break.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                OptionalMinutesField(
                    "Minimum shift pay (minutes)",
                    rules.minimumShiftMinutes,
                ) { rules = rules.copy(minimumShiftMinutes = it) }
                Text(
                    "Shorter shifts are paid as if they lasted this long (minimum-call / reporting-time pay).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow("Round shift length", rules.rounding.enabled) {
                    rules = rules.copy(rounding = rules.rounding.copy(enabled = it))
                }
                if (rules.rounding.enabled) {
                    OptionalMinutesField(
                        "Rounding increment (minutes)",
                        rules.rounding.incrementMinutes,
                    ) { rules = rules.copy(rounding = rules.rounding.copy(incrementMinutes = it ?: 15)) }
                    Spacer(Modifier.height(8.dp))
                    Text("Rounding direction", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("nearest" to "Nearest", "up" to "Up", "down" to "Down").forEach { (value, label) ->
                            FilterChip(
                                selected = rules.rounding.direction == value,
                                onClick = { rules = rules.copy(rounding = rules.rounding.copy(direction = value)) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        item {
            ElmCardPadded {
                ElmSectionHeader("Deductions")
                Spacer(Modifier.height(8.dp))
                ToggleRow("Deduct from gross estimate", rules.deductionsEnabled) { enabled ->
                    rules = rules.copy(
                        deductionsEnabled = enabled,
                        deductionsMode = if (enabled && rules.deductionsMode == "none") "percentage" else rules.deductionsMode,
                    )
                }
                if (rules.deductionsEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rules.deductionsMode == "percentage",
                            onClick = { rules = rules.copy(deductionsMode = "percentage") },
                            label = { Text("Percentage") },
                        )
                        FilterChip(
                            selected = rules.deductionsMode == "fixed",
                            onClick = { rules = rules.copy(deductionsMode = "fixed") },
                            label = { Text("Fixed per shift") },
                        )
                    }
                    if (rules.deductionsMode == "percentage") {
                        MultiplierField("Deduction (%)", rules.deductionsPercentage) {
                            rules = rules.copy(deductionsPercentage = it)
                        }
                    } else {
                        MultiplierField("Deduction ($currencyCode per shift)", rules.deductionsFixedAmount) {
                            rules = rules.copy(deductionsFixedAmount = it)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A rough take-home adjustment (e.g. tax or pension estimate) — not a payroll-grade net calculation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ElmCardPadded {
                Text(
                    COMPENSATION_RULES_GUIDANCE,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    COMPENSATION_DISCLAIMER,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            state.saveMessage?.let { message ->
                Text(
                    message,
                    color = if (message.contains("saved", ignoreCase = true))
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ElmGradientButton(
                onClick = {
                    val rate = hourlyRateText.toDoubleOrNull()
                    onSave(name, regionCode, currencyCode, timezone, rate, stackingPolicy, rules)
                },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSaving) "Saving…" else "Save compensation rules",
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (showCreateDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New compensation profile") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile name") },
                    placeholder = { Text("e.g. Second job, Weekend gig") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateProfile(newProfileName.trim())
                        showCreateDialog = false
                    },
                    enabled = newProfileName.trim().isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RegionDropdown(
    selected: RegionCode,
    options: List<Pair<RegionCode, String>>,
    onSelect: (RegionCode) -> Unit,
) {
    StringDropdown(
        label = "Region preset",
        selected = selected.name,
        options = options.map { it.first.name to it.second },
        onSelect = { onSelect(RegionCode.fromPersisted(it)) },
    )
}

@Composable
private fun StringDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun StackingPolicyRow(selected: StackingPolicy, onSelect: (StackingPolicy) -> Unit) {
    Text("Premium stacking", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    // Same options and wording as custom premium profiles, so both stacking
    // pickers in the app stay aligned.
    val options = (StackingPolicy.selectable + selected).distinct()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { policy ->
            FilterChip(
                selected = selected == policy,
                onClick = { onSelect(policy) },
                label = { Text(StackingPolicyLabels.title(policy)) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        StackingPolicyLabels.helper(selected),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is one toggle target: screen readers announce label and
    // state together, and the touch target spans the full width.
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun HoursField(label: String, minutes: Int, onHours: (Double) -> Unit) {
    var text by remember(minutes) { mutableStateOf((minutes / 60.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.2f".format(it) }) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onHours)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** One-line, read-only view of the configured overtime ladders. */
private fun overtimeLadderSummary(rules: CompensationRules): String? {
    val parts = mutableListOf<String>()
    rules.dailyOvertimeTiers.sortedBy { it.afterMinutes }.forEach {
        parts += "daily after ${ShiftDurationCalculator.formatMinutes(it.afterMinutes)}: ${(it.multiplier * 100).toInt()}%"
    }
    rules.weeklyOvertimeTiers.sortedBy { it.afterMinutes }.forEach {
        parts += "weekly after ${ShiftDurationCalculator.formatMinutes(it.afterMinutes)}: ${(it.multiplier * 100).toInt()}%"
    }
    if (parts.isEmpty()) return null
    return "Current ladder — " + parts.joinToString("; ") + ". Pick a region preset to change tiers."
}

private val TIME_RE = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")

@Composable
private fun TimeField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (TIME_RE.matches(it)) onChange(it)
        },
        label = { Text(label) },
        isError = !TIME_RE.matches(text),
        supportingText = if (!TIME_RE.matches(text)) {
            { Text("Use HH:MM, e.g. 22:00") }
        } else {
            null
        },
        modifier = modifier.padding(top = 8.dp),
        singleLine = true,
    )
}

/** Whole-minute field where blank means "not set". */
@Composable
private fun OptionalMinutesField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }
            text = digits
            onChange(digits.toIntOrNull()?.takeIf { it > 0 })
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun MultiplierField(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
