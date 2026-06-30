@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.tasks.TaskDefaultRulesBuilder
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.ElmCardPadded
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.theme.CornerRadius

import androidx.compose.ui.unit.dp

@Composable
fun TaskManagementScreen(
    onBack: () -> Unit,
    viewModel: TaskManagementViewModel = viewModel(factory = TaskManagementViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    AuroraListScreen {
        when (val state = uiState) {
            is TaskManagementUiState.Loading -> Box(
                Modifier.fillMaxSize().widthIn(max = 448.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is TaskManagementUiState.Error -> ErrorState(state.message, onRetry = viewModel::load)
            is TaskManagementUiState.Ready -> TaskManagementContent(
                state = state,
                onBack = onBack,
                onSave = viewModel::saveTask,
                onArchive = viewModel::archiveTask,
                onDismissMessage = viewModel::clearMessage,
            )
        }
    }
}

@Composable
private fun TaskManagementContent(
    state: TaskManagementUiState.Ready,
    onBack: () -> Unit,
    onSave: (String?, String, String, String?, Double) -> Unit,
    onArchive: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }

    if (state.message != null) {
        androidx.compose.runtime.LaunchedEffect(state.message) {
            kotlinx.coroutines.delay(2500)
            onDismissMessage()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 448.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                "Optional labels with their own hourly rates for punch-in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            state.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            }
            state.errorMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        if (state.defaultRules.isNotEmpty()) {
            item {
                DefaultRulesCard(rules = state.defaultRules)
            }
        }

        if (showForm || editingId != null) {
            item {
                TaskEditorCard(
                    task = state.tasks.firstOrNull { it.id == editingId },
                    existingNames = state.tasks
                        .filter { !it.isArchived && it.id != editingId }
                        .map { it.name },
                    onCancel = { showForm = false; editingId = null },
                    onSave = { name, icon, color, rate ->
                        onSave(editingId, name, icon, color, rate)
                        showForm = false
                        editingId = null
                    },
                )
            }
        } else {
            item {
                OutlinedButton(
                    onClick = { showForm = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add task", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (state.tasks.isEmpty() && !showForm) {
            item {
                ElmCardPadded(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "No tasks yet. Add one to tag shifts with different hourly rates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(state.tasks.filter { !it.isArchived }, key = { it.id }) { task ->
            TaskRow(
                task = task,
                onEdit = { editingId = task.id; showForm = false },
                onArchive = { onArchive(task.id) },
            )
        }

        if (state.tasks.any { it.isArchived }) {
            item {
                Text(
                    "Archived",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.tasks.filter { it.isArchived }, key = { "arch-${it.id}" }) { task ->
                TaskRow(task = task, archived = true, onEdit = {}, onArchive = {})
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    archived: Boolean = false,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    ElmCardPadded(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(task.icon, style = MaterialTheme.typography.headlineSmall)
            TaskColorDot(
                color = task.resolveColor(),
                selected = false,
                modifier = Modifier.padding(start = 8.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(task.name, fontWeight = FontWeight.Bold)
                Text(
                    "${formatTaskRate(task.hourlyRate)}/hr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!archived) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onArchive) { Text("Archive") }
            }
        }
    }
}

@Composable
private fun DefaultRulesCard(rules: List<com.elmtrackr.app.domain.tasks.TaskDefaultRule>) {
    ElmCardPadded(Modifier.padding(horizontal = 16.dp)) {
        Text("Schedule defaults", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Learned from your shift history — used for Suggested now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        rules.take(6).forEach { rule ->
            Text(
                TaskDefaultRulesBuilder.formatRule(rule),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        if (rules.size > 6) {
            Text(
                "+${rules.size - 6} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskEditorCard(
    task: Task?,
    existingNames: List<String>,
    onCancel: () -> Unit,
    onSave: (name: String, icon: String, color: String?, rate: Double) -> Unit,
) {
    var name by remember(task?.id) { mutableStateOf(task?.name.orEmpty()) }
    var icon by remember(task?.id) { mutableStateOf(task?.icon ?: TASK_EMOJI_OPTIONS.first()) }
    var color by remember(task?.id) { mutableStateOf(task?.color ?: TASK_COLOR_OPTIONS.first()) }
    var rateText by remember(task?.id) { mutableStateOf(task?.hourlyRate?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    ElmCardPadded(Modifier.padding(horizontal = 16.dp)) {
        Text(
            if (task == null) "New task" else "Edit task",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Task name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("Icon", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TASK_EMOJI_OPTIONS.forEach { emoji ->
                FilterChip(
                    selected = icon == emoji,
                    onClick = { icon = emoji },
                    label = { Text(emoji) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Color", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TASK_COLOR_OPTIONS.forEach { hex ->
                val swatch = parseTaskColor(hex) ?: Color.Gray
                TaskColorDot(
                    color = swatch,
                    selected = color == hex,
                    modifier = Modifier.clickable { color = hex },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = rateText,
            onValueChange = { rateText = it.filter { ch -> ch.isDigit() || ch == '.' }; error = null },
            label = { Text("Hourly rate") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            ElmGradientButton(
                onClick = {
                    val trimmed = name.trim()
                    val rate = rateText.toDoubleOrNull()
                    when {
                        trimmed.isEmpty() -> error = "Enter a task name"
                        existingNames.any { it.equals(trimmed, ignoreCase = true) } ->
                            error = "A task with this name already exists"
                        rate == null || rate <= 0 -> error = "Enter a valid hourly rate"
                        else -> onSave(trimmed, icon, color, rate)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun formatTaskRate(rate: Double): String =
    if (rate % 1.0 == 0.0) rate.toInt().toString() else "%.2f".format(rate)
