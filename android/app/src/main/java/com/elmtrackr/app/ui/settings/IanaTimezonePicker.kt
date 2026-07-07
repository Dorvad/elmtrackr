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
    val normalized = remember(selected) { IanaTimezones.normalize(selected) }
    val options = remember(query, normalized) {
        val pool = if (query.isBlank()) {
            IanaTimezones.displayOptions(normalized)
        } else {
            IanaTimezones.all
        }
        pool.filter { zone ->
            zone.contains(query, ignoreCase = true) ||
                zoneDisplayName(zone).contains(query, ignoreCase = true)
        }.take(60)
    }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = if (expanded) query else zoneLabel(normalized),
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
              Text(zoneLabel(zone))
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

private fun zoneDisplayName(zoneId: String): String =
    runCatching {
        ZoneId.of(zoneId).getDisplayName(TextStyle.FULL, Locale.getDefault())
    }.getOrDefault(zoneId)

private fun zoneLabel(zoneId: String): String {
    val display = zoneDisplayName(zoneId)
    return if (display == zoneId) zoneId else "$zoneId — $display"
}
