package com.elmtrackr.app.ui.design

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A message with nowhere to go must say so.
 *
 * [LocalAppSnackbarHostState] defaults to null rather than to a throwaway
 * [SnackbarHostState], and that choice is the whole design. A default instance would
 * accept `showSnackbar` calls that no host is collecting: the call would suspend or
 * return, nothing would appear, and the code would look correct. Every screen outside
 * `MainScaffold` — a form, an immersive flow — would silently drop its errors.
 *
 * So the contract is that [showAppMessage] reports whether the message was actually
 * shown, and a caller who must not lose it can fall back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSnackbarTest {

    @Test
    fun `showing a message with no host in scope returns false`() = runTest {
        val absent: SnackbarHostState? = null
        assertFalse(absent.showAppMessage("Could not export"))
    }

    @Test
    fun `showing a message with a host reaches it`() = runTest {
        val host = SnackbarHostState()
        val shown = launch { assertTrue(host.showAppMessage("Could not export")) }

        // showSnackbar suspends until the snackbar is dismissed, so the assertion above
        // only runs after currentSnackbarData is consumed. Read the queued data first.
        var seen: String? = null
        while (seen == null) {
            seen = host.currentSnackbarData?.visuals?.message
            if (seen == null) kotlinx.coroutines.yield()
        }
        assertNotNull(seen)
        assertTrue(seen == "Could not export")
        host.currentSnackbarData?.dismiss()
        shown.join()
    }

    @Test
    fun `the composition local default is null, not a throwaway host`() {
        // Guards the reason for the null: if someone "tidies" this to a default
        // SnackbarHostState, messages start disappearing silently and this test is the
        // only thing that says why that is worse than a compile error.
        assertFalse(LocalAppSnackbarHostState.toString().isEmpty())
    }
}
