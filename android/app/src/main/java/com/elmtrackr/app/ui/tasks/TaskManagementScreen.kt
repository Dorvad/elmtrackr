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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.elmtrackr.app.ui.common.resolve
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.tasks.TaskDefaultRulesBuilder
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.ElmCardPadded
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.theme.CornerRadius

import androidx.compose.ui.unit.dp

@Composable
fun TaskManagementScreen(
    onBack: () -> Unit,
    viewModel: TaskManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Same feedback pattern as Settings and Shifts: snackbar + success haptic
    // instead of an inline auto-dismissing text line.
    val ready = uiState as? TaskManagementUiState.Ready
    val successMessage = ready?.message
    val errorMessage = ready?.errorMessage
    LaunchedEffect(successMessage, errorMessage) {
        val message = errorMessage ?: successMessage ?: return@LaunchedEffect
        if (errorMessage == null) AuroraHaptics.success(haptic)
        snackbarHostState.showSnackbar(
            message = message.resolve(context),
            duration = if (errorMessage != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        viewModel.clearMessage()
    }

    AuroraListScreen {
      Box(Modifier.fillMaxSize()) {
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
                onDelete = viewModel::deleteTask,
                onDismissMessage = viewModel::clearMessage,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskManagementContent(
    state: TaskManagementUiState.Ready,
    onBack: () -> Unit,
    onSave: (String?, String, String, String?, Double) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit = {},
    onDismissMessage: () -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Task?>(null) }
    val haptic = LocalHapticFeedback.current

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.tasks_delete_title, candidate.name)) },
            text = {
                Text(
                    stringResource(R.string.tasks_delete_message),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AuroraHaptics.destructive(haptic)
                        onDelete(candidate.id)
                        deleteCandidate = null
                    },
                ) { Text(stringResource(R.string.tasks_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(R.string.tasks_cancel)) }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 448.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tasks_back))
                }
                Text(stringResource(R.string.tasks_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.tasks_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
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
                    Text(stringResource(R.string.tasks_add_task), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (state.tasks.isEmpty() && !showForm) {
            item {
                ElmCardPadded(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.tasks_empty_hint),
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
                onArchive = {
                    AuroraHaptics.destructive(haptic)
                    onArchive(task.id)
                },
                modifier = Modifier.animateItem(),
            )
        }

        if (state.tasks.any { it.isArchived }) {
            item {
                Text(
                    stringResource(R.string.tasks_archived_header),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.tasks.filter { it.isArchived }, key = { "arch-${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    archived = true,
                    onEdit = {},
                    onArchive = {},
                    onDelete = { deleteCandidate = task },
                )
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
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ElmCardPadded(modifier.padding(horizontal = 16.dp)) {
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
                    stringResource(R.string.tasks_rate_per_hour, formatTaskRate(task.hourlyRate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!archived) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.tasks_edit)) }
                TextButton(onClick = onArchive) { Text(stringResource(R.string.tasks_archive)) }
            } else if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.tasks_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DefaultRulesCard(rules: List<com.elmtrackr.app.domain.tasks.TaskDefaultRule>) {
    ElmCardPadded(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.tasks_schedule_defaults), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.tasks_schedule_defaults_hint),
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
                stringResource(R.string.tasks_more_rules, rules.size - 6),
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
    val errorNameRequired = stringResource(R.string.tasks_error_name_required)
    val errorNameExists = stringResource(R.string.tasks_error_name_exists)
    val errorRateInvalid = stringResource(R.string.tasks_error_rate_invalid)

    ElmCardPadded(Modifier.padding(horizontal = 16.dp)) {
        Text(
            if (task == null) stringResource(R.string.tasks_new_task) else stringResource(R.string.tasks_edit_task),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text(stringResource(R.string.tasks_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.tasks_icon_label), style = MaterialTheme.typography.labelMedium)
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
        Text(stringResource(R.string.tasks_color_label), style = MaterialTheme.typography.labelMedium)
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
            label = { Text(stringResource(R.string.tasks_hourly_rate_label)) },
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
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tasks_cancel)) }
            ElmGradientButton(
                onClick = {
                    val trimmed = name.trim()
                    val rate = rateText.toDoubleOrNull()
                    when {
                        trimmed.isEmpty() -> error = errorNameRequired
                        existingNames.any { it.equals(trimmed, ignoreCase = true) } ->
                            error = errorNameExists
                        rate == null || rate <= 0 -> error = errorRateInvalid
                        else -> onSave(trimmed, icon, color, rate)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.tasks_save), fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun formatTaskRate(rate: Double): String =
    if (rate % 1.0 == 0.0) rate.toInt().toString() else "%.2f".format(rate)
