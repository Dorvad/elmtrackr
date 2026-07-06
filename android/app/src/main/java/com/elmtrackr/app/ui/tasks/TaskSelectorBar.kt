package com.elmtrackr.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.theme.CornerRadius

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskSelectorBar(
    tasks: List<Task>,
    selectedTaskId: String?,
    suggestedTaskId: String?,
    showSuggestedNow: Boolean,
    suggestionExplanation: String?,
    onSelectTask: (String) -> Unit,
    onManageTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return

    val displayTasks = sortedTasksForDisplay(tasks, suggestedTaskId)
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.tasks_task_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.tasks_manage),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .auroraRowClickable(onClick = onManageTasks)
                    .semantics { role = Role.Button },
            )
        }
        if (showSuggestedNow && suggestedTaskId != null) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(CornerRadius.Small),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.tasks_suggested_now),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    suggestionExplanation?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            displayTasks.forEach { task ->
                val isSuggested = showSuggestedNow && task.id == suggestedTaskId
                FilterChip(
                    selected = task.id == selectedTaskId,
                    onClick = {
                        AuroraHaptics.toggle(haptic)
                        onSelectTask(task.id)
                    },
                    label = {
                        val chipContentDescription = if (isSuggested) {
                            stringResource(R.string.tasks_chip_a11y_suggested, task.name, formatRate(task.hourlyRate))
                        } else {
                            stringResource(R.string.tasks_chip_a11y, task.name, formatRate(task.hourlyRate))
                        }
                        Text(
                            if (isSuggested) {
                                stringResource(R.string.tasks_chip_suggested, task.icon, task.name)
                            } else {
                                "${task.icon} ${task.name}"
                            },
                            modifier = Modifier.semantics {
                                contentDescription = chipContentDescription
                            },
                        )
                    },
                    leadingIcon = {
                        TaskChipColorLeading(parseTaskColor(task.color))
                    },
                )
            }
        }
        selectedTaskId?.let { id ->
            tasks.firstOrNull { it.id == id }?.let { selected ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Small),
                    color = parseTaskColor(selected.color)?.copy(alpha = 0.18f)
                        ?: MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.tasks_selected_summary, selected.icon, selected.name, formatRate(selected.hourlyRate)),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveShiftTaskBadge(
    icon: String?,
    name: String?,
    rate: Double?,
    colorHex: String? = null,
    modifier: Modifier = Modifier,
) {
    if (name.isNullOrBlank()) return
    val tint = parseTaskColor(colorHex)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Small),
        color = tint?.copy(alpha = 0.22f) ?: MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            if (rate != null) {
                stringResource(R.string.tasks_badge_with_rate, icon.orEmpty(), name, formatRate(rate))
            } else {
                "${icon.orEmpty()} $name"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint ?: MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun formatRate(rate: Double): String =
    if (rate % 1.0 == 0.0) rate.toInt().toString() else "%.2f".format(rate)
