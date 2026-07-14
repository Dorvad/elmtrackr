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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.notification.ReminderRule
import com.elmtrackr.app.notification.ReminderRulesCodec
import com.elmtrackr.app.notification.ReminderTriggerKind
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.util.Locale

/**
 * Reminder schedule, organised as a list of individual notifications rather
 * than one long "remind me X and Y and Z" sentence. Each row is a saved
 * notification the user taps to review, edit, or delete; "Create notification"
 * appends a new one and opens it for editing straight away.
 *
 * The per-notification editor keeps the original chip-sentence method, so a
 * single notification can still be tuned into any before/after-overtime or
 * timed-plus-days combination.
 *
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

    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var openNewestAfterAdd by rememberSaveable { mutableStateOf(false) }

    // Creating a notification appends a rule asynchronously (via the store);
    // once it lands, open its editor so the user can configure it right away.
    LaunchedEffect(rules) {
        if (openNewestAfterAdd) {
            editingRuleId = rules.lastOrNull()?.id
            openNewestAfterAdd = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSubsectionLabel(stringResource(R.string.settings_reminder_schedule_title))
        Spacer(Modifier.height(Spacing.sm))
        rules.forEach { rule ->
            ReminderRuleRow(rule = rule, onClick = { editingRuleId = rule.id })
            Spacer(Modifier.height(Spacing.xs))
        }
        if (rules.size < ReminderRulesCodec.MAX_RULES) {
            TextButton(
                onClick = {
                    openNewestAfterAdd = true
                    onAddRule()
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.settings_notification_create),
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

    val editingRule = rules.firstOrNull { it.id == editingRuleId }
    if (editingRule != null) {
        ReminderRuleEditorSheet(
            rule = editingRule,
            onUpdate = onUpdateRule,
            onRemove = {
                onRemoveRule(editingRule.id)
                editingRuleId = null
            },
            onDismiss = { editingRuleId = null },
        )
    }
}

@Composable
private fun ReminderRuleRow(rule: ReminderRule, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.Medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = AuroraIndigo,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            ruleSummary(rule),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.settings_notification_edit_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReminderRuleEditorSheet(
    rule: ReminderRule,
    onUpdate: (ReminderRule) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenH)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                stringResource(R.string.settings_notification_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.md))
            // The original chip-sentence editor, scoped to this one notification
            // so complex before/after/timed combinations remain expressible.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.settings_remind_me),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                RuleValueChip(rule = rule, onUpdate = onUpdate)
                RuleKindChip(rule = rule, onUpdate = onUpdate)
                if (rule.kind == ReminderTriggerKind.AT_TIME) {
                    RuleDaysChip(rule = rule, onUpdate = onUpdate)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.Medium),
            ) {
                Text(stringResource(R.string.settings_notification_done))
            }
            Spacer(Modifier.height(Spacing.xs))
            TextButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.settings_notification_delete))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDaysChip(rule: ReminderRule, onUpdate: (ReminderRule) -> Unit) {
    var showDayPicker by remember { mutableStateOf(false) }

    SentenceChip(text = daysLabel(rule.daysOfWeek), onClick = { showDayPicker = true })

    if (showDayPicker) {
        var selection by remember { mutableStateOf(rule.daysOfWeek) }
        AlertDialog(
            onDismissRequest = { showDayPicker = false },
            title = { Text(stringResource(R.string.settings_rule_pick_days)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEEK_ORDER.forEach { day ->
                        val value = day.value
                        FilterChip(
                            selected = selection.isEmpty() || value in selection,
                            onClick = {
                                // An empty set means "every day"; materialise it
                                // to a full set before toggling a single day off.
                                val base = selection.ifEmpty { WEEK_ORDER.map { it.value }.toSet() }
                                selection = if (value in base) base - value else base + value
                            },
                            label = { Text(dayShortLabel(day)) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDayPicker = false
                        // Empty or all-seven both mean "every day"; store the
                        // canonical empty set so the label reads "every day".
                        val normalized = if (selection.size >= WEEK_ORDER.size) emptySet() else selection
                        onUpdate(rule.copy(daysOfWeek = normalized))
                    },
                ) { Text(stringResource(R.string.settings_rule_days_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDayPicker = false }) {
                    Text(stringResource(R.string.settings_rule_time_cancel))
                }
            },
        )
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

/** One-line summary shown on a collapsed notification row in the list. */
@Composable
private fun ruleSummary(rule: ReminderRule): String = when (rule.kind) {
    ReminderTriggerKind.BEFORE_OVERTIME -> stringResource(
        R.string.settings_rule_summary_generic,
        beforeValueLabel(rule.offsetMinutes),
        kindLabel(rule.kind),
    )

    ReminderTriggerKind.AFTER_OVERTIME -> stringResource(
        R.string.settings_rule_summary_generic,
        afterValueLabel(rule.offsetMinutes),
        kindLabel(rule.kind),
    )

    ReminderTriggerKind.AT_TIME -> stringResource(
        R.string.settings_rule_summary_at_time,
        formatMinuteOfDay(rule.timeMinuteOfDay),
        daysLabel(rule.daysOfWeek),
    )
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

@Composable
private fun daysLabel(daysOfWeek: Set<Int>): String {
    if (daysOfWeek.isEmpty() || daysOfWeek.size >= WEEK_ORDER.size) {
        return stringResource(R.string.settings_rule_every_day)
    }
    // Resolve labels in the composable body: stringResource (via dayShortLabel)
    // can't be called inside joinToString's non-inline transform lambda.
    val parts = mutableListOf<String>()
    for (day in WEEK_ORDER) {
        if (day.value in daysOfWeek) parts += dayShortLabel(day)
    }
    return parts.joinToString(separator = ", ")
}

@Composable
private fun dayShortLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.SUNDAY -> R.string.settings_day_short_sun
        DayOfWeek.MONDAY -> R.string.settings_day_short_mon
        DayOfWeek.TUESDAY -> R.string.settings_day_short_tue
        DayOfWeek.WEDNESDAY -> R.string.settings_day_short_wed
        DayOfWeek.THURSDAY -> R.string.settings_day_short_thu
        DayOfWeek.FRIDAY -> R.string.settings_day_short_fri
        DayOfWeek.SATURDAY -> R.string.settings_day_short_sat
    },
)

private val BEFORE_OPTIONS_MINUTES = listOf(10, 15, 30, 45, 60, 90)
private val AFTER_OPTIONS_MINUTES = listOf(0, 30, 60, 120)

/** Week starts on Sunday, matching the Israeli work week this app targets. */
private val WEEK_ORDER = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)
