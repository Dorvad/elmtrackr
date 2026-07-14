package com.elmtrackr.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elmtrackr.app.ui.theme.Spacing

/**
 * A time picker hosted in a self-sizing [Dialog] rather than the platform
 * [androidx.compose.material3.AlertDialog].
 *
 * A Material3 [TimePicker] needs the clock's full intrinsic size and switches
 * to a wider horizontal layout when vertical space is tight. `AlertDialog` pins
 * its content to the platform's phone-sized dialog width, so on tablets (and any
 * landscape window) the clock is clipped. Wrapping a [Surface] sized to
 * [IntrinsicSize.Min] with `usePlatformDefaultWidth = false` lets the dialog grow
 * to whichever layout the picker chooses, matching the official "Time picker
 * dialogs" guidance.
 *
 * The picker is always 24-hour to match how the rest of the app formats times.
 *
 * @param confirmEnabled evaluated against the currently selected hour/minute so
 *   callers can block invalid selections (e.g. a future start time).
 * @param supportingContent optional content shown between the picker and the
 *   action row, e.g. a validation message; receives the current selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: (hour: Int, minute: Int) -> Boolean = { _, _ -> true },
    supportingContent: @Composable (hour: Int, minute: Int) -> Unit = { _, _ -> },
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.md),
                )
                TimePicker(state = pickerState)
                supportingContent(pickerState.hour, pickerState.minute)
                Spacer(Modifier.height(Spacing.sm))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(cancelLabel) }
                    TextButton(
                        onClick = { onConfirm(pickerState.hour, pickerState.minute) },
                        enabled = confirmEnabled(pickerState.hour, pickerState.minute),
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}
