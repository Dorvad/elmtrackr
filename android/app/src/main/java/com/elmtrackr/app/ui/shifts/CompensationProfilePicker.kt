@file:OptIn(ExperimentalLayoutApi::class)

package com.elmtrackr.app.ui.shifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.CompensationProfile

@Composable
fun CompensationProfilePicker(
    profiles: List<CompensationProfile>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCreateProfile: ((name: String, onCreated: (String?) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (profiles.isEmpty() && onCreateProfile == null) return

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val selected = profiles.firstOrNull { it.id == selectedId || it.remoteId == selectedId }
        ?: profiles.firstOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.shifts_compensation_profile_label),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            profiles.forEach { profile ->
                FilterChip(
                    selected = selected?.id == profile.id,
                    onClick = { onSelect(profile.id) },
                    label = { Text(profile.chipLabel) },
                )
            }
            if (onCreateProfile != null) {
                FilterChip(
                    selected = false,
                    onClick = {
                        newProfileName = ""
                        showCreateDialog = true
                    },
                    label = { Text(stringResource(R.string.shifts_add_compensation_profile)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.shifts_add_compensation_profile),
                        )
                    },
                )
            }
        }
        selected?.let { profile ->
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(profile.regionCode.name)
                    profile.baseHourlyRate?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showCreateDialog && onCreateProfile != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreating) showCreateDialog = false
            },
            title = { Text(stringResource(R.string.shifts_new_compensation_profile_title)) },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text(stringResource(R.string.shifts_compensation_profile_name_label)) },
                    placeholder = { Text(stringResource(R.string.shifts_compensation_profile_name_placeholder)) },
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newProfileName.trim()
                        if (name.isBlank()) return@TextButton
                        isCreating = true
                        onCreateProfile(name) { createdId ->
                            isCreating = false
                            if (createdId != null) {
                                onSelect(createdId)
                                showCreateDialog = false
                            }
                        }
                    },
                    enabled = !isCreating && newProfileName.trim().isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.shifts_create_profile))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    enabled = !isCreating,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.shifts_cancel))
                }
            },
        )
    }
}

private val CompensationProfile.chipLabel: String
    get() = buildString {
        append(name)
        baseHourlyRate?.let { append(" · ").append(it) }
    }
