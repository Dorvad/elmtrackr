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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            Text("Task", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                "Manage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.auroraRowClickable(onClick = onManageTasks),
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
                        "Suggested now",
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
                        Text(buildString {
                            if (isSuggested) append("✨ ")
                            append("${task.icon} ${task.name}")
                        })
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
                        "${selected.icon} ${selected.name} · ${formatRate(selected.hourlyRate)}/hr",
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
            "${icon.orEmpty()} $name${rate?.let { " · ${formatRate(it)}/hr" }.orEmpty()}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint ?: MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun formatRate(rate: Double): String =
    if (rate % 1.0 == 0.0) rate.toInt().toString() else "%.2f".format(rate)
