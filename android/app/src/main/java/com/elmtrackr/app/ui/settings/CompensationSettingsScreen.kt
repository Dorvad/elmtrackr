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
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.compensation.StackingPolicyLabels
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.ui.common.asString
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
                message = state.message.asString(),
                onRetry = viewModel::ensureLoaded,
            )
            is CompensationSettingsUiState.Ready -> CompensationSettingsContent(
                state = state,
                onBack = onBack,
                onSelectProfile = viewModel::selectProfile,
                onCreateProfile = viewModel::createProfile,
                onDeleteProfile = viewModel::deleteProfile,
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
    onDeleteProfile: () -> Unit,
    onSave: (
        String, RegionCode, String, String, Double?, StackingPolicy, CompensationRules,
    ) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                }
                Text(
                    stringResource(R.string.settings_comp_rules_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item {
            // Keep the intro to one line so the actual controls stay above the
            // fold; the full guidance and disclaimer live at the end of the form.
            Text(
                stringResource(R.string.comp_profile_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            ElmCardPadded {
                ElmSectionHeader(stringResource(R.string.settings_section_profiles))
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
                        label = { Text(stringResource(R.string.settings_add_profile)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_comp_profiles_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.profiles.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !state.isSaving,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.settings_delete_profile))
                    }
                }
            }
        }

        item {
            ElmCardPadded {
                ElmSectionHeader(stringResource(R.string.settings_profile))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_profile_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                RegionDropdown(regionCode, state.presets.map { it.regionCode to stringResource(it.labelRes) }) {
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
                        stringResource(preset.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                StringDropdown(
                    stringResource(R.string.settings_currency),
                    currencyCode,
                    CurrencyCode.entries.map { it.name to "${it.symbol}  ${it.name} — ${currencyDisplayName(it)}" },
                ) {
                    currencyCode = it
                }
                Spacer(Modifier.height(12.dp))
                StringDropdown(stringResource(R.string.settings_timezone), timezone, state.timezoneOptions.map { it to it }) {
                    timezone = it
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hourlyRateText,
                    onValueChange = { hourlyRateText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.settings_base_hourly_rate, currencyCode)) },
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
                ElmSectionHeader(stringResource(R.string.settings_section_work_week))
                Spacer(Modifier.height(12.dp))
                HoursField(stringResource(R.string.settings_daily_standard_hours), rules.dailyStandardMinutes) {
                    rules = rules.copy(dailyStandardMinutes = (it * 60).roundToInt())
                }
                Spacer(Modifier.height(8.dp))
                HoursField(stringResource(R.string.settings_weekly_standard_hours), rules.weeklyStandardMinutes) {
                    rules = rules.copy(weeklyStandardMinutes = (it * 60).roundToInt())
                }
                Spacer(Modifier.height(12.dp))
                StringDropdown(
                    stringResource(R.string.settings_week_starts_on),
                    rules.weekStartDay.toString(),
                    dayLabels().mapIndexed { index, label -> index.toString() to label },
                ) { rules = rules.copy(weekStartDay = it.toIntOrNull()?.coerceIn(0, 6) ?: 1) }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_week_starts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_weekend_days), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                val weekdayLabels = dayLabels()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weekdayLabels.forEachIndexed { index, label ->
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
                ElmSectionHeader(stringResource(R.string.settings_section_premiums))
                Spacer(Modifier.height(8.dp))
                ToggleRow(stringResource(R.string.settings_overtime), rules.overtimeEnabled) { rules = rules.copy(overtimeEnabled = it) }
                if (rules.overtimeEnabled) {
                    overtimeLadderSummary(rules)?.let { summary ->
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    ToggleRow(stringResource(R.string.settings_seventh_day), rules.seventhDayEnabled) { enabled ->
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
                            stringResource(R.string.settings_seventh_day_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ToggleRow(stringResource(R.string.settings_weekend_premium), rules.weekendEnabled) { rules = rules.copy(weekendEnabled = it) }
                if (rules.weekendEnabled) {
                    MultiplierField(stringResource(R.string.settings_weekend_multiplier), rules.weekendMultiplier) {
                        rules = rules.copy(weekendMultiplier = it)
                    }
                }
                ToggleRow(stringResource(R.string.settings_holiday_special), rules.holidayEnabled) { rules = rules.copy(holidayEnabled = it) }
                if (rules.holidayEnabled) {
                    MultiplierField(stringResource(R.string.settings_holiday_multiplier), rules.holidayMultiplier) {
                        rules = rules.copy(holidayMultiplier = it)
                    }
                }
                ToggleRow(stringResource(R.string.settings_night_shift), rules.nightEnabled) { rules = rules.copy(nightEnabled = it) }
                if (rules.nightEnabled) {
                    MultiplierField(stringResource(R.string.settings_night_multiplier), rules.nightMultiplier) {
                        rules = rules.copy(nightMultiplier = it)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(stringResource(R.string.settings_night_starts), rules.nightStartTime, Modifier.weight(1f)) {
                            rules = rules.copy(nightStartTime = it)
                        }
                        TimeField(stringResource(R.string.settings_night_ends), rules.nightEndTime, Modifier.weight(1f)) {
                            rules = rules.copy(nightEndTime = it)
                        }
                    }
                }
            }
        }

        item {
            ElmCardPadded {
                ElmSectionHeader(stringResource(R.string.settings_section_time_breaks))
                Spacer(Modifier.height(8.dp))
                ToggleRow(stringResource(R.string.settings_breaks_paid), rules.paidBreaks) { rules = rules.copy(paidBreaks = it) }
                if (!rules.paidBreaks) {
                    OptionalMinutesField(
                        stringResource(R.string.settings_auto_deduct_break),
                        rules.autoDeductBreakMinutes,
                    ) { rules = rules.copy(autoDeductBreakMinutes = it) }
                    Text(
                        stringResource(R.string.settings_auto_deduct_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                OptionalMinutesField(
                    stringResource(R.string.settings_min_shift_pay),
                    rules.minimumShiftMinutes,
                ) { rules = rules.copy(minimumShiftMinutes = it) }
                Text(
                    stringResource(R.string.settings_min_shift_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(stringResource(R.string.settings_round_shift_length), rules.rounding.enabled) {
                    rules = rules.copy(rounding = rules.rounding.copy(enabled = it))
                }
                if (rules.rounding.enabled) {
                    OptionalMinutesField(
                        stringResource(R.string.settings_rounding_increment),
                        rules.rounding.incrementMinutes,
                    ) { rules = rules.copy(rounding = rules.rounding.copy(incrementMinutes = it ?: 15)) }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_rounding_direction), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "nearest" to stringResource(R.string.settings_rounding_nearest),
                            "up" to stringResource(R.string.settings_rounding_up),
                            "down" to stringResource(R.string.settings_rounding_down),
                        ).forEach { (value, label) ->
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
                ElmSectionHeader(stringResource(R.string.settings_section_deductions))
                Spacer(Modifier.height(8.dp))
                ToggleRow(stringResource(R.string.settings_deduct_gross), rules.deductionsEnabled) { enabled ->
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
                            label = { Text(stringResource(R.string.settings_percentage)) },
                        )
                        FilterChip(
                            selected = rules.deductionsMode == "fixed",
                            onClick = { rules = rules.copy(deductionsMode = "fixed") },
                            label = { Text(stringResource(R.string.settings_fixed_per_shift)) },
                        )
                    }
                    if (rules.deductionsMode == "percentage") {
                        MultiplierField(stringResource(R.string.settings_deduction_percent), rules.deductionsPercentage) {
                            rules = rules.copy(deductionsPercentage = it)
                        }
                    } else {
                        MultiplierField(stringResource(R.string.settings_deduction_fixed, currencyCode), rules.deductionsFixedAmount) {
                            rules = rules.copy(deductionsFixedAmount = it)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_deduction_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ElmCardPadded {
                Text(
                    stringResource(R.string.comp_rules_guidance),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.comp_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            state.saveMessage?.let { message ->
                Text(
                    message.text.asString(),
                    color = if (message.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
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
                    if (state.isSaving) stringResource(R.string.settings_saving) else stringResource(R.string.settings_save_comp_rules),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_comp_profile_title, state.profile.name)) },
            text = { Text(stringResource(R.string.settings_delete_comp_profile_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteProfile()
                    },
                ) { Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }

    if (showCreateDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.settings_new_comp_profile)) },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text(stringResource(R.string.settings_profile_name)) },
                    placeholder = { Text(stringResource(R.string.settings_comp_profile_name_placeholder)) },
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
                ) { Text(stringResource(R.string.settings_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.settings_cancel)) }
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
        label = stringResource(R.string.settings_region_preset),
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
    Text(stringResource(R.string.settings_premium_stacking), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    // Same options and wording as custom premium profiles, so both stacking
    // pickers in the app stay aligned.
    val options = (StackingPolicy.selectable + selected).distinct()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { policy ->
            FilterChip(
                selected = selected == policy,
                onClick = { onSelect(policy) },
                label = { Text(stringResource(StackingPolicyLabels.titleRes(policy))) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(StackingPolicyLabels.helperRes(selected)),
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
@Composable
private fun overtimeLadderSummary(rules: CompensationRules): String? {
    val parts = mutableListOf<String>()
    rules.dailyOvertimeTiers.sortedBy { it.afterMinutes }.forEach {
        parts += stringResource(
            R.string.settings_ladder_daily,
            ShiftDurationCalculator.formatMinutes(it.afterMinutes),
            (it.multiplier * 100).toInt(),
        )
    }
    rules.weeklyOvertimeTiers.sortedBy { it.afterMinutes }.forEach {
        parts += stringResource(
            R.string.settings_ladder_weekly,
            ShiftDurationCalculator.formatMinutes(it.afterMinutes),
            (it.multiplier * 100).toInt(),
        )
    }
    if (parts.isEmpty()) return null
    return stringResource(R.string.settings_ladder_summary, parts.joinToString("; "))
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
            { Text(stringResource(R.string.settings_use_hhmm)) }
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
