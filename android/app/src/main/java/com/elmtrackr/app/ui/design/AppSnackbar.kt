package com.elmtrackr.app.ui.design

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * The app's one snackbar host, reachable from any screen.
 *
 * Settings, Premium, Shifts and Tasks each host their own `SnackbarHost`. Dashboard,
 * Reports and Projects — the three most-used screens — hosted none, so a transient
 * failure there had nowhere to go but a full-screen error takeover. "Could not refresh"
 * replaced a month of data the user could still read.
 *
 * Hosting it in `MainScaffold` instead of per screen means it also survives tab
 * navigation, which a per-screen host does not: a message posted as the user swipes away
 * used to die with the composable that posted it.
 *
 * Screens that already have their own host keep it. A screen inside a form or an
 * immersive flow is drawn outside the scaffold, so an app-level host would be clipped or
 * hidden there; those need a local one, and this local is deliberately not a silent
 * fallback for them — see [LocalAppSnackbarHostState]'s default.
 */
val LocalAppSnackbarHostState: ProvidableCompositionLocal<SnackbarHostState?> =
    compositionLocalOf {
        // null, not a throwaway SnackbarHostState. A default instance would accept
        // `showSnackbar` calls that no host is collecting, so a message would be
        // silently swallowed and look like code that works. Null makes a caller outside
        // MainScaffold choose: use a local host, or say nothing.
        null
    }

/**
 * Shows [message] on the app-level host, returning false when there is none in scope.
 *
 * The boolean is the point: a caller that must not lose the message can fall back to
 * whatever it had before rather than assuming the snackbar appeared.
 */
suspend fun SnackbarHostState?.showAppMessage(
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
): Boolean {
    val host = this ?: return false
    host.showSnackbar(message = message, actionLabel = actionLabel, duration = duration)
    return true
}

/** Remembers a host state for a screen drawn outside [MainScaffold]. */
@Composable
fun rememberLocalSnackbarHostState(): SnackbarHostState = remember { SnackbarHostState() }
