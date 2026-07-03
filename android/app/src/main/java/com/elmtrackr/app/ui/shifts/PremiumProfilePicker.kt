package com.elmtrackr.app.ui.shifts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.elmtrackr.app.domain.model.PremiumProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumProfilePicker(
    profiles: List<PremiumProfile>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profiles.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedId || it.remoteId == selectedId }
    val displayValue = selected?.displayLabel ?: "No premium"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text("Premium") },
            supportingText = {
                Text(
                    selected?.let { "Applies ${(it.multiplier * 100).toInt()}% rate to this shift" }
                        ?: "Standard pay unless weekend rules apply",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("No premium") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.displayLabel) },
                    onClick = {
                        onSelect(profile.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
