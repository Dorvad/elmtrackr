@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.elmtrackr.app.ui.projects

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material date picker for the optional project dates.
 *
 * The picker works in UTC millis; a project date is a calendar date, so the
 * conversion is anchored to UTC in both directions rather than the device zone,
 * which would shift the chosen day for users behind or ahead of it.
 */
@Composable
fun ProjectDatePickerDialog(
    initial: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    onSelect(picked)
                },
            ) { Text(stringResource(R.string.project_form_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.project_delete_keep)) }
        },
    ) {
        DatePicker(state = state)
    }
}
