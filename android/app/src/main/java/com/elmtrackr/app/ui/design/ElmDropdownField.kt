package com.elmtrackr.app.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * The read-only picker: a field showing the current choice, tapped to reveal the
 * rest.
 *
 * Four screens had hand-rolled this same recipe — read-only [OutlinedTextField],
 * `PrimaryNotEditable` anchor, [ExposedDropdownMenuBox], one [DropdownMenuItem]
 * per option — and all four were missing the same thing, so fixing it meant
 * finding all four. The searchable timezone picker is deliberately not folded in:
 * it is an editable combobox with its own query state, a different control that
 * happens to share a container.
 *
 * ## Accessibility
 *
 * The anchor's `Role.DropdownList` comes from Material's `menuAnchor` and does
 * **not** need adding here — verified against material3 1.3.1 by reading the
 * anchor's semantics in a render test. What Material does not provide at that
 * version is any expanded/collapsed state: the anchor exposes `OnClick` and
 * nothing that says whether the list is currently open. The chevron is the only
 * indication on screen and it carries no description, so a screen-reader user had
 * no way to tell an open list from a closed one. [auroraExpandable] attaches it,
 * reusing the same wording as every other collapsible surface in the app.
 *
 * @param displayName how an option reads in the field and in the list. Composable
 *   so callers can resolve string resources.
 * @param itemContent overrides the list row when one line is not enough — the
 *   premium-type picker shows a title and a description. The field itself always
 *   uses [displayName]; a two-line field would fight the label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ElmDropdownField(
    label: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    displayName: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    itemContent: (@Composable (T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = displayName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .auroraExpandable(expanded)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { itemContent?.invoke(option) ?: Text(displayName(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
