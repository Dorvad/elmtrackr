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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.compensation.COMPENSATION_DISCLAIMER
import com.elmtrackr.app.domain.compensation.COMPENSATION_PROFILE_HELPER
import com.elmtrackr.app.domain.compensation.COMPENSATION_RULES_GUIDANCE
import com.elmtrackr.app.domain.compensation.STACKING_POLICY_ADDITIVE_HELPER
import com.elmtrackr.app.domain.compensation.STACKING_POLICY_HIGHEST_ONLY_HELPER
import com.elmtrackr.app.domain.model.CompensationRules
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
    viewModel: CompensationSettingsViewModel = viewModel(factory = CompensationSettingsViewModel.Factory),
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
private fun CompensationSettingsContent(
    state: CompensationSettingsUiState.Ready,
    onBack: () -> Unit,
    onSave: (
        String, RegionCode, String, String, Double?, StackingPolicy, CompensationRules,
    ) -> Unit,
    onDismissMessage: () -> Unit,
) {
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
            ElmCardPadded {
                Text(
                    COMPENSATION_RULES_GUIDANCE,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(COMPENSATION_PROFILE_HELPER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(COMPENSATION_DISCLAIMER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        rules = preset.rules.copy(
                            weekendDays = rules.weekendDays,
                            dailyStandardMinutes = rules.dailyStandardMinutes,
                            weeklyStandardMinutes = rules.weeklyStandardMinutes,
                        )
                    }
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
                }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == StackingPolicy.HIGHEST_ONLY,
            onClick = { onSelect(StackingPolicy.HIGHEST_ONLY) },
            label = { Text("Highest only") },
        )
        FilterChip(
            selected = selected == StackingPolicy.ADDITIVE,
            onClick = { onSelect(StackingPolicy.ADDITIVE) },
            label = { Text("Additive") },
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        when (selected) {
            StackingPolicy.HIGHEST_ONLY -> STACKING_POLICY_HIGHEST_ONLY_HELPER
            StackingPolicy.ADDITIVE -> STACKING_POLICY_ADDITIVE_HELPER
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
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
