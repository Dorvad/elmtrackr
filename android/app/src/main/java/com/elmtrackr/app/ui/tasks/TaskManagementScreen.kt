@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.tasks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.tasks.TaskDefaultRule
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.common.resolve
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.AuroraScreenHeader
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmCardPadded
import com.elmtrackr.app.ui.design.ElmDashedButton
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmIconTile
import com.elmtrackr.app.ui.design.ElmIconTileSize
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.ui.projects.StatusPill
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSemantics
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
                onRestore = viewModel::unarchiveTask,
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

@Composable
internal fun TaskManagementContent(
    state: TaskManagementUiState.Ready,
    onBack: () -> Unit,
    onSave: (String?, String, String, String?, Double) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onDismissMessage: () -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
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

    if (showForm || editingId != null) {
        TaskEditorSheet(
            task = state.tasks.firstOrNull { it.id == editingId },
            existingNames = state.tasks
                .filter { !it.isArchived && it.id != editingId }
                .map { it.name },
            onDismiss = { showForm = false; editingId = null },
            onSave = { name, icon, color, rate ->
                onSave(editingId, name, icon, color, rate)
                showForm = false
                editingId = null
            },
            onArchive = editingId?.let { id ->
                {
                    AuroraHaptics.destructive(haptic)
                    onArchive(id)
                    editingId = null
                }
            },
        )
    }

    val activeTasks = state.tasks.filter { !it.isArchived }
    val archivedTasks = state.tasks.filter { it.isArchived }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 448.dp)
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Layout.cardGap),
    ) {
        item {
            AuroraScreenHeader(
                title = stringResource(R.string.tasks_title),
                subtitle = stringResource(R.string.tasks_subtitle),
                onBack = onBack,
                backContentDescription = stringResource(R.string.tasks_back),
                action = {
                    ElmGradientButton(onClick = { showForm = true }, compact = true) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(Spacing.s16))
                        Spacer(Modifier.size(Spacing.s4))
                        Text(stringResource(R.string.tasks_add), fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        if (state.defaultRules.isNotEmpty()) {
            item {
                DefaultRulesCard(rules = state.defaultRules)
            }
        }

        if (activeTasks.isEmpty()) {
            item {
                ElmCardPadded {
                    Text(
                        stringResource(R.string.tasks_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                ElmSectionHeader(
                    stringResource(R.string.tasks_your_tasks),
                    modifier = Modifier.padding(top = Spacing.s4),
                )
            }
            item {
                ElmCard {
                    activeTasks.forEachIndexed { index, task ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TaskRow(task = task, onClick = { editingId = task.id })
                    }
                }
            }
        }

        item {
            ElmDashedButton(
                label = stringResource(R.string.tasks_new_task),
                onClick = { showForm = true },
            )
        }

        if (archivedTasks.isNotEmpty()) {
            item {
                ArchivedTasksCard(
                    tasks = archivedTasks,
                    expanded = archivedExpanded,
                    onExpandedChange = { archivedExpanded = it },
                    onRestore = onRestore,
                    onDelete = { deleteCandidate = it },
                )
            }
        }

        item { Spacer(Modifier.height(Spacing.s24)) }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One action per row: tapping opens the editor, where archive also lives.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .auroraRowClickable(onClick = onClick)
            .padding(horizontal = Spacing.s16, vertical = Spacing.s12)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The task's colour tints its emoji tile — one identity token per row.
        ElmIconTile(background = task.resolveColor().copy(alpha = 0.14f)) {
            Text(task.icon, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            task.name,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.s12),
        )
        Text(
            stringResource(R.string.tasks_rate_per_hour, formatTaskRate(task.hourlyRate)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(start = Spacing.s6)
                .size(Spacing.s18)
                .mirrorInRtl(),
        )
    }
}

@Composable
private fun ArchivedTasksCard(
    tasks: List<Task>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (Task) -> Unit,
) {
    // Collapsed by default so Restore/Delete aren't ambient on the page.
    ElmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .auroraRowClickable(onClick = { onExpandedChange(!expanded) })
                .padding(horizontal = Spacing.s16, vertical = Spacing.s14),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            Text(
                stringResource(R.string.tasks_archived_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(text = tasks.size.toString(), accent = AuroraIndigo)
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (expanded) {
            tasks.forEach { task ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s16, vertical = Spacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ElmIconTile(background = task.resolveColor().copy(alpha = 0.14f)) {
                        Text(task.icon, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        task.name,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.s12),
                    )
                    // Restore comes first: archiving is a single unconfirmed tap, so it
                    // needs a way back that is at least as reachable as deletion.
                    TextButton(onClick = { onRestore(task.id) }) {
                        Text(stringResource(R.string.tasks_restore))
                    }
                    TextButton(onClick = { onDelete(task) }) {
                        Text(stringResource(R.string.tasks_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultRulesCard(rules: List<TaskDefaultRule>) {
    val locale = appLocale()
    ElmCardPadded {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ElmIconTile(
                background = auroraSemantics.infoContainer,
                size = ElmIconTileSize.Small,
            ) { Text("✨", style = MaterialTheme.typography.titleMedium) }
            Column(Modifier.padding(start = Spacing.s10)) {
                Text(
                    stringResource(R.string.tasks_schedule_defaults),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.tasks_schedule_defaults_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s12))
        // Day-chips instead of raw text lines: one chip per day/task pair.
        val chips = rules.distinctBy { it.dayOfWeek to it.taskId }
        val shown = chips.take(MAX_RULE_CHIPS)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
            verticalArrangement = Arrangement.spacedBy(Spacing.s6),
        ) {
            shown.forEach { rule ->
                Row(
                    modifier = Modifier
                        .background(auroraSurfaceSub(), RoundedCornerShape(CornerRadius.Chip))
                        .padding(horizontal = Spacing.s10, vertical = Spacing.s6),
                ) {
                    Text(
                        ruleDayName(rule, locale),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " · ${rule.taskName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (chips.size > shown.size) {
                Text(
                    stringResource(R.string.tasks_more_rules, chips.size - shown.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = auroraSemantics.infoInk,
                    modifier = Modifier
                        .background(auroraSemantics.infoContainer, RoundedCornerShape(CornerRadius.Chip))
                        .padding(horizontal = Spacing.s10, vertical = Spacing.s6),
                )
            }
        }
    }
}

@Composable
private fun TaskEditorSheet(
    task: Task?,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: String?, rate: Double) -> Unit,
    onArchive: (() -> Unit)?,
) {
    var name by remember(task?.id) { mutableStateOf(task?.name.orEmpty()) }
    var icon by remember(task?.id) { mutableStateOf(task?.icon ?: TASK_EMOJI_OPTIONS.first()) }
    var color by remember(task?.id) { mutableStateOf(task?.color ?: TASK_COLOR_OPTIONS.first()) }
    var rateText by remember(task?.id) { mutableStateOf(task?.hourlyRate?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val errorNameRequired = stringResource(R.string.tasks_error_name_required)
    val errorNameExists = stringResource(R.string.tasks_error_name_exists)
    val errorRateInvalid = stringResource(R.string.tasks_error_rate_invalid)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Editor opens in context on the tapped row instead of reflowing the list.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenH)
                .padding(bottom = Spacing.s32),
        ) {
            Text(
                if (task == null) stringResource(R.string.tasks_new_task) else stringResource(R.string.tasks_edit_task),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.s12))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text(stringResource(R.string.tasks_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.s12))
            Text(stringResource(R.string.tasks_icon_label), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(Spacing.s6))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                TASK_EMOJI_OPTIONS.forEach { emoji ->
                    FilterChip(
                        selected = icon == emoji,
                        onClick = { icon = emoji },
                        label = { Text(emoji) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s12))
            Text(stringResource(R.string.tasks_color_label), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(Spacing.s6))
            // No extra arrangement spacing: each swatch now reserves a 48dp touch
            // target around its 28dp circle, which supplies the gap on its own.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TASK_COLOR_OPTIONS.forEach { hex ->
                    val swatch = parseTaskColor(hex) ?: Color.Gray
                    TaskColorDot(
                        color = swatch,
                        selected = color == hex,
                        contentDescription = stringResource(R.string.tasks_color_label),
                        onClick = { color = hex },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s12))
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it.filter { ch -> ch.isDigit() || ch == '.' }; error = null },
                label = { Text(stringResource(R.string.tasks_hourly_rate_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(Spacing.s4))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(Spacing.s16))
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tasks_save), fontWeight = FontWeight.SemiBold) }
            // The reversible path: archiving lives in the editor, not on the row.
            onArchive?.let {
                TextButton(onClick = it, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s4)) {
                    Text(
                        stringResource(R.string.tasks_archive_task),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private const val MAX_RULE_CHIPS = 5

private fun ruleDayName(rule: TaskDefaultRule, locale: Locale): String =
    DayOfWeek.of(if (rule.dayOfWeek == 0) 7 else rule.dayOfWeek)
        .getDisplayName(TextStyle.SHORT, locale)

private fun formatTaskRate(rate: Double): String =
    if (rate % 1.0 == 0.0) rate.toInt().toString() else "%.2f".format(rate)
