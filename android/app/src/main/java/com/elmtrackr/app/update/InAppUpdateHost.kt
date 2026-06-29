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
import androidx.compose.ui.unit.dp

/**
 * Wraps [content] and overlays a "restart to install" snackbar whenever a
 * flexible Google Play update has finished downloading ([updateReady] is true).
 *
 * Google Play does not restart the app automatically for flexible updates, so we
 * ask the user for confirmation before calling [onInstall]
 * (`AppUpdateManager.completeUpdate()`), as recommended by the in-app updates guide.
 */
@Composable
fun InAppUpdateHost(
    updateReady: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

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

    LaunchedEffect(updateReady) {
        if (!updateReady) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "A new version of ElmTrackr is ready.",
            actionLabel = "Restart",
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onInstall()
            SnackbarResult.Dismissed -> onDismiss()
        }
    }
}
