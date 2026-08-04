package com.elmtrackr.app.ui.projects

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R

/**
 * A close request that asks before throwing typed data away.
 *
 * The three project forms — project, billing record, payment — had no dirty
 * tracking, no [BackHandler] and no discard dialog. A user who filled in a name,
 * client, fee, currency, tax and dates, or a payment amount, lost all of it to one
 * back gesture. The shift form and every Settings detail screen guard exactly this;
 * these three were the ones that did not.
 *
 * All three build a single input object from their fields and are handed the
 * original, so "dirty" is one comparison rather than per-field bookkeeping. That is
 * why this takes a [Boolean] and not a change count: there is no honest count to
 * show, and the wording does not need one.
 *
 * @return the close action to give both the header's back arrow and the system back
 *   gesture. Wiring only one of them is how a guard gets half-applied.
 */
@Composable
internal fun rememberProjectDiscardGuard(
    isDirty: Boolean,
    onConfirmClose: () -> Unit,
): () -> Unit {
    var pending by remember { mutableStateOf(false) }

    val requestClose: () -> Unit = { if (isDirty) pending = true else onConfirmClose() }

    BackHandler { requestClose() }

    if (pending) {
        AlertDialog(
            onDismissRequest = { pending = false },
            // The Settings strings, deliberately: they are already generic ("Discard
            // changes?" / "You have unsaved changes on this screen.") and already
            // translated. Duplicating them under a project_ prefix would add two
            // locales' worth of surface to say the same thing.
            title = { Text(stringResource(R.string.settings_discard_title)) },
            text = { Text(stringResource(R.string.settings_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = false
                    onConfirmClose()
                }) {
                    Text(
                        stringResource(R.string.settings_discard_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = false }) {
                    Text(stringResource(R.string.settings_discard_keep))
                }
            },
        )
    }

    return requestClose
}
