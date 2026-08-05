@file:OptIn(ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.premium.PremiumTypeLabels
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.ui.common.resolve
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.AuroraScreenHeader
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmDropdownField
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmIconTile
import com.elmtrackr.app.ui.design.ElmIconTileSize
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.ui.projects.StatusPill
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraStatusPillInk
import com.elmtrackr.app.ui.theme.auroraWeekendBackground

@Composable
fun PremiumProfilesScreen(
    onBack: () -> Unit,
    viewModel: PremiumProfilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Save feedback via snackbar + success haptic, like Settings and Shifts.
    val saveMessage = (uiState as? PremiumProfilesUiState.Ready)?.saveMessage
    LaunchedEffect(saveMessage) {
        val message = saveMessage ?: return@LaunchedEffect
        if (message == UiText.Res(R.string.settings_premium_feedback_saved)) {
            AuroraHaptics.success(haptic)
        }
        snackbarHostState.showSnackbar(message.resolve(context))
        viewModel.clearSaveMessage()
    }

    // Same chrome as every other settings sub-screen (AuroraScreenHeader
    // inside AuroraListScreen), instead of a one-off Scaffold + TopAppBar.
    AuroraListScreen {
        Box(Modifier.fillMaxSize()) {
            when (val state = uiState) {
                PremiumProfilesUiState.Loading -> BoxCentered(Modifier) {
                    CircularProgressIndicator()
                }
                is PremiumProfilesUiState.Error -> ErrorState(
                    message = state.message.asString(),
                    onRetry = viewModel::ensureLoaded,
                )
                is PremiumProfilesUiState.Ready -> PremiumProfilesContent(
                    state = state,
                    onBack = onBack,
                    onCreate = viewModel::openCreate,
                    onEdit = viewModel::openEdit,
                    onDelete = viewModel::deleteProfile,
                    onDismissEditor = viewModel::dismissEditor,
                    onSave = viewModel::saveEditor,
                    onNameChange = viewModel::updateName,
                    onMultiplierChange = viewModel::updateMultiplier,
                    onPremiumTypeChange = viewModel::updatePremiumType,
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
internal fun PremiumProfilesContent(
    state: PremiumProfilesUiState.Ready,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onCreate: () -> Unit,
    onEdit: (PremiumProfile) -> Unit,
    onDelete: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onMultiplierChange: (String) -> Unit,
    onPremiumTypeChange: (PremiumType) -> Unit,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    pendingDeleteId?.let { deleteId ->
        val name = state.profiles.firstOrNull { it.id == deleteId }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.settings_delete_premium_title)) },
            text = { Text(stringResource(R.string.settings_delete_premium_text, name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    onDelete(deleteId)
                }) { Text(stringResource(R.string.settings_delete_profile), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.dashboard_cancel))
                }
            },
        )
    }

    // The editor opens as a bottom sheet on the tapped row, instead of a form
    // appended after the list where it landed below the fold.
    state.editor?.let { editor ->
        PremiumProfileEditorSheet(
            editor = editor,
            onDismiss = onDismissEditor,
            onSave = onSave,
            onNameChange = onNameChange,
            onMultiplierChange = onMultiplierChange,
            onPremiumTypeChange = onPremiumTypeChange,
            onRemove = editor.profileId
                ?.takeIf { !editor.isDefault }
                ?.let { id -> { pendingDeleteId = id } },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            AuroraScreenHeader(
                title = stringResource(R.string.settings_premium_profiles),
                subtitle = stringResource(R.string.settings_premium_profiles_tagline),
                onBack = onBack,
                backContentDescription = stringResource(R.string.settings_back),
                action = {
                    // Content first: the list leads, "New" stays reachable in the header.
                    ElmGradientButton(onClick = onCreate, compact = true) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(Spacing.s16))
                        Spacer(Modifier.size(Spacing.s4))
                        Text(stringResource(R.string.settings_new), fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }
        item { ElmSectionHeader(stringResource(R.string.settings_your_profiles)) }
        items(state.profiles, key = { it.id }) { profile ->
            PremiumProfileCard(profile = profile, onClick = { onEdit(profile) })
        }
        item { Spacer(Modifier.height(Spacing.s24)) }
    }
}

@Composable
private fun PremiumProfileCard(
    profile: PremiumProfile,
    onClick: () -> Unit,
) {
    ElmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .auroraRowClickable(onClick = onClick)
                .padding(horizontal = Spacing.s16, vertical = Spacing.s14)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The multiplier is what you scan for, so it is the hero of the row.
            ElmIconTile(
                background = auroraWeekendBackground(),
                size = ElmIconTileSize.Large,
            ) {
                Text(
                    formatMultiplierTile(profile.multiplier),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = auroraStatusPillInk(AuroraPlum),
                    maxLines = 1,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.s12),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s6)) {
                    Text(
                        profile.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (profile.isDefault) {
                        StatusPill(
                            text = stringResource(R.string.settings_default).uppercase(),
                            accent = AuroraIndigo,
                        )
                    }
                }
                Text(
                    PremiumTypeLabels.title(profile.premiumType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(Spacing.s18).mirrorInRtl(),
            )
        }
    }
}

@Composable
private fun PremiumProfileEditorSheet(
    editor: PremiumProfileEditorState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onMultiplierChange: (String) -> Unit,
    onPremiumTypeChange: (PremiumType) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenH)
                .padding(bottom = Spacing.s32),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            Text(
                stringResource(
                    if (editor.profileId == null) R.string.settings_new_profile else R.string.settings_edit_profile,
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.settings_profile_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = editor.multiplierText,
                onValueChange = onMultiplierChange,
                label = { Text(stringResource(R.string.settings_multiplier)) },
                supportingText = {
                    // A live reading of what the typed multiplier pays, falling back
                    // to the worked example while the field doesn't parse.
                    val percent = editor.multiplierText.toDoubleOrNull()
                        ?.takeIf { it > 0.0 }
                        ?.let { (it * 100).toInt() }
                    Text(
                        if (percent != null) {
                            stringResource(R.string.settings_multiplier_equals, percent)
                        } else {
                            stringResource(R.string.settings_multiplier_hint)
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            // The one picker whose rows need two lines: a premium type is not
            // self-explanatory from its name, so the description sits under it in the
            // list and under the field once chosen.
            ElmDropdownField(
                label = stringResource(R.string.settings_premium_type),
                selected = editor.premiumType,
                options = PremiumType.selectable,
                onSelect = onPremiumTypeChange,
                displayName = { PremiumTypeLabels.title(it) },
                supportingText = PremiumTypeLabels.description(editor.premiumType),
                itemContent = { type ->
                    Column {
                        Text(PremiumTypeLabels.title(type), fontWeight = FontWeight.Medium)
                        Text(
                            PremiumTypeLabels.description(type),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
            ElmGradientButton(
                onClick = onSave,
                enabled = !editor.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (editor.isSaving) R.string.settings_saving else R.string.settings_save_profile))
            }
            // Delete demoted: the reversible path comes first, removal lives here.
            onRemove?.let {
                TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.settings_remove_this_rate),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun formatMultiplierTile(multiplier: Double): String {
    val text = if (multiplier % 1.0 == 0.0) {
        multiplier.toInt().toString()
    } else {
        multiplier.toString().trimEnd('0').trimEnd('.')
    }
    return "×$text"
}

@Composable
private fun BoxCentered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
