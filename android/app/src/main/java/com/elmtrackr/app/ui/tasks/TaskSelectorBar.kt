package com.elmtrackr.app.ui.tasks

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.ui.theme.CornerRadius

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskSelectorBar(
    tasks: List<Task>,
    selectedTaskId: String?,
    habitSuggested: Boolean,
    onSelectTask: (String) -> Unit,
    onManageTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return

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
                modifier = Modifier.clickable(onClick = onManageTasks),
            )
        }
        if (habitSuggested) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Suggested based on your recent shifts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tasks.forEach { task ->
                FilterChip(
                    selected = task.id == selectedTaskId,
                    onClick = { onSelectTask(task.id) },
                    label = {
                        Text("${task.icon} ${task.name}")
                    },
                    leadingIcon = null,
                )
            }
        }
        selectedTaskId?.let { id ->
            tasks.firstOrNull { it.id == id }?.let { selected ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Small),
                    color = MaterialTheme.colorScheme.surfaceVariant,
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
    modifier: Modifier = Modifier,
) {
    if (name.isNullOrBlank()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Small),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            "${icon.orEmpty()} $name${rate?.let { " · ${formatRate(it)}/hr" }.orEmpty()}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun formatRate(rate: Double): String =
    if (rate % 1.0 == 0.0) rate.toInt().toString() else "%.2f".format(rate)
