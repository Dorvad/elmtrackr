package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.notification.ReminderRule
import com.elmtrackr.app.notification.ReminderRulesCodec
import com.elmtrackr.app.notification.ReminderTriggerKind
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.Spacing
import java.util.Locale

/**
 * Monday-automations-style sentence editor for the reminder schedule:
 * "Remind me [30 min] [before overtime] and [every hour] [after overtime]".
 * Each bracketed chunk is a dropdown chip; "+ and" appends another sentence.
 * State is hoisted (see [ReminderRulesViewModel]) so screenshot tests can
 * render the screen without a ViewModel store.
 */
@Composable
internal fun ReminderScheduleSection(
    enabled: Boolean,
    rules: List<ReminderRule>,
    onAddRule: () -> Unit,
    onUpdateRule: (ReminderRule) -> Unit,
    onRemoveRule: (String) -> Unit,
) {
    if (!enabled) return

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSubsectionLabel(stringResource(R.string.settings_reminder_schedule_title))
        Spacer(Modifier.height(Spacing.sm))
        rules.forEachIndexed { index, rule ->
            ReminderRuleSentence(
                rule = rule,
                leadIn = if (index == 0) {
                    stringResource(R.string.settings_remind_me)
                } else {
                    stringResource(R.string.settings_rule_and)
                },
                canRemove = rules.size > 1,
                onUpdate = onUpdateRule,
                onRemove = { onRemoveRule(rule.id) },
            )
        }
        if (rules.size < ReminderRulesCodec.MAX_RULES) {
            TextButton(onClick = onAddRule) {
                Text(
                    stringResource(R.string.settings_rule_add_and),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            stringResource(R.string.settings_reminder_schedule_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderRuleSentence(
    rule: ReminderRule,
    leadIn: String,
    canRemove: Boolean,
    onUpdate: (ReminderRule) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                leadIn,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            RuleValueChip(rule = rule, onUpdate = onUpdate)
            RuleKindChip(rule = rule, onUpdate = onUpdate)
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.settings_rule_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RuleKindChip(rule: ReminderRule, onUpdate: (ReminderRule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SentenceChip(text = kindLabel(rule.kind), onClick = { expanded = true }) {
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReminderTriggerKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kindLabel(kind)) },
                    onClick = {
                        expanded = false
                        if (kind != rule.kind) {
                            onUpdate(
                                when (kind) {
                                    ReminderTriggerKind.BEFORE_OVERTIME ->
                                        rule.copy(kind = kind, offsetMinutes = 30)
                                    ReminderTriggerKind.AFTER_OVERTIME ->
                                        rule.copy(kind = kind, offsetMinutes = 60)
                                    ReminderTriggerKind.AT_TIME ->
                                        rule.copy(kind = kind, timeMinuteOfDay = ReminderRule.DEFAULT_TIME_MINUTE_OF_DAY)
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleValueChip(rule: ReminderRule, onUpdate: (ReminderRule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    when (rule.kind) {
        ReminderTriggerKind.BEFORE_OVERTIME -> {
            SentenceChip(text = beforeValueLabel(rule.offsetMinutes), onClick = { expanded = true }) {
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    BEFORE_OPTIONS_MINUTES.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(beforeValueLabel(minutes)) },
                            onClick = {
                                expanded = false
                                onUpdate(rule.copy(offsetMinutes = minutes))
                            },
                        )
                    }
                }
            }
        }

        ReminderTriggerKind.AFTER_OVERTIME -> {
            SentenceChip(text = afterValueLabel(rule.offsetMinutes), onClick = { expanded = true }) {
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    AFTER_OPTIONS_MINUTES.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(afterValueLabel(minutes)) },
                            onClick = {
                                expanded = false
                                onUpdate(rule.copy(offsetMinutes = minutes))
                            },
                        )
                    }
                }
            }
        }

        ReminderTriggerKind.AT_TIME -> {
            SentenceChip(text = formatMinuteOfDay(rule.timeMinuteOfDay), onClick = { showTimePicker = true })
            if (showTimePicker) {
                val pickerState = rememberTimePickerState(
                    initialHour = rule.timeMinuteOfDay / 60,
                    initialMinute = rule.timeMinuteOfDay % 60,
                    is24Hour = true,
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text(stringResource(R.string.settings_rule_pick_time)) },
                    text = { TimePicker(state = pickerState) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showTimePicker = false
                                onUpdate(rule.copy(timeMinuteOfDay = pickerState.hour * 60 + pickerState.minute))
                            },
                        ) { Text(stringResource(R.string.settings_rule_time_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.settings_rule_time_cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SentenceChip(
    text: String,
    onClick: () -> Unit,
    dropdownContent: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .background(AuroraIndigo.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AuroraIndigo,
        )
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = AuroraIndigo,
            modifier = Modifier.size(18.dp),
        )
        dropdownContent()
    }
}

@Composable
private fun kindLabel(kind: ReminderTriggerKind): String = stringResource(
    when (kind) {
        ReminderTriggerKind.BEFORE_OVERTIME -> R.string.settings_rule_before_overtime
        ReminderTriggerKind.AFTER_OVERTIME -> R.string.settings_rule_after_overtime
        ReminderTriggerKind.AT_TIME -> R.string.settings_rule_at_specific_time
    },
)

@Composable
private fun beforeValueLabel(minutes: Int): String = when (minutes) {
    60 -> stringResource(R.string.settings_rule_one_hour)
    else -> stringResource(R.string.settings_rule_minutes_short, minutes)
}

@Composable
private fun afterValueLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.settings_rule_when_it_starts)
    minutes == 60 -> stringResource(R.string.settings_rule_every_hour)
    minutes % 60 == 0 -> stringResource(R.string.settings_rule_every_n_hours, minutes / 60)
    else -> stringResource(R.string.settings_rule_every_minutes, minutes)
}

private fun formatMinuteOfDay(minuteOfDay: Int): String =
    String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

private val BEFORE_OPTIONS_MINUTES = listOf(10, 15, 30, 45, 60, 90)
private val AFTER_OPTIONS_MINUTES = listOf(0, 30, 60, 120)
