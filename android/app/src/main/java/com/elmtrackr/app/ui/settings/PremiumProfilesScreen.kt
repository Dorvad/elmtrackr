@file:OptIn(ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.premium.PremiumTypeLabels
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.theme.Spacing

@Composable
fun PremiumProfilesScreen(
    onBack: () -> Unit,
    viewModel: PremiumProfilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium profiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            PremiumProfilesUiState.Loading -> BoxCentered(Modifier.padding(padding)) {
                CircularProgressIndicator()
            }
            is PremiumProfilesUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::ensureLoaded,
                modifier = Modifier.padding(padding),
            )
            is PremiumProfilesUiState.Ready -> PremiumProfilesContent(
                state = state,
                modifier = Modifier.padding(padding),
                onCreate = viewModel::openCreate,
                onEdit = viewModel::openEdit,
                onDelete = viewModel::deleteProfile,
                onDismissEditor = viewModel::dismissEditor,
                onSave = viewModel::saveEditor,
                onNameChange = viewModel::updateName,
                onMultiplierChange = viewModel::updateMultiplier,
                onPremiumTypeChange = viewModel::updatePremiumType,
                onDismissMessage = viewModel::clearSaveMessage,
            )
        }
    }
}

@Composable
private fun PremiumProfilesContent(
    state: PremiumProfilesUiState.Ready,
    modifier: Modifier = Modifier,
    onCreate: () -> Unit,
    onEdit: (PremiumProfile) -> Unit,
    onDelete: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onMultiplierChange: (String) -> Unit,
    onPremiumTypeChange: (PremiumType) -> Unit,
    onDismissMessage: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(
                "Create reusable premium rates for holidays, Shabbat, and special shifts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.saveMessage?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.primary)
                LaunchedEffect(message) { onDismissMessage() }
            }
        }
        item {
            ElmGradientButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Add premium profile")
            }
        }
        item { ElmSectionHeader("Your profiles") }
        items(state.profiles, key = { it.id }) { profile ->
            PremiumProfileCard(
                profile = profile,
                onEdit = { onEdit(profile) },
                onDelete = { onDelete(profile.id) },
            )
        }
        state.editor?.let { editor ->
            item { ElmSectionHeader(if (editor.profileId == null) "New profile" else "Edit profile") }
            item {
                PremiumProfileEditor(
                    editor = editor,
                    onDismiss = onDismissEditor,
                    onSave = onSave,
                    onNameChange = onNameChange,
                    onMultiplierChange = onMultiplierChange,
                    onPremiumTypeChange = onPremiumTypeChange,
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun PremiumProfileCard(
    profile: PremiumProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${(profile.multiplier * 100).toInt()}% · ${PremiumTypeLabels.title(profile.premiumType)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (profile.isDefault) {
                        Text(
                            "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (!profile.isDefault) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete profile")
                    }
                }
            }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumProfileEditor(
    editor: PremiumProfileEditorState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onMultiplierChange: (String) -> Unit,
    onPremiumTypeChange: (PremiumType) -> Unit,
) {
    var typeExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = editor.name,
            onValueChange = onNameChange,
            label = { Text("Profile name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = editor.multiplierText,
            onValueChange = onMultiplierChange,
            label = { Text("Multiplier") },
            supportingText = { Text("1.5 = 150% pay") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = PremiumTypeLabels.title(editor.premiumType),
                onValueChange = {},
                readOnly = true,
                label = { Text("Premium type") },
                supportingText = { Text(PremiumTypeLabels.description(editor.premiumType)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                PremiumType.selectable.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(PremiumTypeLabels.title(type), fontWeight = FontWeight.Medium)
                                Text(
                                    PremiumTypeLabels.description(type),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        onClick = {
                            onPremiumTypeChange(type)
                            typeExpanded = false
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            ElmGradientButton(
                onClick = onSave,
                enabled = !editor.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (editor.isSaving) "Saving…" else "Save profile")
            }
        }
    }
}

@Composable
private fun BoxCentered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
