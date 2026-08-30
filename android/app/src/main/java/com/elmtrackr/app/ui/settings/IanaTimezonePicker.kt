package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.design.auroraExpandable
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IanaTimezonePicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // The composition's locale, not Locale.getDefault(): on Android 12 and
    // below the default never follows the in-app language, so the zone
    // display names stayed in the system language whatever the app was set to.
    val locale = appLocale()
    val normalized = remember(selected) { IanaTimezones.normalize(selected) }
    val options = remember(query, normalized, locale) {
        val pool = if (query.isBlank()) {
            IanaTimezones.displayOptions(normalized)
        } else {
            IanaTimezones.all
        }
        pool.filter { zone ->
            zone.contains(query, ignoreCase = true) ||
                zoneDisplayName(zone, locale).contains(query, ignoreCase = true)
        }.take(60)
    }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = if (expanded) query else zoneLabel(normalized, locale),
      onValueChange = {
        query = it
        if (!expanded) expanded = true
      },
      label = { Text(stringResource(R.string.settings_timezone)) },
      readOnly = !expanded,
      supportingText = {
        Text(
          if (expanded) stringResource(R.string.settings_timezone_search_hint) else "IANA: $normalized",
        )
      },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
        // This picker stays hand-rolled rather than moving to ElmDropdownField:
        // it is an editable combobox with its own query state, and typing filters
        // the list. It still needs the open/closed state the shared field adds,
        // and more so here — whether the field is showing the search query or the
        // current zone depends entirely on it.
        .auroraExpandable(expanded)
        .fillMaxWidth(),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = {
        expanded = false
        query = ""
      },
    ) {
      if (options.isEmpty()) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.settings_no_matching_timezones), color = MaterialTheme.colorScheme.onSurfaceVariant) },
          onClick = {},
          enabled = false,
        )
      } else {
        options.forEach { zone ->
          DropdownMenuItem(
            text = {
              Text(zoneLabel(zone, locale))
            },
            onClick = {
              onSelect(zone)
              expanded = false
              query = ""
            },
          )
        }
      }
    }
  }
}

private fun zoneDisplayName(zoneId: String, locale: Locale): String =
    runCatching {
        ZoneId.of(zoneId).getDisplayName(TextStyle.FULL, locale)
    }.getOrDefault(zoneId)

private fun zoneLabel(zoneId: String, locale: Locale): String {
    val display = zoneDisplayName(zoneId, locale)
    return if (display == zoneId) zoneId else "$zoneId — $display"
}
