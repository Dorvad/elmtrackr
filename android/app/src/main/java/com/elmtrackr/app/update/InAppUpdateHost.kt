package com.elmtrackr.app.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R

/**
 * Wraps [content] and overlays update prompts from [InAppUpdateManager]:
 * - [InAppUpdatePrompt.RestartReady]: flexible download finished; confirm restart.
 * - [InAppUpdatePrompt.PlayStore]: in-app flows unavailable; open the listing.
 */
@Composable
fun InAppUpdateHost(
    prompt: InAppUpdatePrompt?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPlayStore: () -> Unit,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val restartMessage = stringResource(R.string.in_app_update_ready_message)
    val restartActionLabel = stringResource(R.string.in_app_update_restart)
    val playStoreMessage = stringResource(R.string.in_app_update_play_store_message)
    val playStoreActionLabel = stringResource(R.string.in_app_update_open_play_store)

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    LaunchedEffect(prompt) {
        when (prompt) {
            null -> Unit
            InAppUpdatePrompt.RestartReady -> {
                val result = snackbarHostState.showSnackbar(
                    message = restartMessage,
                    actionLabel = restartActionLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite,
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> onInstall()
                    SnackbarResult.Dismissed -> onDismiss()
                }
            }
            InAppUpdatePrompt.PlayStore -> {
                val result = snackbarHostState.showSnackbar(
                    message = playStoreMessage,
                    actionLabel = playStoreActionLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> onOpenPlayStore()
                    SnackbarResult.Dismissed -> onDismiss()
                }
            }
        }
    }
}
